package com.example.euc_oracle_android.utils

import com.example.euc_oracle_android.models.RegisterDescriptor
import com.example.euc_oracle_android.models.RegisterType

object RegisterTypeDetector {

    fun detectType(address: Int, size: Int, data: ByteArray): RegisterType {
        return when {
            isProbablyBoolean(size, data) -> RegisterType.BOOLEAN
            isProbablyEnum(address) -> RegisterType.ENUM
            isProbablyColor(size, address) -> RegisterType.RGBW
            isProbablyMac(size) -> RegisterType.MAC
            isProbablyString(size, data) -> RegisterType.STRING
            isProbablyFloat16_8(address) -> RegisterType.FLOAT16_8
            else -> when (size) {
                1 -> if (isSigned(data)) RegisterType.INT8 else RegisterType.UINT8
                2 -> if (isSigned(data)) RegisterType.INT16 else RegisterType.UINT16
                4 -> if (isSigned(data)) RegisterType.INT32 else RegisterType.UINT32
                else -> RegisterType.UNKNOWN
            }
        }
    }

    private fun isProbablyBoolean(size: Int, data: ByteArray): Boolean {
        return size == 1 && (data[0] == 0.toByte() || data[0] == 1.toByte())
    }

    private fun isProbablyEnum(address: Int): Boolean {
        val enumAddresses = setOf(
            0x26, // LOG_LEVEL
            0x4D, 0x4E, // Column types
            0x59, // UART protocol
            0x84, // Effect mode
            0xB8, 0xB9, 0xBA // Behaviors
        )
        return address in enumAddresses
    }

    private fun isProbablyColor(size: Int, address: Int): Boolean {
        val colorAddresses = setOf(
            0x90, 0x94, 0x98, 0x9C, 0xA0, 0xA4, 0xBB
        )
        return size == 4 && address in colorAddresses
    }

    private fun isProbablyMac(size: Int): Boolean {
        return size == 6
    }

    private fun isProbablyString(size: Int, data: ByteArray): Boolean {
        if (size !in 4..32) return false

        // Проверяем, что байты похожи на ASCII текст
        var hasNull = false
        for (byte in data) {
            if (byte == 0.toByte()) {
                hasNull = true
            } else if (byte < 0x20.toByte() && byte != 0x0A.toByte() && byte != 0x0D.toByte()) {
                return false // Непечатный символ
            }
        }

        return hasNull // Строки обычно заканчиваются нулем
    }

    private fun isProbablyFloat16_8(address: Int): Boolean {
        val floatAddresses = setOf(
            0x60 // SPEED_FACTOR
        )
        return address in floatAddresses
    }

    private fun isSigned(data: ByteArray): Boolean {
        // Если старший бит установлен, возможно это знаковое число
        return data.lastOrNull()?.let { (it.toInt() and 0x80) != 0 } ?: false
    }
}