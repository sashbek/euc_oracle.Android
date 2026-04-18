package com.example.euc_oracle_android.data.protocol

import android.util.Log

object PacketParser {

    /**
     * Парсит ответ от устройства (данные из READ характеристики)
     * Формат: [OFFSET_L][OFFSET_H][DATA...]
     */
    fun parseReadResponse(data: ByteArray): Pair<Int, ByteArray>? {
        if (data.size < 2) {
            Log.w("PacketParser", "Response too short: ${data.size} bytes")
            return null
        }

        val offset = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        val payload = data.copyOfRange(2, data.size)

        Log.d("PacketParser", "Parsed response: offset=$offset, payload=${payload.size} bytes")
        return offset to payload
    }

    /**
     * Проверяет, является ли пакет маркером начала
     */
    fun isStartMarker(data: ByteArray, offset: Int = 0): Boolean {
        if (data.size - offset < 4) return false
        return data[offset] == 0x55.toByte() &&
                data[offset + 1] == 0xAA.toByte() &&
                data[offset + 2] == 0x55.toByte() &&
                data[offset + 3] == 0xAA.toByte()
    }

    /**
     * Проверяет, является ли пакет маркером конца
     */
    fun isEndMarker(data: ByteArray, offset: Int = 0): Boolean {
        if (data.size - offset < 4) return false
        return data[offset] == 0x55.toByte() &&
                data[offset + 1] == 0xBB.toByte() &&
                data[offset + 2] == 0x55.toByte() &&
                data[offset + 3] == 0xBB.toByte()
    }
}