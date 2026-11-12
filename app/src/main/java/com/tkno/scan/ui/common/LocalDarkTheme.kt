package com.tkno.scan.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.tkno.scan.util.DarkThemePreference

val LocalDarkTheme = compositionLocalOf { DarkThemePreference() }
