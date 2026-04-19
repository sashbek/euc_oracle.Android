package com.example.euc_oracle_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.euc_oracle_android.data.ble.BleManager
import com.example.euc_oracle_android.databinding.ActivityMainBinding
import com.example.euc_oracle_android.ui.config.ConfigFragment
import com.example.euc_oracle_android.ui.scan.ScanFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showScanFragment()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleManager.getInstance(this)

        // Настройка обработчика back press
        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }

        if (hasRequiredPermissions()) {
            showScanFragment()
        } else {
            requestPermissions()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleBackPressed() {
        // Проверяем, какой фрагмент сейчас активен
        val currentFragment = supportFragmentManager.findFragmentById(binding.fragmentContainer.id)

        when (currentFragment) {
            is ConfigFragment -> {
                // Если мы в ConfigFragment, отключаемся от устройства
                Log.d("MainActivity", "Back pressed in ConfigFragment, disconnecting...")
                bleManager.disconnect()
                // Возвращаемся к ScanFragment
                supportFragmentManager.popBackStack()
            }
            is ScanFragment -> {
                // Если мы в ScanFragment, выходим из приложения
                Log.d("MainActivity", "Back pressed in ScanFragment, finishing...")
                finish()
            }
            else -> {
                // По умолчанию - стандартное поведение
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        requestPermissionLauncher.launch(permissions)
    }

    private fun showScanFragment() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, ScanFragment())
            .commit()
    }

    fun showConfigFragment() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, ConfigFragment())
            .addToBackStack("config")
            .commit()
    }
}