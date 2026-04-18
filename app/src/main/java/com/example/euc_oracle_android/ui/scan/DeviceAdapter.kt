package com.example.euc_oracle_android.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.euc_oracle_android.R
import com.example.euc_oracle_android.data.ble.BleDevice
import com.example.euc_oracle_android.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onItemClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, DeviceAdapter.ViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(
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
        private val binding: ItemDeviceBinding,
        private val onItemClick: (BleDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BleDevice) {
            binding.apply {
                deviceNameText.text = device.displayName
                deviceAddressText.text = device.address

                root.setOnClickListener {
                    onItemClick(device)
                }
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<BleDevice>() {
        override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem == newItem
        }
    }
}