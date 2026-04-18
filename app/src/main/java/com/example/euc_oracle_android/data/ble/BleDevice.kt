package com.example.euc_oracle_android.data.ble

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice
) {
    val displayName: String
        get() = if (name.isNotBlank()) name else "Unknown Device"

    val shortAddress: String
        get() = address.takeLast(5)
}