package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformMpvDirectoryRows(
    isTablet: Boolean,
    fontsDirectoryUri: String?,
    configDirectoryUri: String?,
)

internal expect fun platformMpvDirectoryLabel(uri: String?): String

internal expect fun clearCachedMpvConfigurations()

internal expect fun restoreDefaultMpvConfigurations()

internal expect fun clearCachedMpvFonts()

internal expect fun readExternalMpvConfigFile(configDirectoryUri: String?, fileName: String): String?

internal expect fun writeExternalMpvConfigFile(configDirectoryUri: String?, fileName: String, text: String): Boolean
