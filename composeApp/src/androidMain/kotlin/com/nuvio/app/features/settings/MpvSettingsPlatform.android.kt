package com.nuvio.app.features.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.nuvio.app.features.player.PlayerSettingsRepository
import java.io.File

@Composable
internal actual fun PlatformMpvDirectoryRows(
    isTablet: Boolean,
    fontsDirectoryUri: String?,
    configDirectoryUri: String?,
) {
    val context = LocalContext.current
    val fontsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.persistDirectoryPermission(it)
            PlayerSettingsRepository.setMpvFontsDirectoryUri(it.toString())
        }
    }
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.persistDirectoryPermission(it)
            PlayerSettingsRepository.setMpvConfigDirectoryUri(it.toString())
        }
    }

    SettingsNavigationRow(
        title = "Subtitle fonts directory",
        description = platformMpvDirectoryLabel(fontsDirectoryUri),
        isTablet = isTablet,
        onClick = { fontsPicker.launch(fontsDirectoryUri?.toUri()) },
        trailingContent = {
            if (!fontsDirectoryUri.isNullOrBlank()) {
                IconButton(onClick = { PlayerSettingsRepository.setMpvFontsDirectoryUri(null) }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear subtitle fonts directory")
                }
            }
        },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = "mpv configuration storage location",
        description = platformMpvDirectoryLabel(configDirectoryUri),
        isTablet = isTablet,
        onClick = { configPicker.launch(configDirectoryUri?.toUri()) },
        trailingContent = {
            if (!configDirectoryUri.isNullOrBlank()) {
                IconButton(onClick = { PlayerSettingsRepository.setMpvConfigDirectoryUri(null) }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear mpv configuration location")
                }
            }
        },
    )
}

internal actual fun platformMpvDirectoryLabel(uri: String?): String {
    if (uri.isNullOrBlank()) return "Not set"
    return runCatching {
        val decoded = Uri.decode(uri)
        decoded.substringAfterLast(":").ifBlank { decoded.substringAfterLast("/") }
    }.getOrDefault(uri)
}

internal actual fun clearCachedMpvConfigurations() {
    NuvioSettingsApplicationContext.get()?.let { context ->
        context.filesDir.resolve("mpv.conf").writeText("")
        context.filesDir.resolve("input.conf").writeText("")
        context.filesDir.resolve("scripts").deleteRecursively()
        context.filesDir.resolve("scripts").mkdirs()
        context.filesDir.resolve("shaders").deleteRecursively()
        context.filesDir.resolve("shaders").mkdirs()
        PlayerSettingsRepository.setMpvConf("")
        PlayerSettingsRepository.setMpvInputConf("")
    }
}

internal actual fun clearCachedMpvFonts() {
    NuvioSettingsApplicationContext.get()?.let { context ->
        context.cacheDir.resolve("fonts").deleteRecursively()
        context.cacheDir.resolve("fonts").mkdirs()
        context.filesDir.resolve("fonts").deleteRecursively()
        context.filesDir.resolve("fonts").mkdirs()
    }
}

internal actual fun readExternalMpvConfigFile(configDirectoryUri: String?, fileName: String): String? {
    val context = NuvioSettingsApplicationContext.get() ?: return null
    if (configDirectoryUri.isNullOrBlank()) return null
    return runCatching {
        DocumentFile.fromTreeUri(context, configDirectoryUri.toUri())
            ?.findFile(fileName)
            ?.takeIf { it.isFile }
            ?.uri
            ?.let { uri ->
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
    }.getOrNull()
}

internal actual fun writeExternalMpvConfigFile(configDirectoryUri: String?, fileName: String, text: String): Boolean {
    val context = NuvioSettingsApplicationContext.get() ?: return false
    if (configDirectoryUri.isNullOrBlank()) return false
    return runCatching {
        val tree = DocumentFile.fromTreeUri(context, configDirectoryUri.toUri()) ?: return false
        val file = tree.findFile(fileName) ?: tree.createFile("text/plain", fileName)?.also { it.renameTo(fileName) }
        if (file == null) {
            false
        } else {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                output.write(text.toByteArray())
            }
            true
        }
    }.getOrDefault(false)
}

private fun Context.persistDirectoryPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

internal object NuvioSettingsApplicationContext {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context? = context
}
