package com.example.euc_oracle_android.ui.config.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.euc_oracle_android.R
import com.example.euc_oracle_android.models.Register
import com.example.euc_oracle_android.models.RegisterType
import com.example.euc_oracle_android.models.RegisterValue
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ValueEditDialog : DialogFragment() {

    private lateinit var register: Register
    var onValueChanged: ((RegisterValue) -> Unit)? = null

    companion object {
        private const val ARG_ADDRESS = "address"

        fun newInstance(register: Register): ValueEditDialog {
            return ValueEditDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ADDRESS, register.descriptor.address)
                }
                this.register = register
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Заголовок с адресом
        val addressText = TextView(context).apply {
            text = "Address: 0x${register.descriptor.address.toString(16).uppercase()}"
            textSize = 14f
            setTextColor(context.getColor(android.R.color.darker_gray))
            setPadding(0, 0, 0, 8)
        }
        layout.addView(addressText)

        // Тип данных
        val typeText = TextView(context).apply {
            text = "Type: ${register.descriptor.type}"
            textSize = 12f
            setTextColor(context.getColor(android.R.color.darker_gray))
            setPadding(0, 0, 0, 24)
        }
        layout.addView(typeText)

        // Поле ввода в зависимости от типа
        val inputView = createInputView(context)
        layout.addView(inputView)

        return AlertDialog.Builder(context)
            .setTitle(register.descriptor.name)
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newValue = getValueFromInput(inputView)
                newValue?.let { onValueChanged?.invoke(it) }
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun createInputView(context: android.content.Context): View {
        return when (register.descriptor.type) {
            RegisterType.BOOLEAN -> createBooleanInput(context)
            RegisterType.ENUM -> createEnumInput(context)
            RegisterType.UINT8, RegisterType.INT8,
            RegisterType.UINT16, RegisterType.INT16,
            RegisterType.UINT32, RegisterType.INT32 -> createNumberInput(context)
            RegisterType.FLOAT16_8 -> createFloatInput(context)
            RegisterType.STRING -> createTextInput(context)
            else -> createTextInput(context)
        }
    }

    private fun createBooleanInput(context: android.content.Context): View {
        val currentValue = (register.value as? RegisterValue.BooleanValue)?.value ?: false

        return Switch(context).apply {
            isChecked = currentValue
            text = if (currentValue) "ON" else "OFF"

            setOnCheckedChangeListener { _, isChecked ->
                text = if (isChecked) "ON" else "OFF"
            }
        }
    }

    private fun createEnumInput(context: android.content.Context): View {
        val enumValues = register.descriptor.enumValues ?: emptyMap()
        val currentValue = (register.value as? RegisterValue.EnumValue)?.value ?: 0

        val items = enumValues.values.toList()
        val selectedIndex = enumValues.keys.indexOf(currentValue).coerceAtLeast(0)

        return Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                items
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedIndex)
        }
    }

    private fun createNumberInput(context: android.content.Context): View {
        val currentValue = when (val v = register.value) {
            is RegisterValue.IntValue -> v.value.toString()
            else -> "0"
        }

        val layout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val editText = TextInputEditText(context).apply {
            setText(currentValue)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        layout.addView(editText)
        return layout
    }

    private fun createFloatInput(context: android.content.Context): View {
        val currentValue = when (val v = register.value) {
            is RegisterValue.FloatValue -> v.value.toString()
            is RegisterValue.IntValue -> v.value.toString()
            else -> "0.0"
        }

        val layout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "Value (e.g. 1.5)"
        }

        val editText = TextInputEditText(context).apply {
            setText(currentValue)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        layout.addView(editText)
        return layout
    }

    private fun createTextInput(context: android.content.Context): View {
        val currentValue = when (val v = register.value) {
            is RegisterValue.StringValue -> v.value
            else -> ""
        }

        val layout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val editText = TextInputEditText(context).apply {
            setText(currentValue)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        layout.addView(editText)
        return layout
    }

    private fun getValueFromInput(view: View): RegisterValue? {
        return when (view) {
            is Switch -> RegisterValue.BooleanValue(view.isChecked)
            is Spinner -> {
                val selectedPosition = view.selectedItemPosition
                val enumValue = register.descriptor.enumValues?.keys?.elementAtOrNull(selectedPosition) ?: 0
                RegisterValue.EnumValue(enumValue, register.descriptor.enumValues ?: emptyMap())
            }
            is TextInputLayout -> {
                val editText = view.editText
                val text = editText?.text?.toString() ?: return null

                when (register.descriptor.type) {
                    RegisterType.UINT8, RegisterType.INT8,
                    RegisterType.UINT16, RegisterType.INT16,
                    RegisterType.UINT32, RegisterType.INT32 -> {
                        val longValue = text.toLongOrNull() ?: 0L
                        RegisterValue.IntValue(longValue, register.descriptor.type)
                    }
                    RegisterType.FLOAT16_8 -> {
                        val doubleValue = text.toDoubleOrNull() ?: 0.0
                        RegisterValue.FloatValue(doubleValue, register.descriptor.type)
                    }
                    RegisterType.STRING -> RegisterValue.StringValue(text)
                    else -> RegisterValue.StringValue(text)
                }
            }
            else -> null
        }
    }
}
