package com.example.euc_oracle_android.ui.scan

import android.Manifest
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.euc_oracle_android.data.ble.BleDevice
import com.example.euc_oracle_android.data.ble.BleManager
import kotlinx.coroutines.launch

class ScanViewModel(
    private val bleManager: BleManager
) : ViewModel() {

    private val _devices = MutableLiveData<List<BleDevice>>(emptyList())
    val devices: LiveData<List<BleDevice>> = _devices

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val connectionState = bleManager.connectionState.asLiveData()
    val deviceInfo = bleManager.deviceInfo.asLiveData()

    // Храним устройства с временем последнего обнаружения
    private val discoveredDevices = LinkedHashMap<String, DeviceEntry>()
    private val handler = Handler(Looper.getMainLooper())

    // Таймаут для удаления устройств (200 мс)
    private val deviceTimeoutMs = 200L

    // Имена, сохраненные после успешного подключения (переживают переподключение)
    private val persistentNames = HashMap<String, String>()

    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupExpiredDevices()
            handler.postDelayed(this, 200) // Проверяем каждые 200 мс
        }
    }

    private data class DeviceEntry(
        val device: BleDevice,
        val lastSeen: Long,
        val originalName: String?
    )

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        _isScanning.value = true
        handler.post(cleanupRunnable)

        bleManager.startScan { device ->
            val now = System.currentTimeMillis()

            // Получаем сохраненное имя (приоритет: persistentNames > originalName > device.name)
            val savedName = persistentNames[device.address]
            val entry = discoveredDevices[device.address]
            val originalName = savedName
                ?: entry?.originalName
                ?: if (device.name.isNotBlank() && device.name != "Unknown") device.name else null

            // Обновляем устройство с сохранением имени
            val displayName = when {
                savedName != null -> savedName
                originalName != null -> originalName
                device.name.isNotBlank() && device.name != "Unknown" -> device.name
                else -> device.name // "Unknown" или пустое
            }

            val updatedDevice = BleDevice(
                name = displayName,
                address = device.address,
                device = device.device
            )

            discoveredDevices[device.address] = DeviceEntry(
                device = updatedDevice,
                lastSeen = now,
                originalName = originalName ?: (if (device.name != "Unknown") device.name else null)
            )

            updateDeviceList()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bleManager.stopScan()
        _isScanning.value = false
        handler.removeCallbacks(cleanupRunnable)
        // Не очищаем persistentNames при остановке сканирования
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun refreshScan() {
        discoveredDevices.clear()
        _devices.value = emptyList()
        startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun connectToDevice(device: BleDevice) {
        stopScan()

        // Сохраняем имя устройства перед подключением
        if (device.name.isNotBlank() && device.name != "Unknown") {
            persistentNames[device.address] = device.name
        }

        viewModelScope.launch {
            bleManager.connect(device.address)
                .onSuccess { deviceInfo ->
                    // Сохраняем имя из deviceInfo
                    if (deviceInfo.name.isNotBlank()) {
                        persistentNames[device.address] = deviceInfo.name
                    }
                }
                .onFailure { error ->
                    _error.value = "Failed to connect: ${error.message}"
                    startScan()
                }
        }
    }

    /**
     * Вызывается при отключении от устройства
     */
    fun onDeviceDisconnected(address: String) {
        // Не удаляем persistentName, чтобы сохранить имя при следующем сканировании
    }

    fun clearError() {
        _error.value = null
    }

    private fun cleanupExpiredDevices() {
        val now = System.currentTimeMillis()
        val expiredAddresses = discoveredDevices.entries
            .filter { now - it.value.lastSeen > deviceTimeoutMs }
            .map { it.key }

        if (expiredAddresses.isNotEmpty()) {
            expiredAddresses.forEach { discoveredDevices.remove(it) }
            updateDeviceList()
        }
    }

    private fun updateDeviceList() {
        // Сортируем: сначала с именем, потом по адресу
        val sortedDevices = discoveredDevices.values
            .map { it.device }
            .sortedWith(compareByDescending<BleDevice> {
                it.name.isNotBlank() && it.name != "Unknown"
            }.thenBy { it.address })

        _devices.postValue(sortedDevices)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        stopScan()
        handler.removeCallbacks(cleanupRunnable)
    }
}