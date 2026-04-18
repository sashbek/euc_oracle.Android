package com.example.euc_oracle_android.data.repository

import android.content.Context
import android.util.Log
import com.example.euc_oracle_android.data.protocol.models.DeviceType
import com.example.euc_oracle_android.models.GroupJson
import com.example.euc_oracle_android.models.RegisterDescriptor
import com.example.euc_oracle_android.models.RegisterJson
import com.example.euc_oracle_android.models.RegisterType
import com.example.euc_oracle_android.models.RegistersConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class RegisterGroup(
    val name: String,
    val description: String,
    val registers: List<RegisterDescriptor>
)

class RegisterLoader(private val context: Context) {

    private val gson = Gson()

    fun loadRegisters(deviceType: DeviceType): List<RegisterDescriptor> {
        val fileName = when (deviceType) {
            DeviceType.WHEEL -> "wheel_registers.json"
            DeviceType.LED -> "led_registers.json"
            DeviceType.UNKNOWN -> return emptyList()
        }

        return try {
            Log.d("RegisterLoader", "Loading registers from assets: $fileName")
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<RegistersConfig>() {}.type
            val config: RegistersConfig = gson.fromJson(reader, type)
            reader.close()
            inputStream.close()

            Log.d("RegisterLoader", "Loaded ${config.registers.size} registers from JSON")

            config.registers
                .filter { !it.hidden }
                .map { json ->
                    RegisterDescriptor(
                        address = json.address,
                        name = json.name,
                        type = RegisterType.fromString(json.type),
                        size = json.size,
                        readable = json.readable,
                        writable = json.writable,
                        hidden = json.hidden,
                        persistent = json.persistent,
                        min = json.min,
                        max = json.max,
                        enumValues = json.enumValues?.mapKeys { it.key.toIntOrNull() ?: 0 },
                        unit = json.unit,
                        scale = json.scale,
                        bitfield = json.bitfield,
                        defaultValue = parseDefaultValue(json.defaultValue, json.type),
                        group = json.group,
                        description = json.description
                    )
                }
        } catch (e: Exception) {
            Log.e("RegisterLoader", "Failed to load registers from assets", e)

            // Если не удалось загрузить из assets, возвращаем базовый набор регистров
            getFallbackRegisters(deviceType)
        }
    }

    private fun getFallbackRegisters(deviceType: DeviceType): List<RegisterDescriptor> {
        Log.w("RegisterLoader", "Using fallback registers for $deviceType")
        return when (deviceType) {
            DeviceType.WHEEL -> listOf(
                RegisterDescriptor(0x10, "DEVICE_ID", RegisterType.UINT32, 4, writable = false, group = "System"),
                RegisterDescriptor(0x14, "FW_VERSION", RegisterType.UINT32, 4, writable = false, group = "System"),
                RegisterDescriptor(0x26, "BOOT_COUNT", RegisterType.UINT32, 4, group = "System"),
                RegisterDescriptor(0x27, "DEVICE_NAME", RegisterType.STRING, 16, group = "System"),
                RegisterDescriptor(0x40, "THIRD_EYE_ENABLED", RegisterType.BOOLEAN, 1, group = "ThirdEye"),
                RegisterDescriptor(0x44, "THIRD_EYE_BRIGHTNESS", RegisterType.UINT8, 1, min = 0.0, max = 255.0, group = "ThirdEye"),
                RegisterDescriptor(0xC0, "DYNAMIC_SPEED", RegisterType.UINT16, 2, writable = false, unit = "km/h", scale = 0.1, group = "Dynamic"),
                RegisterDescriptor(0xC4, "DYNAMIC_BATTERY", RegisterType.UINT8, 1, writable = false, unit = "%", group = "Dynamic")
            )
            DeviceType.LED -> listOf(
                RegisterDescriptor(0x10, "DEVICE_ID", RegisterType.UINT32, 4, writable = false, group = "System"),
                RegisterDescriptor(0x80, "GLOBAL_BRIGHTNESS", RegisterType.UINT8, 1, min = 0.0, max = 255.0, group = "LED"),
                RegisterDescriptor(0x84, "EFFECT_MODE", RegisterType.ENUM, 1, enumValues = mapOf(0 to "Static", 1 to "Speed", 2 to "Battery", 3 to "Rainbow"), group = "LED"),
                RegisterDescriptor(0x90, "PRIMARY_COLOR", RegisterType.COLOR_RGBW, 4, group = "LED")
            )
            DeviceType.UNKNOWN -> emptyList()
        }
    }

    fun loadGroups(deviceType: DeviceType): List<RegisterGroup> {
        val fileName = when (deviceType) {
            DeviceType.WHEEL -> "wheel_registers.json"
            DeviceType.LED -> "led_registers.json"
            DeviceType.UNKNOWN -> return emptyList()
        }

        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<RegistersConfig>() {}.type
            val config: RegistersConfig = gson.fromJson(reader, type)
            reader.close()
            inputStream.close()

            val registers = config.registers
                .filter { !it.hidden }
                .map { json ->
                    RegisterDescriptor(
                        address = json.address,
                        name = json.name,
                        type = RegisterType.fromString(json.type),
                        size = json.size,
                        readable = json.readable,
                        writable = json.writable,
                        hidden = json.hidden,
                        persistent = json.persistent,
                        min = json.min,
                        max = json.max,
                        enumValues = json.enumValues?.mapKeys { it.key.toIntOrNull() ?: 0 },
                        unit = json.unit,
                        scale = json.scale,
                        bitfield = json.bitfield,
                        defaultValue = parseDefaultValue(json.defaultValue, json.type),
                        group = json.group,
                        description = json.description
                    )
                }

            val groups = config.groups ?: emptyList()

            groups.map { group ->
                RegisterGroup(
                    name = group.name,
                    description = group.description,
                    registers = registers.filter { it.group == group.name }
                )
            }.filter { it.registers.isNotEmpty() }

        } catch (e: Exception) {
            Log.e("RegisterLoader", "Failed to load groups", e)
            emptyList()
        }
    }

    private fun parseDefaultValue(value: Any?, type: String): Any? {
        if (value == null) return null

        return when (type.uppercase()) {
            "BOOLEAN" -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> false
            }
            "STRING" -> value.toString()
            "COLOR_RGBW", "RGBW" -> {
                when (value) {
                    is String -> {
                        val hex = value.removePrefix("#")
                        hex.toLongOrNull(16) ?: 0xFF000000
                    }
                    is Number -> value.toLong()
                    else -> 0xFF000000
                }
            }
            else -> when (value) {
                is Number -> value
                is String -> value.toDoubleOrNull() ?: 0
                else -> 0
            }
        }
    }
}