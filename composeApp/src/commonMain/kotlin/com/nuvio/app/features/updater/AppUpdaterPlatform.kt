package com.nuvio.app.features.updater

expect object AppUpdaterPlatform {
    val isSupported: Boolean

    fun getSupportedAbis(): List<String>

    fun getInstalledAppInfo(): InstalledAppInfo?

    fun getIgnoredTag(): String?

    fun setIgnoredTag(tag: String?)

    suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        releaseTag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String>

    fun canRequestPackageInstalls(): Boolean

    fun openUnknownSourcesSettings()

    fun installDownloadedApk(path: String, expectedVersionName: String): Result<Unit>
}

data class InstalledAppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)
