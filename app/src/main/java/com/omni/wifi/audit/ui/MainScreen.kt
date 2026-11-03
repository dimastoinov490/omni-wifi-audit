package com.omni.wifi.audit.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omni.wifi.audit.domain.hardware.SystemInfoProvider
import com.omni.wifi.audit.domain.hardware.SystemOverview
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.components.NavigationTab
import com.omni.wifi.audit.ui.components.PrototypeBottomNavBar
import com.omni.wifi.audit.ui.screens.*
import com.omni.wifi.audit.ui.theme.BackgroundBrush
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    scanResults: List<AccessPointInfo>,
    isScanning: Boolean = false,
    onScanClick: () -> Unit = {},
    onUpdateGithub: () -> Unit = {},
    onUpdateLocalFile: () -> Unit = {},
    onAccessPointClick: (AccessPointInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var diagnosticTargetAp by remember { mutableStateOf<AccessPointInfo?>(null) }

    var systemInfo by remember {
        mutableStateOf(
            SystemOverview(
                driverName = "qcacld-3.0",
                androidVersion = Build.VERSION.RELEASE ?: "13",
                rootStatusText = "Проверка...",
                isRootGranted = false,
                batteryPercent = 100,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                boardPlatform = Build.BOARD ?: "whyred"
            )
        )
    }

    fun refreshSystemInfo() {
        coroutineScope.launch {
            systemInfo = SystemInfoProvider.getSystemOverview(context)
        }
    }

    LaunchedEffect(Unit) {
        refreshSystemInfo()
    }

    BackHandler(enabled = currentTab != NavigationTab.HOME) {
        currentTab = NavigationTab.HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) {
            when (currentTab) {
                NavigationTab.HOME -> {
                    HomeScreen(
                        scanResults = scanResults,
                        isScanning = isScanning,
                        systemInfo = systemInfo,
                        onRefresh = {
                            refreshSystemInfo()
                            onScanClick()
                        },
                        onNavigateToNetworks = { currentTab = NavigationTab.NETWORKS },
                        onNavigateToDiagnostics = { currentTab = NavigationTab.DIAGNOSTICS }
                    )
                }

                NavigationTab.NETWORKS -> {
                    NetworksScreen(
                        scanResults = scanResults,
                        isScanning = isScanning,
                        selectedAp = null,
                        onAccessPointClick = { ap ->
                            onAccessPointClick(ap)
                        },
                        onRefresh = {
                            refreshSystemInfo()
                            onScanClick()
                        }
                    )
                }

                NavigationTab.DIAGNOSTICS -> {
                    DiagnosticsTabScreen(
                        currentTarget = diagnosticTargetAp ?: scanResults.firstOrNull(),
                        allAccessPoints = scanResults,
                        onSelectTarget = { ap -> diagnosticTargetAp = ap }
                    )
                }

                NavigationTab.HISTORY -> {
                    HistoryScreen(
                        onSelectReport = { report ->
                            val matched = scanResults.firstOrNull { it.bssid.equals(report.targetBssid, ignoreCase = true) }
                            if (matched != null) {
                                onAccessPointClick(matched)
                            }
                        }
                    )
                }
            }
        }

        PrototypeBottomNavBar(
            selectedTab = currentTab,
            onTabSelected = { tab ->
                currentTab = tab
                if (tab == NavigationTab.HOME || tab == NavigationTab.NETWORKS) {
                    refreshSystemInfo()
                }
            },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
