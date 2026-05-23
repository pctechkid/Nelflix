package com.nuvio.app.features.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AndroidAppUpdaterPlatform {
    private const val TAG = "AndroidAppUpdater"
    private const val preferencesName = "nelflix_updater"
    private const val ignoredTagKey = "ignored_release_tag"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getSupportedAbis(): List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()

    fun getInstalledAppInfo(): InstalledAppInfo? {
        val context = appContext ?: return null
        return runCatching {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            InstalledAppInfo(
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
            )
        }.getOrNull()
    }

    fun getIgnoredTag(): String? =
        preferences().getString(ignoredTagKey, null)

    fun setIgnoredTag(tag: String?) {
        preferences().edit().apply {
            if (tag == null) remove(ignoredTagKey) else putString(ignoredTagKey, tag)
        }.apply()
    }

    suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        releaseTag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val context = requireContext()
            val safeName = assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val updatesDirectory = File(context.cacheDir, "updates")
            updatesDirectory.mkdirs()
            updatesDirectory.listFiles()
                ?.filter { it.isFile && (it.extension.equals("apk", ignoreCase = true) || it.name.endsWith(".part", ignoreCase = true)) }
                ?.forEach { it.delete() }
            val destination = File(updatesDirectory, "nelflix-${releaseTag.replace(Regex("[^a-zA-Z0-9._-]"), "_")}-$safeName")
            if (destination.exists()) {
                destination.delete()
            }
            val partial = File(updatesDirectory, "${destination.name}.part")
            if (partial.exists()) {
                partial.delete()
            }

            val request = Request.Builder()
                .url(assetUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed with HTTP ${response.code}")
                }

                val body = response.body ?: error("Empty download body")
                val totalBytes = body.contentLength().takeIf { it > 0L }
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }
            }

            if (destination.exists()) {
                destination.delete()
            }
            if (!partial.renameTo(destination)) {
                error("Unable to finalize downloaded APK")
            }

            destination.absolutePath
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        val context = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: SecurityException) {
            
                true
            }
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installDownloadedApk(path: String, expectedVersionName: String): Result<Unit> = runCatching {
        val context = requireContext()
        val apkFile = File(path)
        check(apkFile.exists()) { "Downloaded update file is missing." }
        check(apkFile.extension.equals("apk", ignoreCase = true)) {
            apkFile.delete()
            "Update download failed: invalid APK file."
        }

        val archiveInfo = getArchiveInfo(context, apkFile)
            ?: run {
                apkFile.delete()
                error("Update download failed: invalid APK file.")
            }
        val installedInfo = getInstalledAppInfo()
            ?: run {
                apkFile.delete()
                error("Downloaded update does not match this app.")
            }

        val archivePackageName = archiveInfo.packageName
        val archiveVersionName = archiveInfo.versionName
        val archiveVersionCode = archiveInfo.versionCode

        Log.d(TAG, "Installed app=${installedInfo.packageName} ${installedInfo.versionName} (${installedInfo.versionCode})")
        Log.d(TAG, "Downloaded APK path=${apkFile.absolutePath}")
        Log.d(TAG, "APK package=$archivePackageName version=$archiveVersionName code=$archiveVersionCode expected=$expectedVersionName")

        check(archivePackageName == context.packageName) {
            apkFile.delete()
            "Downloaded update does not match this app."
        }
        check(archiveVersionName.isNotBlank()) {
            apkFile.delete()
            "Update download failed: invalid APK file."
        }
        check(archiveVersionName == expectedVersionName.removePrefix("v").removePrefix("V")) {
            apkFile.delete()
            "Downloaded update does not match the selected version."
        }
        check(archiveVersionCode > installedInfo.versionCode) {
            apkFile.delete()
            "Downloaded update is not newer than the installed version."
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private data class ApkMetadata(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
    )

    private fun getArchiveInfo(context: Context, apkFile: File): ApkMetadata? =
        runCatching {
            val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
            }
            packageInfo?.let {
                ApkMetadata(
                    packageName = it.packageName.orEmpty(),
                    versionName = it.versionName.orEmpty(),
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode.toLong()
                    },
                )
            }
        }.getOrNull()

    private fun preferences() = requireContext().getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun requireContext(): Context =
        requireNotNull(appContext) { "AndroidAppUpdaterPlatform.initialize must be called before use." }
}
