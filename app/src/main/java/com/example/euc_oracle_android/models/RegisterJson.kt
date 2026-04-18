package com.example.euc_oracle_android.models

import com.google.gson.annotations.SerializedName

data class RegisterJson(
    val address: Int,
    val name: String,
    val type: String,
    val size: Int,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val hidden: Boolean = false,
    val persistent: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    @SerializedName("enumValues")
    val enumValues: Map<String, String>? = null,
    val unit: String? = null,
    val scale: Double? = null,
    val bitfield: Boolean = false,
    val defaultValue: Any? = null,
    val group: String? = null,
    val description: String? = null
)

data class GroupJson(
    val name: String,
    val description: String
)

data class RegistersConfig(
    val deviceType: String,
    val version: String,
    val registers: List<RegisterJson>,
    val groups: List<GroupJson>? = null
)