package com.tkno.scan

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tkno.scan.theme.ScanTheme
import com.tkno.scan.ui.common.LocalDarkTheme
import com.tkno.scan.ui.main.MainScreen
import com.tkno.scan.ui.onboarding.OnboardingScreen
import com.tkno.scan.util.DarkThemePreference
import com.tkno.scan.util.LanguageManager
import com.tkno.scan.util.PreferenceUtil
import com.tkno.scan.util.UpdateNotificationHelper

class MainActivity : ComponentActivity() {
  private var navigateToUpdateState = mutableStateOf(false)
  private var triggerUpdateState = mutableStateOf(false)

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val navigateTo = intent.getStringExtra(UpdateNotificationHelper.EXTRA_NAVIGATE_TO)
    val trigger = intent.getBooleanExtra(UpdateNotificationHelper.EXTRA_TRIGGER_UPDATE, false)
    if (navigateTo == UpdateNotificationHelper.NAV_TARGET_AUTO_UPDATE) {
      navigateToUpdateState.value = true
      triggerUpdateState.value = trigger
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PreferenceUtil.init(this)
    UpdateNotificationHelper.createNotificationChannel(this)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val prefs = remember { PreferenceUtil.getPrefs(context) }

      val hasCompletedOnboarding = remember { prefs.getBoolean("onboarding_completed", false) }
      var showOnboarding by rememberSaveable { mutableStateOf(!hasCompletedOnboarding) }

      var darkThemePref by remember { mutableIntStateOf(prefs.getInt("dark_theme", DarkThemePreference.FOLLOW_SYSTEM)) }
      var isHighContrastPref by remember { mutableStateOf(prefs.getBoolean("high_contrast_dark_theme", false)) }
      var dynamicColorPref by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }
      var appLanguagePref by remember { mutableStateOf(prefs.getString("app_language", "system") ?: "system") }

      DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
          if (key == "dark_theme") {
            darkThemePref = p.getInt("dark_theme", DarkThemePreference.FOLLOW_SYSTEM)
          } else if (key == "high_contrast_dark_theme") {
            isHighContrastPref = p.getBoolean("high_contrast_dark_theme", false)
          } else if (key == "dynamic_color") {
            dynamicColorPref = p.getBoolean("dynamic_color", true)
          } else if (key == "app_language") {
            appLanguagePref = p.getString("app_language", "system") ?: "system"
          }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
          prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
      }

      val localeContext = remember(appLanguagePref, context) {
        LanguageManager.wrapContext(context, appLanguagePref)
      }

      val effectiveTag = remember(appLanguagePref, context) {
        LanguageManager.resolveEffectiveLanguageTag(context, appLanguagePref)
      }

      val isRtl = remember(effectiveTag, context) {
        LanguageManager.isRtl(context, effectiveTag)
      }

      val isArabic = remember(effectiveTag) {
        effectiveTag.equals("ar", ignoreCase = true)
      }

      val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

      val darkThemePreference = remember(darkThemePref, isHighContrastPref) {
        DarkThemePreference(darkThemePref, isHighContrastPref)
      }

      val isDark = darkThemePreference.isDarkTheme()

      androidx.compose.runtime.LaunchedEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle = if (isDark) {
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
          },
          navigationBarStyle = if (isDark) {
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
          }
        )
      }

      CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalLayoutDirection provides layoutDirection,
        LocalDarkTheme provides darkThemePreference
      ) {
        ScanTheme(
          darkTheme = isDark,
          isHighContrast = isHighContrastPref,
          dynamicColor = dynamicColorPref,
          isArabic = isArabic
        ) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showOnboarding) {
              OnboardingScreen(
                onFinished = {
                  prefs.edit().putBoolean("onboarding_completed", true).apply()
                  showOnboarding = false
                }
              )
            } else {
              MainScreen(
                navigateToUpdate = navigateToUpdateState.value,
                triggerUpdate = triggerUpdateState.value,
                onNavigateToUpdateConsumed = {
                  navigateToUpdateState.value = false
                  triggerUpdateState.value = false
                }
              )
            }
          }
        }
      }
    }
  }
}
