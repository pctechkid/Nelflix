package com.nuvio.app.features.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.nuvio.app.core.ui.NuvioToastController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
internal actual fun PlatformDebugLogExportRows(isTablet: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = "Export debug log",
        description = "Share recent app and crash logs for troubleshooting.",
        icon = Icons.Rounded.CloudDownload,
        isTablet = isTablet,
        onClick = {
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching { exportDebugLog(context) }
                }
                result
                    .onSuccess { file -> shareDebugLog(context, file) }
                    .onFailure { error ->
                        NuvioToastController.show("Unable to export debug log: ${error.message.orEmpty()}")
                    }
            }
        },
    )
}

private fun exportDebugLog(context: Context): File {
    val directory = File(context.cacheDir, "debug_logs").apply { mkdirs() }
    directory.listFiles()
        ?.filter { it.name.startsWith("nelflix-debug-") && it.extension == "txt" }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(5)
        ?.forEach { it.delete() }

    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(directory, "nelflix-debug-$timestamp.txt")
    val logcat = readLogcat()

    file.writeText(
        buildString {
            appendLine("Nelflix debug log")
            appendLine("Generated: ${Date()}")
            appendLine("Package: ${context.packageName}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(logcat)
        },
    )
    return file
}

private fun readLogcat(): String {
    val command = listOf("logcat", "-d", "-v", "threadtime", "-t", "5000")
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val completed = process.waitFor(8, TimeUnit.SECONDS)
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!completed) {
        process.destroy()
    }
    return output.ifBlank {
        "No logcat output was available to this app. Android may be restricting log access on this device.\n"
    }
}

private fun shareDebugLog(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Nelflix debug log")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share debug log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
