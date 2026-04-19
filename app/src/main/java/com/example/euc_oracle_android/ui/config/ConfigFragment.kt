package com.example.euc_oracle_android.ui.config

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.euc_oracle_android.R
import com.example.euc_oracle_android.data.ble.BleManager
import com.example.euc_oracle_android.data.repository.DeviceRepository
import com.example.euc_oracle_android.databinding.FragmentConfigBinding
import com.example.euc_oracle_android.models.Register
import com.example.euc_oracle_android.ui.config.dialogs.ValueEditDialog
import com.google.android.material.snackbar.Snackbar

class ConfigFragment : Fragment() {

    private lateinit var viewModel: ConfigViewModel
    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RegisterGroupAdapter
    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = BleManager.getInstance(requireContext())
        Log.d("ConfigFragment", "onCreate, deviceInfo: ${bleManager.deviceInfo.value}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ConfigFragment", "onViewCreated")

        val factory = ConfigViewModelFactory(requireContext(), bleManager)
        viewModel = ViewModelProvider(this, factory).get(ConfigViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeState()

        // Ждем немного перед загрузкой регистров
        view.postDelayed({
            Log.d("ConfigFragment", "Loading registers (delayed)...")
            viewModel.loadRegisters()
        }, 1000)
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "Configuration"
            inflateMenu(R.menu.config_menu)
            // Показываем кнопку "Назад"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener {
                // Отключаемся от устройства и возвращаемся
                disconnectAndGoBack()
            }
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_save -> {
                        viewModel.saveToFlash()
                        true
                    }
                    R.id.action_load -> {
                        viewModel.loadFromFlash()
                        true
                    }
                    R.id.action_reset -> {
                        showResetConfirmation()
                        true
                    }
                    else -> false
                }
            }
        }

        viewModel.deviceInfo.observe(viewLifecycleOwner) { deviceInfo ->
            deviceInfo?.let {
                binding.toolbar.subtitle = "${it.name} (${it.type.name})"
                Log.d("ConfigFragment", "Device info updated: $it")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectAndGoBack() {
        Log.d("ConfigFragment", "Disconnecting and going back...")

        // Отключаемся от устройства
        bleManager.disconnect()

        // Возвращаемся к сканированию
        parentFragmentManager.popBackStack()
    }

    private fun setupRecyclerView() {
        // Используем новый адаптер с поддержкой групп
        adapter = RegisterGroupAdapter(
            onItemClick = { register ->
                if (register.descriptor.writable) {
                    showEditDialog(register)
                }
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ConfigFragment.adapter
            itemAnimator = null
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRegisters()
        }
    }

    private fun observeState() {
        viewModel.registers.observe(viewLifecycleOwner) { registers ->
            Log.d("ConfigFragment", "=== REGISTERS UPDATED ===")
            Log.d("ConfigFragment", "Count: ${registers.size}")

            if (registers.isNotEmpty()) {
                registers.forEach { reg ->
                    Log.d("ConfigFragment", "  ${reg.descriptor.group ?: "Other"}: ${reg.descriptor.name} = ${reg.value}")
                }
            }

            // Группируем регистры
            val groupedRegisters = registers.groupBy { it.descriptor.group ?: "Other" }
                .toSortedMap()

            val flatList = mutableListOf<RegisterListItem>()
            groupedRegisters.forEach { (groupName, groupRegisters) ->
                if (groupRegisters.isNotEmpty()) {
                    flatList.add(RegisterListItem.Header(groupName))
                    groupRegisters.sortedBy { it.descriptor.address }.forEach { register ->
                        flatList.add(RegisterListItem.RegisterItem(register))
                    }
                }
            }

            adapter.submitList(flatList)
            binding.swipeRefresh.isRefreshing = false
            binding.emptyView.isVisible = registers.isEmpty()
            binding.recyclerView.isVisible = registers.isNotEmpty()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d("ConfigFragment", "Loading: $isLoading")
            binding.progressBar.isVisible = isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e("ConfigFragment", "Error: $it")
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        bleManager.connectionState.asLiveData().observe(viewLifecycleOwner) { state ->
            if (state == com.example.euc_oracle_android.data.ble.ConnectionState.DISCONNECTED) {
                // Если устройство отключилось, возвращаемся к сканированию
                Snackbar.make(binding.root, "Device disconnected", Snackbar.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun showEditDialog(register: Register) {
        val dialog = ValueEditDialog.newInstance(register)
        dialog.onValueChanged = { newValue ->
            viewModel.updateRegister(register.descriptor.address, newValue)
        }
        dialog.show(childFragmentManager, "edit_dialog")
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset to Defaults")
            .setMessage("Are you sure you want to reset all settings to factory defaults?")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        // Не отключаемся здесь, чтобы сохранить соединение при повороте экрана
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отключаемся только при полном уничтожении фрагмента
        if (isRemoving) {
            bleManager.disconnect()
        }
    }
}

// Элементы списка
sealed class RegisterListItem {
    data class Header(val title: String) : RegisterListItem()
    data class RegisterItem(val register: Register) : RegisterListItem()
}

// Адаптер с поддержкой групп
class RegisterGroupAdapter(
    private val onItemClick: (Register) -> Unit
) : androidx.recyclerview.widget.ListAdapter<RegisterListItem, androidx.recyclerview.widget.RecyclerView.ViewHolder>(RegisterListItemDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RegisterListItem.Header -> TYPE_HEADER
            is RegisterListItem.RegisterItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_group_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_ITEM -> {
                val binding = com.example.euc_oracle_android.databinding.ItemRegisterBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                RegisterViewHolder(binding, onItemClick)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RegisterListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is RegisterListItem.RegisterItem -> (holder as RegisterViewHolder).bind(item.register)
        }
    }

    class HeaderViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val titleText: android.widget.TextView = itemView.findViewById(R.id.group_title)

        fun bind(title: String) {
            // Форматируем название группы для лучшего отображения
            val displayTitle = when (title) {
                "ThirdEye" -> "👁️ Third Eye"
                "LED Proxy" -> "🔗 LED Proxy"
                "LED Effects" -> "💡 LED Effects"
                "UART" -> "📡 UART"
                "Dynamic" -> "📊 Dynamic"
                "System" -> "⚙️ System"
                else -> title
            }
            titleText.text = displayTitle
        }
    }

    class RegisterViewHolder(
        private val binding: com.example.euc_oracle_android.databinding.ItemRegisterBinding,
        private val onItemClick: (Register) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(register: Register) {
            binding.apply {
                nameText.text = register.descriptor.displayName
                addressText.text = "0x%02X".format(register.descriptor.address)
                valueText.text = formatValue(register)

                if (!register.descriptor.writable) {
                    root.alpha = 0.5f
                    editIcon.visibility = View.GONE
                    valueText.setTextColor(valueText.context.getColor(android.R.color.darker_gray))
                } else {
                    root.alpha = 1.0f
                    editIcon.visibility = View.VISIBLE
                    valueText.setTextColor(valueText.context.getColor(android.R.color.holo_blue_dark))
                }

                root.setOnClickListener {
                    onItemClick(register)
                }
            }
        }

        private fun formatValue(register: Register): String {
            val value = register.value ?: return "—"

            return when (value) {
                is com.example.euc_oracle_android.models.RegisterValue.IntValue -> {
                    val scaledValue = register.descriptor.scale?.let { value.value * it } ?: value.value.toDouble()
                    val unit = register.descriptor.unit?.let { " $it" } ?: ""
                    if (scaledValue == scaledValue.toLong().toDouble()) {
                        "${scaledValue.toLong()}$unit"
                    } else {
                        "%.2f%s".format(scaledValue, unit)
                    }
                }
                is com.example.euc_oracle_android.models.RegisterValue.FloatValue -> {
                    val scaledValue = register.descriptor.scale?.let { value.value * it } ?: value.value
                    val unit = register.descriptor.unit?.let { " $it" } ?: ""
                    "%.2f%s".format(scaledValue, unit)
                }
                is com.example.euc_oracle_android.models.RegisterValue.StringValue -> value.value
                is com.example.euc_oracle_android.models.RegisterValue.BooleanValue -> if (value.value) "ON" else "OFF"
                is com.example.euc_oracle_android.models.RegisterValue.ColorValue -> {
                    "█ #${"%02X".format(value.r)}${"%02X".format(value.g)}${"%02X".format(value.b)}"
                }
                is com.example.euc_oracle_android.models.RegisterValue.EnumValue -> {
                    value.options[value.value] ?: "Unknown"
                }
                is com.example.euc_oracle_android.models.RegisterValue.MacValue -> value.toStringValue()
                is com.example.euc_oracle_android.models.RegisterValue.RawValue -> {
                    if (value.bytes.size <= 4) {
                        value.bytes.joinToString(" ") { "%02X".format(it) }
                    } else {
                        "[${value.bytes.size} bytes]"
                    }
                }
            }
        }
    }

    private class RegisterListItemDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<RegisterListItem>() {
        override fun areItemsTheSame(oldItem: RegisterListItem, newItem: RegisterListItem): Boolean {
            return when {
                oldItem is RegisterListItem.Header && newItem is RegisterListItem.Header -> oldItem.title == newItem.title
                oldItem is RegisterListItem.RegisterItem && newItem is RegisterListItem.RegisterItem ->
                    oldItem.register.descriptor.address == newItem.register.descriptor.address
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: RegisterListItem, newItem: RegisterListItem): Boolean {
            return when {
                oldItem is RegisterListItem.Header && newItem is RegisterListItem.Header -> oldItem == newItem
                oldItem is RegisterListItem.RegisterItem && newItem is RegisterListItem.RegisterItem ->
                    oldItem.register == newItem.register
                else -> false
            }
        }
    }
}

// Исправленная фабрика
class ConfigViewModelFactory(
    private val context: android.content.Context,
    private val bleManager: BleManager
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfigViewModel::class.java)) {
            val deviceRepository = DeviceRepository(bleManager, context)
            @Suppress("UNCHECKED_CAST")
            return ConfigViewModel(deviceRepository, bleManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}