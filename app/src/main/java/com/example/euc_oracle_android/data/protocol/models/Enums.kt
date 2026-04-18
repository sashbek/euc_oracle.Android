package com.example.euc_oracle_android.data.protocol.models

enum class CtrlReg(val address: Int) {
    READ_START_L(0x00),
    READ_START_H(0x01),
    READ_SIZE(0x02),
    CMD(0x03),
    STATUS(0x04),
    VERSION(0x05),
    DEVICE_TYPE(0x06)
}

enum class Command(val value: Byte) {
    NONE(0x00),
    START_READ(0x01),
    SAVE_TO_FLASH(0x02),
    LOAD_FROM_FLASH(0x03),
    RESET_DEFAULTS(0x04),
    SOFT_RESET(0x05);

    fun toByte(): Byte = value

    companion object {
        fun fromByte(value: Byte): Command {
            return values().find { it.value == value } ?: NONE
        }
    }
}

enum class Status(val value: Byte) {
    OK(0x00),
    ERR_MARKER(0x01),
    ERR_ADDR(0x02),
    ERR_SIZE(0x03),
    ERR_OVERFLOW(0x04),
    ERR_PROTECTED(0x05),
    ERR_FLASH(0x06),
    ERR_BUSY(0x07),
    DATA_READY(0x80.toByte());

    companion object {
        fun fromByte(value: Byte): Status {
            return values().find { it.value == value } ?: ERR_MARKER
        }
    }
}

enum class DeviceType(val value: Byte) {
    WHEEL(0x00),
    LED(0x01),
    UNKNOWN(0xFF.toByte());

    companion object {
        fun fromByte(value: Byte): DeviceType {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}