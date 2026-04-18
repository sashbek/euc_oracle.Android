package com.example.euc_oracle_android.data.repository

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.euc_oracle_android.data.ble.BleManager
import com.example.euc_oracle_android.data.protocol.models.Command
import com.example.euc_oracle_android.data.protocol.models.DeviceType
import com.example.euc_oracle_android.models.Register
import com.example.euc_oracle_android.models.RegisterDescriptor
import com.example.euc_oracle_android.models.RegisterType
import com.example.euc_oracle_android.models.RegisterValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DeviceRepository(
    private val bleManager: BleManager,
    private val context: Context
) {
    private val registerLoader = RegisterLoader(context)

    private val _registers = MutableStateFlow<List<Register>>(emptyList())
    val registers: StateFlow<List<Register>> = _registers.asStateFlow()

    private var cachedDescriptors: List<RegisterDescriptor>? = null

    /**
     * Группирует регистры по непрерывным диапазонам адресов
     */
    private fun groupRegistersByContiguousAddresses(
        descriptors: List<RegisterDescriptor>
    ): List<RegisterBatch> {
        if (descriptors.isEmpty()) return emptyList()

        // Сортируем по адресу
        val sorted = descriptors.sortedBy { it.address }

        val batches = mutableListOf<RegisterBatch>()
        var currentBatch = mutableListOf(sorted[0])
        var currentEnd = sorted[0].address + sorted[0].size

        for (i in 1 until sorted.size) {
            val descriptor = sorted[i]

            // Если адрес следует сразу за предыдущим регистром или рядом (разрыв < 16 байт)
            if (descriptor.address <= currentEnd + 16) {
                currentBatch.add(descriptor)
                currentEnd = maxOf(currentEnd, descriptor.address + descriptor.size)
            } else {
                // Сохраняем текущую группу и начинаем новую
                batches.add(RegisterBatch(
                    startAddress = currentBatch.first().address,
                    size = currentEnd - currentBatch.first().address,
                    descriptors = currentBatch.toList()
                ))
                currentBatch = mutableListOf(descriptor)
                currentEnd = descriptor.address + descriptor.size
            }
        }

        // Добавляем последнюю группу
        if (currentBatch.isNotEmpty()) {
            batches.add(RegisterBatch(
                startAddress = currentBatch.first().address,
                size = currentEnd - currentBatch.first().address,
                descriptors = currentBatch.toList()
            ))
        }

        return batches
    }

    suspend fun discoverRegisters(): Result<Unit> {
        Log.d("DeviceRepository", "discoverRegisters called")

        // Ждем, пока deviceInfo станет доступен
        var attempts = 0
        while (bleManager.deviceInfo.value == null && attempts < 10) {
            Log.d("DeviceRepository", "Waiting for deviceInfo... attempt $attempts")
            delay(200)
            attempts++
        }

        return try {
            var deviceType = bleManager.deviceInfo.value?.type
            Log.d("DeviceRepository", "Device type from bleManager: $deviceType")

            if (deviceType == null || deviceType == DeviceType.UNKNOWN) {
                Log.w("DeviceRepository", "Device type is null or UNKNOWN, trying WHEEL")
                deviceType = DeviceType.WHEEL
            }

            // 1. Загружаем дескрипторы из JSON
            val descriptors = registerLoader.loadRegisters(deviceType)
            cachedDescriptors = descriptors
            Log.d("DeviceRepository", "Loaded ${descriptors.size} descriptors from JSON")

            // 2. Группируем регистры по непрерывным адресам
            val batches = groupRegistersByContiguousAddresses(descriptors)
            Log.d("DeviceRepository", "Grouped into ${batches.size} batches")

            // 3. Читаем данные батчами
            val registers = mutableListOf<Register>()

            for (batch in batches) {
                Log.d("DeviceRepository", "Reading batch: start=0x${batch.startAddress.toString(16)}, size=${batch.size}, registers=${batch.descriptors.size}")

                try {
                    // Читаем весь диапазон одним запросом
                    val data = bleManager.readRegister(batch.startAddress, batch.size).getOrThrow()
                    Log.d("DeviceRepository", "Read ${data.size} bytes for batch at 0x${batch.startAddress.toString(16)}")

                    // Парсим каждый регистр из полученных данных
                    for (descriptor in batch.descriptors) {
                        val offset = descriptor.address - batch.startAddress
                        val valueBytes = data.copyOfRange(offset, offset + descriptor.size)
                        val value = parseRegisterValue(valueBytes, descriptor)
                        registers.add(Register(descriptor, value))

                        Log.d("DeviceRepository", "  ${descriptor.name} @ 0x${descriptor.address.toString(16)} = ${formatValueForLog(value, descriptor)}")
                    }
                } catch (e: Exception) {
                    Log.e("DeviceRepository", "Failed to read batch at 0x${batch.startAddress.toString(16)}", e)
                    // Добавляем регистры с null значениями
                    batch.descriptors.forEach { descriptor ->
                        registers.add(Register(descriptor, null))
                    }
                }
            }

            _registers.value = registers
            Log.d("DeviceRepository", "Discovered ${registers.size} registers")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("DeviceRepository", "discoverRegisters failed", e)
            Result.failure(e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readRegister(address: Int): Result<RegisterValue> {
        val descriptor = _registers.value.find { it.descriptor.address == address }?.descriptor
            ?: cachedDescriptors?.find { it.address == address }
            ?: return Result.failure(Exception("Register not found"))

        return try {
            val data = bleManager.readRegister(address, descriptor.size).getOrThrow()
            val value = parseRegisterValue(data, descriptor)

            _registers.update { registers ->
                registers.map { register ->
                    if (register.descriptor.address == address) {
                        register.copy(value = value)
                    } else {
                        register
                    }
                }
            }

            Result.success(value)
        } catch (e: Exception) {
            Log.e("DeviceRepository", "Failed to read register at 0x${address.toString(16)}", e)
            Result.failure(e)
        }
    }

    suspend fun writeRegister(address: Int, value: RegisterValue): Result<Unit> {
        val descriptor = _registers.value.find { it.descriptor.address == address }?.descriptor
            ?: cachedDescriptors?.find { it.address == address }
            ?: return Result.failure(Exception("Register not found"))

        if (!descriptor.writable) {
            return Result.failure(Exception("Register is read-only"))
        }

        val bytes = serializeRegisterValue(value, descriptor)
        Log.d("DeviceRepository", "Writing to 0x${address.toString(16)}: ${bytes.joinToString(" ") { "%02X".format(it) }}")

        return bleManager.writeRegister(address, bytes).onSuccess {
            _registers.update { registers ->
                registers.map { register ->
                    if (register.descriptor.address == address) {
                        register.copy(value = value)
                    } else {
                        register
                    }
                }
            }
        }
    }

    suspend fun saveToFlash(): Result<Unit> {
        return bleManager.sendCommand(Command.SAVE_TO_FLASH)
    }

    suspend fun loadFromFlash(): Result<Unit> {
        return bleManager.sendCommand(Command.LOAD_FROM_FLASH).onSuccess {
            discoverRegisters()
        }
    }

    suspend fun resetToDefaults(): Result<Unit> {
        return bleManager.sendCommand(Command.RESET_DEFAULTS).onSuccess {
            discoverRegisters()
        }
    }

    fun getGroups(): List<RegisterGroup> {
        val deviceType = bleManager.deviceInfo.value?.type ?: DeviceType.WHEEL
        return registerLoader.loadGroups(deviceType)
    }

    private fun parseRegisterValue(bytes: ByteArray, descriptor: RegisterDescriptor): RegisterValue {
        return when (descriptor.type) {
            RegisterType.UINT8 -> RegisterValue.IntValue((bytes[0].toInt() and 0xFF).toLong(), descriptor.type)
            RegisterType.INT8 -> RegisterValue.IntValue(bytes[0].toLong(), descriptor.type)
            RegisterType.UINT16 -> {
                val value = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
                RegisterValue.IntValue(value.toLong(), descriptor.type)
            }
            RegisterType.INT16 -> {
                val value = (bytes[1].toInt() shl 8) or (bytes[0].toInt() and 0xFF)
                RegisterValue.IntValue(value.toLong(), descriptor.type)
            }
            RegisterType.UINT32 -> {
                val value = ((bytes[3].toLong() and 0xFF) shl 24) or
                        ((bytes[2].toLong() and 0xFF) shl 16) or
                        ((bytes[1].toLong() and 0xFF) shl 8) or
                        (bytes[0].toLong() and 0xFF)
                RegisterValue.IntValue(value, descriptor.type)
            }
            RegisterType.INT32 -> {
                val value = (bytes[3].toInt() shl 24) or
                        ((bytes[2].toInt() and 0xFF) shl 16) or
                        ((bytes[1].toInt() and 0xFF) shl 8) or
                        (bytes[0].toInt() and 0xFF)
                RegisterValue.IntValue(value.toLong(), descriptor.type)
            }
            RegisterType.FLOAT16_8 -> {
                val raw = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
                RegisterValue.FloatValue(raw / 256.0, descriptor.type)
            }
            RegisterType.STRING -> {
                val str = bytes.takeWhile { it != 0.toByte() }.toByteArray()
                RegisterValue.StringValue(String(str, Charsets.UTF_8))
            }
            RegisterType.COLOR_RGBW, RegisterType.RGBW -> {
                RegisterValue.ColorValue(
                    bytes[0].toInt() and 0xFF,
                    bytes[1].toInt() and 0xFF,
                    bytes[2].toInt() and 0xFF,
                    if (bytes.size > 3) bytes[3].toInt() and 0xFF else 0
                )
            }
            RegisterType.ENUM -> {
                RegisterValue.EnumValue(
                    bytes[0].toInt() and 0xFF,
                    descriptor.enumValues ?: emptyMap()
                )
            }
            RegisterType.BOOLEAN -> RegisterValue.BooleanValue(bytes[0] != 0.toByte())
            RegisterType.MAC -> RegisterValue.MacValue(bytes.copyOf(6))
            RegisterType.UNKNOWN -> RegisterValue.RawValue(bytes)
        }
    }

    private fun serializeRegisterValue(value: RegisterValue, descriptor: RegisterDescriptor): ByteArray {
        return when (value) {
            is RegisterValue.IntValue -> when (descriptor.type) {
                RegisterType.UINT8, RegisterType.INT8 -> byteArrayOf(value.value.toByte())
                RegisterType.UINT16, RegisterType.INT16 -> byteArrayOf(
                    (value.value and 0xFF).toByte(),
                    ((value.value shr 8) and 0xFF).toByte()
                )
                RegisterType.UINT32, RegisterType.INT32 -> byteArrayOf(
                    (value.value and 0xFF).toByte(),
                    ((value.value shr 8) and 0xFF).toByte(),
                    ((value.value shr 16) and 0xFF).toByte(),
                    ((value.value shr 24) and 0xFF).toByte()
                )
                else -> throw IllegalArgumentException("Invalid type for IntValue: ${descriptor.type}")
            }
            is RegisterValue.FloatValue -> when (descriptor.type) {
                RegisterType.FLOAT16_8 -> {
                    val raw = (value.value * 256).toInt()
                    byteArrayOf((raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
                }
                else -> throw IllegalArgumentException("Invalid type for FloatValue: ${descriptor.type}")
            }
            is RegisterValue.StringValue -> {
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                ByteArray(descriptor.size).apply {
                    System.arraycopy(bytes, 0, this, 0, minOf(bytes.size, descriptor.size))
                }
            }
            is RegisterValue.BooleanValue -> byteArrayOf(if (value.value) 1 else 0)
            is RegisterValue.ColorValue -> byteArrayOf(
                value.r.toByte(),
                value.g.toByte(),
                value.b.toByte(),
                value.w.toByte()
            )
            is RegisterValue.EnumValue -> byteArrayOf(value.value.toByte())
            is RegisterValue.MacValue -> value.bytes.copyOf(6)
            is RegisterValue.RawValue -> value.bytes
        }
    }

    private fun formatValueForLog(value: RegisterValue, descriptor: RegisterDescriptor): String {
        return when (value) {
            is RegisterValue.IntValue -> {
                descriptor.scale?.let { scale ->
                    "%.2f %s".format(value.value * scale, descriptor.unit ?: "")
                } ?: "${value.value} ${descriptor.unit ?: ""}"
            }
            is RegisterValue.FloatValue -> {
                descriptor.scale?.let { scale ->
                    "%.2f %s".format(value.value * scale, descriptor.unit ?: "")
                } ?: "%.2f %s".format(value.value, descriptor.unit ?: "")
            }
            is RegisterValue.StringValue -> "\"${value.value}\""
            is RegisterValue.BooleanValue -> if (value.value) "ON" else "OFF"
            is RegisterValue.ColorValue -> "#${"%02X".format(value.r)}${"%02X".format(value.g)}${"%02X".format(value.b)}"
            is RegisterValue.EnumValue -> value.options[value.value] ?: "Unknown"
            is RegisterValue.MacValue -> value.toStringValue()
            is RegisterValue.RawValue -> "[${value.bytes.size} bytes]"
        }
    }
}

/**
 * Группа регистров для batch чтения
 */
data class RegisterBatch(
    val startAddress: Int,
    val size: Int,
    val descriptors: List<RegisterDescriptor>
)