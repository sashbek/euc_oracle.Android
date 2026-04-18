package com.example.euc_oracle_android.ui.scan

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.euc_oracle_android.MainActivity
import com.example.euc_oracle_android.databinding.FragmentScanBinding
import com.example.euc_oracle_android.data.ble.BleManager
import com.example.euc_oracle_android.data.ble.ConnectionState
import com.google.android.material.snackbar.Snackbar

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ScanViewModel
    private lateinit var adapter: DeviceAdapter
    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = BleManager.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Создаем ViewModel с фабрикой
        val factory = ScanViewModelFactory(bleManager)
        viewModel = ViewModelProvider(this, factory).get(ScanViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeState()

        viewModel.startScan()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "MonoWheel Devices"
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun setupRecyclerView() {
        adapter = DeviceAdapter { device ->
            viewModel.connectToDevice(device)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ScanFragment.adapter
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshScan()
        }
    }

    private fun observeState() {
        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            adapter.submitList(devices)
            binding.emptyView.isVisible = devices.isEmpty()
            binding.recyclerView.isVisible = devices.isNotEmpty()
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { isScanning ->
            binding.swipeRefresh.isRefreshing = isScanning
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            Log.d("ScanFragment", "Connection state: $state")
            when (state) {
                ConnectionState.CONNECTING -> {
                    binding.progressBar.isVisible = true
                }
                ConnectionState.CONNECTED -> {
                    Log.d("ScanFragment", "CONNECTED! Showing config fragment...")
                    binding.progressBar.isVisible = false
                    (requireActivity() as MainActivity).showConfigFragment()
                }
                ConnectionState.ERROR -> {
                    Log.e("ScanFragment", "Connection ERROR")
                    binding.progressBar.isVisible = false
                }
                else -> {
                    binding.progressBar.isVisible = false
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopScan()
        _binding = null
    }
}

// Фабрика для создания ScanViewModel
class ScanViewModelFactory(
    private val bleManager: BleManager
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScanViewModel(bleManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}