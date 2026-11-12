package com.tkno.scan.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tkno.scan.util.PreferenceUtil
import com.tkno.scan.util.UpdateNotificationHelper
import com.tkno.scan.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background composable that checks for updates on launch (at most once every 48 hours)
 * and automatically downloads if auto-update is enabled, or sends a notification if the bell is enabled.
 */
@Composable
fun AppUpdater(isAutoUpdateEnabled: Boolean) {
    val context = LocalContext.current

    val prefs = remember { PreferenceUtil.getPrefs(context) }
    val isBellEnabled = prefs.getBoolean("update_bell_enabled", true)
    val updateChannel = prefs.getInt("update_channel", 0) // 0: Stable, 1: Beta
    val includePrerelease = updateChannel == 1

    LaunchedEffect(isAutoUpdateEnabled, isBellEnabled, updateChannel) {
        if (!isAutoUpdateEnabled && !isBellEnabled) return@LaunchedEffect

        val lastCheckTime = prefs.getLong("app_last_update_check_time", 0L)
        val currentTime = System.currentTimeMillis()
        val twoDaysMillis = 2 * 86_400_000L // 48 hours

        if (currentTime < lastCheckTime + twoDaysMillis) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val foundRelease = UpdateUtil.checkForUpdate(context, includePrerelease = includePrerelease)
                prefs.edit().putLong("app_last_update_check_time", System.currentTimeMillis()).apply()

                if (foundRelease != null) {
                    val releaseTag = foundRelease.tagName ?: foundRelease.name ?: ""
                    val lastDownloadedTag = prefs.getString("last_downloaded_update_tag", null)

                    if (isAutoUpdateEnabled) {
                        if (releaseTag.isNotEmpty() && releaseTag == lastDownloadedTag) {
                            return@runCatching
                        }

                        UpdateNotificationHelper.createNotificationChannel(context)
                        var lastNotifiedPercent = -1

                        UpdateUtil.downloadApk(context, foundRelease).collect { status ->
                            when (status) {
                                is UpdateUtil.DownloadStatus.Progress -> {
                                    if (status.percent - lastNotifiedPercent >= 5 || status.percent == 0 || status.percent == 100) {
                                        lastNotifiedPercent = status.percent
                                        UpdateNotificationHelper.showDownloadProgressNotification(
                                            context,
                                            foundRelease,
                                            status.percent
                                        )
                                    }
                                }
                                is UpdateUtil.DownloadStatus.Finished -> {
                                    if (releaseTag.isNotEmpty()) {
                                        prefs.edit().putString("last_downloaded_update_tag", releaseTag).apply()
                                    }
                                    UpdateNotificationHelper.showDownloadCompletedNotification(
                                        context,
                                        foundRelease
                                    )
                                }
                                else -> {}
                            }
                        }
                    } else if (isBellEnabled) {
                        UpdateNotificationHelper.showUpdateAvailableNotification(context, foundRelease)
                    }
                }
            }.onFailure { it.printStackTrace() }
        }
    }
}
