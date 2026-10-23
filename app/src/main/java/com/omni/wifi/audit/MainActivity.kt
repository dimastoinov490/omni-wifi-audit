package com.omni.wifi.audit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.omni.wifi.audit.data.AuditDatabase
import com.omni.wifi.audit.data.DatabaseCacheManager
import com.omni.wifi.audit.data.DatabaseManager
import com.omni.wifi.audit.data.DataInitializer
import com.omni.wifi.audit.domain.VulnerabilityAnalyzer
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.DetailScreen
import com.omni.wifi.audit.ui.MainScreen
import com.omni.wifi.audit.ui.theme.OmniWifiAuditTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager
    private val analyzer by lazy { VulnerabilityAnalyzer(this) }

    private var scanResultsState = mutableStateOf<List<AccessPointInfo>>(emptyList())
    private var isScanningState = mutableStateOf(false)
    private var wifiScanReceiver: BroadcastReceiver? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            val wifiState = permissions[Manifest.permission.ACCESS_WIFI_STATE] ?: false

            if (fineLocation || coarseLocation || wifiState) {
                startWifiScan()
            } else {
                Toast.makeText(this, "Требуются разрешения на геолокацию и Wi-Fi для сканирования сетей", Toast.LENGTH_LONG).show()
                isScanningState.value = false
            }
        } catch (e: Exception) {
            isScanningState.value = false
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = AuditDatabase.getDatabase(applicationContext)
                    val result = DatabaseManager.updateFromUri(applicationContext, db.auditDao(), uri)
                    if (result.isSuccess) {
                        DatabaseCacheManager.refreshCache(applicationContext, db.auditDao())
                    }
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(this@MainActivity, "Базы успешно обновлены из файла! Загружено: ${result.getOrDefault(0)} записей", Toast.LENGTH_LONG).show()
                            fetchScanResults()
                        } else {
                            Toast.makeText(this@MainActivity, "Ошибка загрузки файла: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Ошибка обработки файла: ${e.localizedMessage}. Переход на локальный кэш.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка инициализации Wi-Fi сервиса", Toast.LENGTH_SHORT).show()
        }

        // Initialize database data & DatabaseManager snapshot & In-Memory Cache Manager on Dispatchers.IO
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AuditDatabase.getDatabase(applicationContext)
                DataInitializer.populate(db.auditDao())
                DatabaseManager.saveDefaultDatabaseSnapshot(applicationContext, db.auditDao())
                DatabaseCacheManager.initialize(applicationContext, db.auditDao())
            } catch (e: Exception) {
                // Fail-safe initialization fallback
            }
        }

        // Register broadcast receiver for scan results with try-catch
        try {
            wifiScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    fetchScanResults()
                }
            }
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(wifiScanReceiver, intentFilter)
            }
        } catch (e: Exception) {}

        setContent {
            OmniWifiAuditTheme {
                var selectedAp by remember { mutableStateOf<AccessPointInfo?>(null) }
                val scanResults by scanResultsState
                val isScanning by isScanningState

                if (selectedAp == null) {
                    MainScreen(
                        scanResults = scanResults,
                        isScanning = isScanning,
                        onScanClick = {
                            checkAndStartScan()
                        },
                        onUpdateGithub = {
                            updateDatabaseFromGithub()
                        },
                        onUpdateLocalFile = {
                            try {
                                filePickerLauncher.launch("*/*")
                            } catch (e: Exception) {
                                Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAccessPointClick = { ap -> selectedAp = ap }
                    )
                } else {
                    DetailScreen(
                        accessPoint = selectedAp!!,
                        onBack = { selectedAp = null }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }

    private fun updateDatabaseFromGithub() {
        Toast.makeText(this, "Загрузка обновлений с GitHub (оффлайн-автовосстановление)...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AuditDatabase.getDatabase(applicationContext)
                val result = DatabaseManager.updateFromGithub(applicationContext, db.auditDao())
                if (result.isSuccess) {
                    DatabaseCacheManager.refreshCache(applicationContext, db.auditDao())
                }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "Базы успешно обновлены с GitHub! Загружено: ${result.getOrDefault(0)} записей", Toast.LENGTH_LONG).show()
                        fetchScanResults()
                    } else {
                        Toast.makeText(this@MainActivity, "Интернет недоступен. Приложение работает в надежном оффлайн-режиме.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сети. Используется встроенный оффлайн-кэш баз данных.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkAndStartScan() {
        isScanningState.value = true

        try {
            val hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasWifiState = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLoc || !hasCoarseLoc || !hasWifiState) {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
                return
            }

            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { true }
            val isNetworkLocationEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { true }

            if (!isGpsEnabled && !isNetworkLocationEnabled) {
                Toast.makeText(this, "Служба геолокации (GPS) отключена. Рекомендуется включить её для точного сканирования сетей.", Toast.LENGTH_LONG).show()
            }

            startWifiScan()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка проверки разрешений/геолокации: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            isScanningState.value = false
        }
    }

    private fun startWifiScan() {
        try {
            val success = wifiManager.startScan()
            if (!success) {
                fetchScanResults()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка запуска сканирования: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            isScanningState.value = false
            fetchScanResults()
        }
    }

    private fun fetchScanResults() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = try {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                        wifiManager.scanResults ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                val analyzedList = results.map { scanResult ->
                    analyzer.analyzeAccessPoint(scanResult)
                }.sortedByDescending { it.signalLevel }

                withContext(Dispatchers.Main) {
                    scanResultsState.value = analyzedList
                    isScanningState.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка получения результатов сканирования: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isScanningState.value = false
                }
            }
        }
    }
}
