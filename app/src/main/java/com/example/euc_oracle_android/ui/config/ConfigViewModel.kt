package com.example.euc_oracle_android.ui.config

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.euc_oracle_android.data.ble.BleManager
import com.example.euc_oracle_android.data.repository.DeviceRepository
import com.example.euc_oracle_android.models.Register
import com.example.euc_oracle_android.models.RegisterValue
import kotlinx.coroutines.launch

class ConfigViewModel(
    private val deviceRepository: DeviceRepository,
    private val bleManager: BleManager
) : ViewModel() {

    val registers = deviceRepository.registers.asLiveData()
    val deviceInfo = bleManager.deviceInfo.asLiveData()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadRegisters() {
        Log.d("ConfigViewModel", "loadRegisters called")
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("ConfigViewModel", "Calling deviceRepository.discoverRegisters()")
            deviceRepository.discoverRegisters()
                .onSuccess {
                    Log.d("ConfigViewModel", "discoverRegisters SUCCESS")
                }
                .onFailure { error ->
                    Log.e("ConfigViewModel", "discoverRegisters FAILED: ${error.message}", error)
                    _error.value = "Failed to load registers: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    fun refreshRegisters() {
        viewModelScope.launch {
            _isLoading.value = true

            registers.value?.forEach { register ->
                if (register.descriptor.readable) {
                    deviceRepository.readRegister(register.descriptor.address)
                }
            }

            _isLoading.value = false
        }
    }

    fun updateRegister(address: Int, value: RegisterValue) {
        viewModelScope.launch {
            deviceRepository.writeRegister(address, value)
                .onFailure { error ->
                    _error.value = "Failed to write register: ${error.message}"
                }
        }
    }

    fun saveToFlash() {
        viewModelScope.launch {
            _isLoading.value = true
            deviceRepository.saveToFlash()
                .onSuccess {
                    _error.value = "Settings saved successfully"
                }
                .onFailure { error ->
                    _error.value = "Failed to save: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    fun loadFromFlash() {
        viewModelScope.launch {
            _isLoading.value = true
            deviceRepository.loadFromFlash()
                .onSuccess {
                    _error.value = "Settings loaded successfully"
                }
                .onFailure { error ->
                    _error.value = "Failed to load: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _isLoading.value = true
            deviceRepository.resetToDefaults()
                .onSuccess {
                    _error.value = "Settings reset to defaults"
                }
                .onFailure { error ->
                    _error.value = "Failed to reset: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
