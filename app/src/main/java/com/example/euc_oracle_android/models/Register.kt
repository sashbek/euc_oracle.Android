package com.example.euc_oracle_android.models

enum class RegisterType {
    UINT8, INT8, UINT16, INT16, UINT32, INT32,
    FLOAT16_8, STRING, RGBW, COLOR_RGBW, ENUM, BOOLEAN, MAC, UNKNOWN;

    companion object {
        fun fromString(type: String): RegisterType {
            return when (type.uppercase()) {
                "UINT8" -> UINT8
                "INT8" -> INT8
                "UINT16" -> UINT16
                "INT16" -> INT16
                "UINT32" -> UINT32
                "INT32" -> INT32
                "FLOAT16_8" -> FLOAT16_8
                "STRING" -> STRING
                "RGBW", "COLOR_RGBW" -> COLOR_RGBW
                "ENUM" -> ENUM
                "BOOLEAN" -> BOOLEAN
                "MAC" -> MAC
                else -> UNKNOWN
            }
        }
    }
}

data class RegisterDescriptor(
    val address: Int,
    val name: String,
    val type: RegisterType,
    val size: Int,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val hidden: Boolean = false,
    val persistent: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val enumValues: Map<Int, String>? = null,
    val unit: String? = null,
    val scale: Double? = null,
    val bitfield: Boolean = false,
    val defaultValue: Any? = null,
    val group: String? = null,
    val description: String? = null
) {
    val displayName: String
        get() = name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

sealed class RegisterValue {
    data class IntValue(val value: Long, val type: RegisterType) : RegisterValue()
    data class FloatValue(val value: Double, val type: RegisterType) : RegisterValue()
    data class StringValue(val value: String) : RegisterValue()
    data class BooleanValue(val value: Boolean) : RegisterValue()
    data class ColorValue(val r: Int, val g: Int, val b: Int, val w: Int) : RegisterValue()
    data class MacValue(val bytes: ByteArray) : RegisterValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MacValue
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()

        fun toStringValue(): String {
            return bytes.joinToString(":") { "%02X".format(it) }
        }
    }
    data class EnumValue(val value: Int, val options: Map<Int, String>) : RegisterValue()
    data class RawValue(val bytes: ByteArray) : RegisterValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RawValue
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

data class Register(
    val descriptor: RegisterDescriptor,
    var value: RegisterValue?
)