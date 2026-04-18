package com.example.euc_oracle_android.ui.config

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.euc_oracle_android.databinding.ItemRegisterBinding
import com.example.euc_oracle_android.models.Register
import com.example.euc_oracle_android.models.RegisterType
import com.example.euc_oracle_android.models.RegisterValue

class RegisterItemAdapter(
    private val onItemClick: (Register) -> Unit
) : ListAdapter<Register, RegisterItemAdapter.ViewHolder>(RegisterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRegisterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemRegisterBinding,
        private val onItemClick: (Register) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(register: Register) {
            binding.apply {
                nameText.text = register.descriptor.name
                addressText.text = "0x%02X".format(register.descriptor.address)
                valueText.text = formatValue(register)

                // Визуальное различие для read-only регистров
                if (!register.descriptor.writable) {
                    root.alpha = 0.7f
                    editIcon.isVisible = false
                } else {
                    root.alpha = 1.0f
                    editIcon.isVisible = true
                }

                root.setOnClickListener {
                    if (register.descriptor.writable) {
                        onItemClick(register)
                    }
                }
            }
        }

        private fun formatValue(register: Register): String {
            val value = register.value ?: return "Unknown"

            return when (value) {
                is RegisterValue.IntValue -> {
                    val unit = register.descriptor.unit?.let { " $it" } ?: ""
                    when (register.descriptor.type) {
                        RegisterType.FLOAT16_8 -> "%.2f%s".format(value.value / 256.0, unit)
                        else -> "${value.value}$unit"
                    }
                }
                is RegisterValue.FloatValue -> {
                    val unit = register.descriptor.unit?.let { " $it" } ?: ""
                    "%.2f%s".format(value.value, unit)
                }
                is RegisterValue.StringValue -> value.value
                is RegisterValue.BooleanValue -> if (value.value) "ON" else "OFF"
                is RegisterValue.ColorValue -> {
                    "RGB(${value.r},${value.g},${value.b})"
                }
                is RegisterValue.EnumValue -> {
                    value.options[value.value] ?: "Unknown(${value.value})"
                }
                is RegisterValue.MacValue -> value.toStringValue()
                is RegisterValue.RawValue -> {
                    value.bytes.joinToString(" ") { "%02X".format(it) }
                }
            }
        }
    }

    private class RegisterDiffCallback : DiffUtil.ItemCallback<Register>() {
        override fun areItemsTheSame(oldItem: Register, newItem: Register): Boolean {
            return oldItem.descriptor.address == newItem.descriptor.address
        }

        override fun areContentsTheSame(oldItem: Register, newItem: Register): Boolean {
            return oldItem == newItem
        }
    }
}