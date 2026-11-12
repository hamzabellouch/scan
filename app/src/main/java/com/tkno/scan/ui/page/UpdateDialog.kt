package com.tkno.scan.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tkno.scan.ui.component.ConfirmButton
import com.tkno.scan.ui.component.DismissButton
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkno.scan.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialogImpl(
    onDismissRequest: () -> Unit,
    title: String,
    onConfirmUpdate: () -> Unit,
    releaseNote: String,
    downloadStatus: UpdateUtil.DownloadStatus,
    onOpenInFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { com.tkno.scan.util.PreferenceUtil.getPrefs(context) }
    val autoInstall = prefs.getBoolean("auto_install_apk", false)
    val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else true
    val isAutoInstallActive = autoInstall && canInstall

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        icon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
        confirmButton = {
            when (downloadStatus) {
                is UpdateUtil.DownloadStatus.Progress -> {
                    // No confirm button while downloading
                }
                is UpdateUtil.DownloadStatus.Finished -> {
                    if (isAutoInstallActive) {
                        ConfirmButton(
                            text = stringResource(com.tkno.scan.R.string.done),
                            onClick = onDismissRequest
                        )
                    } else {
                        ConfirmButton(
                            text = stringResource(com.tkno.scan.R.string.open_in_files),
                            onClick = {
                                onOpenInFiles()
                                onDismissRequest()
                            }
                        )
                    }
                }
                else -> {
                    ConfirmButton(
                        text = stringResource(com.tkno.scan.R.string.update),
                        onClick = onConfirmUpdate
                    )
                }
            }
        },
        dismissButton = {
            DismissButton(
                text = when (downloadStatus) {
                    is UpdateUtil.DownloadStatus.Progress -> stringResource(com.tkno.scan.R.string.close)
                    else -> stringResource(com.tkno.scan.R.string.cancel)
                },
                onClick = onDismissRequest
            )
        },
        text = {
            Column {
                when (downloadStatus) {
                    is UpdateUtil.DownloadStatus.Progress -> {
                        Column {
                            LinearProgressIndicator(
                                progress = { downloadStatus.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                strokeCap = StrokeCap.Round,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(com.tkno.scan.R.string.downloading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${downloadStatus.percent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    is UpdateUtil.DownloadStatus.Finished -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(com.tkno.scan.R.string.downloaded_successfully),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            val customUri = prefs.getString("app_update_directory_uri", null)
                            val savedLocationText = if (!customUri.isNullOrBlank()) {
                                val dirName = remember(customUri) {
                                    runCatching {
                                        androidx.documentfile.provider.DocumentFile.fromTreeUri(
                                            context,
                                            android.net.Uri.parse(customUri)
                                        )?.name
                                    }.getOrNull()
                                }
                                stringResource(
                                    com.tkno.scan.R.string.apk_saved_in_dir,
                                    dirName ?: stringResource(com.tkno.scan.R.string.custom_folder)
                                )
                            } else {
                                stringResource(com.tkno.scan.R.string.apk_saved_to_downloads)
                            }
                            Text(
                                text = if (isAutoInstallActive) {
                                    stringResource(com.tkno.scan.R.string.installer_auto_open)
                                } else {
                                    savedLocationText
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        if (releaseNote.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = releaseNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun UpdateDialog(
    onDismissRequest: () -> Unit,
    release: UpdateUtil.Release,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentDownloadStatus by remember {
        mutableStateOf<UpdateUtil.DownloadStatus>(UpdateUtil.DownloadStatus.NotYet)
    }

    UpdateDialogImpl(
        onDismissRequest = onDismissRequest,
        title = release.name ?: "New Update",
        releaseNote = release.body ?: "",
        downloadStatus = currentDownloadStatus,
        onOpenInFiles = {
            UpdateUtil.openApkLocation(context, release)
        },
        onConfirmUpdate = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    UpdateUtil.downloadApk(context, release).collect { status ->
                        currentDownloadStatus = status
                        if (status is UpdateUtil.DownloadStatus.Finished) {
                            withContext(Dispatchers.Main) {
                                val prefs = com.tkno.scan.util.PreferenceUtil.getPrefs(context)
                                val autoInstall = prefs.getBoolean("auto_install_apk", false)
                                val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.packageManager.canRequestPackageInstalls()
                                } else true

                                if (autoInstall && canInstall) {
                                    UpdateUtil.installLatestApk(context)
                                } else {
                                    UpdateUtil.openApkLocation(context, release)
                                }
                            }
                        }
                    }
                }.onFailure {
                    it.printStackTrace()
                    currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(com.tkno.scan.R.string.app_update_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        },
    )
}
