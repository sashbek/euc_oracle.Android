package com.example.euc_oracle_android.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ByteUtils {

    fun Short.toByteArray(): ByteArray {
        return ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(this)
            .array()
    }

    fun Int.toByteArray(): ByteArray {
        return ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(this)
            .array()
    }

    fun Long.toByteArray(): ByteArray {
        return ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(this)
            .array()
    }

    fun ByteArray.toShort(): Short {
        return ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
    }

    fun ByteArray.toInt(): Int {
        return ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    fun ByteArray.toLong(): Long {
        return ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
    }

    fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        require(cleanHex.length % 2 == 0) { "Invalid hex string" }

        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            cleanHex.substring(index, index + 2).toInt(16).toByte()
        }
    }
}