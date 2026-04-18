package com.example.euc_oracle_android.ui.scan

import android.Manifest
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

    // Исправлено: используем kotlinx.coroutines.flow.asLiveData
    val connectionState = bleManager.connectionState.asLiveData()

    private val discoveredDevices = mutableMapOf<String, BleDevice>()

    val deviceInfo = bleManager.deviceInfo.asLiveData()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        _isScanning.value = true

        bleManager.startScan { device ->
            discoveredDevices[device.address] = device
            _devices.postValue(discoveredDevices.values.toList())
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bleManager.stopScan()
        _isScanning.value = false
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

        viewModelScope.launch @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) {
            bleManager.connect(device.address)
                .onFailure { error ->
                    _error.value = "Failed to connect: ${error.message}"
                    startScan()
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}