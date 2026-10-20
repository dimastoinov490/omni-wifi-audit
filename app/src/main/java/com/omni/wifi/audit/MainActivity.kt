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
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val wifiState = permissions[Manifest.permission.ACCESS_WIFI_STATE] ?: false

        if (fineLocation || coarseLocation || wifiState) {
            startWifiScan()
        } else {
            Toast.makeText(this, "Требуются разрешения на геолокацию и Wi-Fi для сканирования сетей", Toast.LENGTH_LONG).show()
            isScanningState.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Initialize database data
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AuditDatabase.getDatabase(applicationContext)
            DataInitializer.populate(db.auditDao())
        }

        // Register broadcast receiver for scan results
        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                fetchScanResults()
            }
        }
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(wifiScanReceiver, intentFilter)
        }

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

    private fun checkAndStartScan() {
        isScanningState.value = true

        // 1. Check permissions
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

        // 2. Check GPS / Location service
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkLocationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkLocationEnabled) {
            Toast.makeText(this, "Служба геолокации (GPS) отключена. Включите её для сканирования сетей.", Toast.LENGTH_LONG).show()
            isScanningState.value = false
            return
        }

        startWifiScan()
    }

    private fun startWifiScan() {
        try {
            val success = wifiManager.startScan()
            if (!success) {
                // If startScan is throttled or failed, try fetching current results directly
                fetchScanResults()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка запуска сканирования: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            isScanningState.value = false
        }
    }

    private fun fetchScanResults() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                    wifiManager.scanResults
                } else {
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
                    Toast.makeText(this@MainActivity, "Ошибка получения результатов: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isScanningState.value = false
                }
            }
        }
    }
}
