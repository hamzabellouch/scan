package com.tkno.scan.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tkno.scan.*
import com.tkno.scan.ui.component.ClassicBottomBar
import com.tkno.scan.ui.component.FloatingBottomBar
import com.tkno.scan.ui.component.loadNavOrder
import com.tkno.scan.ui.menu.MenuScreen
import com.tkno.scan.ui.page.AppUpdater
import com.tkno.scan.util.PreferenceUtil

enum class Tab {
    Scan, Library, Advance, Menu
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navigateToUpdate: Boolean = false,
    triggerUpdate: Boolean = false,
    onNavigateToUpdateConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceUtil.getPrefs(context) }
    var isAutoUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("auto_update_enabled", false)) }
    var hideLabels by remember { mutableStateOf(prefs.getBoolean("hide_navigation_labels", false)) }
    var useClassicTaskbar by remember { mutableStateOf(prefs.getBoolean("use_classic_taskbar", false)) }
    var animateIndicator by remember { mutableStateOf(prefs.getBoolean("animate_taskbar_indicator", true)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "auto_update_enabled" -> isAutoUpdateEnabled = p.getBoolean("auto_update_enabled", false)
                "hide_navigation_labels" -> hideLabels = p.getBoolean("hide_navigation_labels", false)
                "use_classic_taskbar" -> useClassicTaskbar = p.getBoolean("use_classic_taskbar", false)
                "animate_taskbar_indicator" -> animateIndicator = p.getBoolean("animate_taskbar_indicator", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Launch background update check on app startup
    AppUpdater(isAutoUpdateEnabled = isAutoUpdateEnabled)

    val reorderableTabs = remember { mutableStateListOf(*loadNavOrder(prefs).toTypedArray()) }
    val defaultTab by remember { derivedStateOf { reorderableTabs.firstOrNull() ?: Tab.Scan } }
    var currentTab by rememberSaveable { mutableStateOf(loadNavOrder(prefs).firstOrNull() ?: Tab.Scan) }
    var isScannerFullScreen by remember { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val hideTaskbar = isScannerFullScreen && currentTab == Tab.Scan

    LaunchedEffect(navigateToUpdate) {
        if (navigateToUpdate) {
            currentTab = Tab.Menu
        }
    }

    BackHandler(enabled = currentTab != defaultTab && !hideTaskbar) {
        currentTab = defaultTab
    }

    val appBackground = MaterialTheme.colorScheme.background

    Scaffold(
        bottomBar = {
            if (useClassicTaskbar && !hideTaskbar) {
                ClassicBottomBar(
                    selectedTab = currentTab,
                    onTabSelect = { currentTab = it },
                    reorderableTabs = reorderableTabs,
                    hideLabels = hideLabels
                )
            }
        },
        containerColor = appBackground,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground)
                .padding(
                    top = if (hideTaskbar) 0.dp else paddingValues.calculateTopPadding(),
                    bottom = if (useClassicTaskbar && !hideTaskbar) paddingValues.calculateBottomPadding() else 0.dp
                )
        ) {
            saveableStateHolder.SaveableStateProvider(currentTab) {
                when (currentTab) {
                    Tab.Scan -> ScanScreen(
                        onScannerActiveChange = { active -> isScannerFullScreen = active }
                    )
                    Tab.Library -> LibraryScreen()
                    Tab.Advance -> AdvanceScreen()
                    Tab.Menu -> MenuScreen(
                        navigateToUpdate = navigateToUpdate,
                        triggerUpdate = triggerUpdate,
                        onNavigateToUpdateConsumed = onNavigateToUpdateConsumed
                    )
                }
            }

            if (!useClassicTaskbar && !hideTaskbar) {
                FloatingBottomBar(
                    selectedTab = currentTab,
                    onTabSelect = { currentTab = it },
                    reorderableTabs = reorderableTabs,
                    hideLabels = hideLabels,
                    animateIndicator = animateIndicator,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
