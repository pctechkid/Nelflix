package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformMpvDirectoryRows(
    isTablet: Boolean,
    fontsDirectoryUri: String?,
    configDirectoryUri: String?,
) = Unit

internal actual fun platformMpvDirectoryLabel(uri: String?): String =
    uri?.takeIf { it.isNotBlank() } ?: "Not set"

internal actual fun clearCachedMpvConfigurations() = Unit

internal actual fun restoreDefaultMpvConfigurations() = Unit

internal actual fun clearCachedMpvFonts() = Unit

internal actual fun readExternalMpvConfigFile(configDirectoryUri: String?, fileName: String): String? = null

internal actual fun writeExternalMpvConfigFile(configDirectoryUri: String?, fileName: String, text: String): Boolean = false
