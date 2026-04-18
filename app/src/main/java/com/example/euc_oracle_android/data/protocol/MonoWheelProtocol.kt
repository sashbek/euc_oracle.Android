package com.example.euc_oracle_android.data.protocol

object MonoWheelProtocol {
    const val SETTINGS_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
    const val SETTINGS_WRITE_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
    const val SETTINGS_READ_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9"

    const val PROTOCOL_VERSION_MAJOR = 1
    const val PROTOCOL_VERSION_MINOR = 0
    const val PROTOCOL_VERSION = (PROTOCOL_VERSION_MAJOR shl 8) or PROTOCOL_VERSION_MINOR

    const val MAX_CHUNK_SIZE = 20
}