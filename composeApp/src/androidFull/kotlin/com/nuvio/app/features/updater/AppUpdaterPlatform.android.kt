package com.nuvio.app.features.updater

actual object AppUpdaterPlatform {
    actual val isSupported: Boolean = true

    actual fun getSupportedAbis(): List<String> = AndroidAppUpdaterPlatform.getSupportedAbis()

    actual fun getInstalledAppInfo(): InstalledAppInfo? = AndroidAppUpdaterPlatform.getInstalledAppInfo()

    actual fun getIgnoredTag(): String? = AndroidAppUpdaterPlatform.getIgnoredTag()

    actual fun setIgnoredTag(tag: String?) {
        AndroidAppUpdaterPlatform.setIgnoredTag(tag)
    }

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        releaseTag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = AndroidAppUpdaterPlatform.downloadApk(assetUrl, assetName, releaseTag, onProgress)

    actual fun canRequestPackageInstalls(): Boolean = AndroidAppUpdaterPlatform.canRequestPackageInstalls()

    actual fun openUnknownSourcesSettings() {
        AndroidAppUpdaterPlatform.openUnknownSourcesSettings()
    }

    actual fun installDownloadedApk(path: String, expectedVersionName: String): Result<Unit> =
        AndroidAppUpdaterPlatform.installDownloadedApk(path, expectedVersionName)
}
