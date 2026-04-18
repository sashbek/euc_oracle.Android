package com.example.euc_oracle_android.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.euc_oracle_android.data.protocol.MonoWheelProtocol
import com.example.euc_oracle_android.data.protocol.PacketBuilder
import com.example.euc_oracle_android.data.protocol.PacketParser
import com.example.euc_oracle_android.data.protocol.models.Command
import com.example.euc_oracle_android.data.protocol.models.DeviceType
import com.example.euc_oracle_android.models.DeviceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class BleManager private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: BleManager? = null

        fun getInstance(context: Context): BleManager {
            return instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
        }
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null

    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    private var servicesDiscoveredDeferred: CompletableDeferred<Unit>? = null

    // Для ожидания уведомления о готовности данных
    private var dataReadyDeferred: CompletableDeferred<Unit>? = null

    // Для ожидания результата чтения характеристики
    private var readCharDeferred: CompletableDeferred<ByteArray>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BleManager", "onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.d("BleManager", "Connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BleManager", "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(MonoWheelProtocol.SETTINGS_SERVICE_UUID))
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(
                        UUID.fromString(MonoWheelProtocol.SETTINGS_WRITE_CHAR_UUID)
                    )
                    readCharacteristic = service.getCharacteristic(
                        UUID.fromString(MonoWheelProtocol.SETTINGS_READ_CHAR_UUID)
                    )

                    Log.d("BleManager", "Write characteristic: ${writeCharacteristic != null}")
                    Log.d("BleManager", "Read characteristic: ${readCharacteristic != null}")

                    // Включаем уведомления для READ характеристики
                    readCharacteristic?.let {
                        val success = gatt.setCharacteristicNotification(it, true)
                        Log.d("BleManager", "setCharacteristicNotification: $success")

                        // Настраиваем CCCD дескриптор
                        it.descriptors?.find { desc ->
                            desc.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        }?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }

                    servicesDiscoveredDeferred?.complete(Unit)
                } else {
                    Log.e("BleManager", "Service not found")
                    servicesDiscoveredDeferred?.completeExceptionally(Exception("Service not found"))
                }
            } else {
                servicesDiscoveredDeferred?.completeExceptionally(Exception("Service discovery failed"))
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("BleManager", "onDescriptorWrite: status=$status")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("BleManager", "onCharacteristicWrite: status=$status")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { data ->
                Log.d("BleManager", "Notification received: ${data.size} bytes")
                // Устройство сигнализирует что данные готовы к чтению
                dataReadyDeferred?.complete(Unit)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("BleManager", "onCharacteristicRead: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.value?.let { data ->
                    Log.d("BleManager", "Read data: ${data.size} bytes")
                    readCharDeferred?.complete(data)
                }
            } else {
                readCharDeferred?.completeExceptionally(Exception("Read failed: $status"))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(callback: (BleDevice) -> Unit) {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(MonoWheelProtocol.SETTINGS_SERVICE_UUID)))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(scanFilter), scanSettings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let { device ->
                    callback(BleDevice(
                        name = device.name ?: "Unknown",
                        address = device.address,
                        device = device
                    ))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleManager", "Scan failed: $errorCode")
            }
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bleScanner?.stopScan(object : ScanCallback() {})
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(address: String): Result<DeviceInfo> {
        return try {
            _connectionState.value = ConnectionState.CONNECTING
            servicesDiscoveredDeferred = CompletableDeferred()

            withTimeout(15000) {
                val device = bluetoothAdapter?.getRemoteDevice(address)
                bluetoothGatt = device?.connectGatt(context, false, gattCallback)

                // Ждем обнаружения сервисов
                servicesDiscoveredDeferred?.await()

                // Даем время на настройку
                delay(500)

                // Пробуем прочитать информацию об устройстве
                val deviceInfo = try {
                    readDeviceInfo()
                } catch (e: Exception) {
                    Log.w("BleManager", "Could not read device info, using defaults", e)
                    createDefaultDeviceInfo()
                }

                _deviceInfo.value = deviceInfo
                Result.success(deviceInfo)
            }
        } catch (e: Exception) {
            Log.e("BleManager", "Connection failed", e)
            _connectionState.value = ConnectionState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Чтение регистров с поддержкой чанков
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readRegister(startAddress: Int, size: Int): Result<ByteArray> {
        val wChar = writeCharacteristic
        val rChar = readCharacteristic

        if (wChar == null || rChar == null) {
            Log.e("BleManager", "Characteristics not ready")
            return Result.failure(Exception("Characteristics not ready"))
        }

        return try {
            // Шаг 1: Отправляем запрос на чтение
            val packet = PacketBuilder.buildReadRequest(startAddress, size)
            Log.d("BleManager", "Sending read request: startAddr=0x${startAddress.toString(16)}, size=$size")

            dataReadyDeferred = CompletableDeferred()

            wChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            wChar.value = packet

            val success = bluetoothGatt?.writeCharacteristic(wChar) ?: false
            if (!success) {
                dataReadyDeferred = null
                return Result.failure(Exception("Failed to write characteristic"))
            }

            // Шаг 2: Ждем уведомление о готовности данных
            withTimeout(5000) {
                dataReadyDeferred?.await()
            }
            dataReadyDeferred = null
            Log.d("BleManager", "Data ready notification received")

            // Шаг 3: Читаем данные чанками
            val result = ByteArray(size)
            var bytesReceived = 0
            var moreData = true

            while (moreData && bytesReceived < size) {
                readCharDeferred = CompletableDeferred()

                val readSuccess = bluetoothGatt?.readCharacteristic(rChar) ?: false
                if (!readSuccess) {
                    readCharDeferred = null
                    return Result.failure(Exception("Failed to read characteristic"))
                }

                val chunk = withTimeout(5000) {
                    readCharDeferred?.await()
                }
                readCharDeferred = null

                if (chunk == null) {
                    return Result.failure(Exception("No data received"))
                }

                // Парсим чанк
                val parsed = PacketParser.parseReadResponse(chunk)
                if (parsed != null) {
                    val (offset, payload) = parsed
                    Log.d("BleManager", "Chunk: offset=$offset, size=${payload.size}")

                    System.arraycopy(payload, 0, result, offset, payload.size)
                    bytesReceived += payload.size

                    // Проверяем, есть ли еще данные
                    // Если offset + payload.size < size, продолжаем читать
                    moreData = (offset + payload.size) < size
                } else {
                    // Если не удалось распарсить, считаем что это последний чанк
                    moreData = false
                }
            }

            Log.d("BleManager", "Read complete: $bytesReceived bytes")
            Result.success(result)

        } catch (e: Exception) {
            dataReadyDeferred = null
            readCharDeferred = null
            Log.e("BleManager", "Read register failed", e)
            Result.failure(e)
        }
    }

    suspend fun writeRegister(address: Int, data: ByteArray): Result<Unit> {
        val wChar = writeCharacteristic
        if (wChar == null) {
            Log.e("BleManager", "Write characteristic is null")
            return Result.failure(Exception("Characteristic not ready"))
        }

        return try {
            val packet = PacketBuilder.buildWritePacket(address, data)
            Log.d("BleManager", "Writing register: addr=0x${address.toString(16)}, size=${data.size}")

            // Ждем подтверждения записи через onCharacteristicWrite
            suspendCoroutine { continuation ->
                wChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                wChar.value = packet

                val success = bluetoothGatt?.writeCharacteristic(wChar) ?: false
                if (success) {
                    // Даем время на запись
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        continuation.resume(Result.success(Unit))
                    }, 500)
                } else {
                    continuation.resumeWithException(Exception("Failed to write characteristic"))
                }
            }
        } catch (e: Exception) {
            Log.e("BleManager", "Write register failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendCommand(command: Command): Result<Unit> {
        return writeRegister(PacketBuilder.REG_CMD, byteArrayOf(command.toByte()))
    }

    suspend fun saveToFlash(): Result<Unit> {
        return sendCommand(Command.SAVE_TO_FLASH)
    }

    suspend fun loadFromFlash(): Result<Unit> {
        return sendCommand(Command.LOAD_FROM_FLASH)
    }

    suspend fun resetToDefaults(): Result<Unit> {
        return sendCommand(Command.RESET_DEFAULTS)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun readDeviceInfo(): DeviceInfo {
        Log.d("BleManager", "Reading device info...")

        // Читаем DEVICE_TYPE отдельно (адрес 0x06, 1 байт)
        val deviceTypeData = readRegister(0x06, 1).getOrNull()
        val deviceTypeByte = deviceTypeData?.getOrElse(0) { 0xFF.toByte() } ?: 0xFF.toByte()
        Log.d("BleManager", "Device type byte: 0x${"%02X".format(deviceTypeByte)}")
        val deviceType = DeviceType.fromByte(deviceTypeByte)

        // Читаем системные регистры (0x10 - 0x2F)
        val systemData = readRegister(0x10, 32).getOrThrow()
        Log.d("BleManager", "System data: ${systemData.joinToString(" ") { "%02X".format(it) }}")

        // DEVICE_ID: 0x10-0x13 (смещение 0-3 от начала systemData)
        val deviceId = systemData.copyOfRange(0, 4).joinToString("") { "%02X".format(it) }

        // FW_VERSION: 0x14-0x17 (смещение 4-7)
        val fwVersion = systemData.copyOfRange(4, 8).let { bytes ->
            "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
        }

        // HW_REVISION: 0x18-0x19 (смещение 8-9)
        val hwRev = systemData.copyOfRange(8, 10).let { bytes ->
            "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}"
        }

        // DEVICE_NAME: 0x27-0x36 (смещение 0x17 от 0x10 = 23)
        val deviceNameBytes = systemData.copyOfRange(23, 39) // 0x27 - 0x10 = 0x17 = 23
        val deviceName = deviceNameBytes
            .takeWhile { it != 0.toByte() }
            .toByteArray()
            .let { String(it, Charsets.UTF_8) }
            .ifEmpty { "MonoWheel" }

        return DeviceInfo(
            name = deviceName,
            type = deviceType,
            firmwareVersion = fwVersion,
            hardwareRevision = hwRev,
            deviceId = deviceId,
            protocolVersion = MonoWheelProtocol.PROTOCOL_VERSION
        )
    }

    private fun createDefaultDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            name = "MonoWheel",
            type = DeviceType.WHEEL,
            firmwareVersion = "1.0.0.0",
            hardwareRevision = "1.0",
            deviceId = "UNKNOWN",
            protocolVersion = MonoWheelProtocol.PROTOCOL_VERSION
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        cleanup()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        readCharacteristic = null
        servicesDiscoveredDeferred = null
        dataReadyDeferred = null
        readCharDeferred = null
    }
}