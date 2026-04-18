package com.example.euc_oracle_android.models

import com.example.euc_oracle_android.data.protocol.models.DeviceType

data class DeviceInfo(
    val name: String,
    val type: DeviceType,
    val firmwareVersion: String,
    val hardwareRevision: String,
    val deviceId: String,
    val protocolVersion: Int
)