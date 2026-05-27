package com.nuvio.app.features.downloads

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.download_failed
import nuvio.composeapp.generated.resources.downloads_error_empty_body
import nuvio.composeapp.generated.resources.downloads_error_http_failed
import nuvio.composeapp.generated.resources.downloads_error_not_initialized
import nuvio.composeapp.generated.resources.downloads_error_request_failed
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

private const val downloadsPreferencesName = "nelflix_download_settings"
private const val downloadFolderUriKey = "download_folder_uri"
private const val defaultRelativeDownloadPath = "Download/Nelflix/"

private val downloadHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .writeTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal actual object DownloadsPlatformDownloader {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        var call: Call? = null

        scope.launch {
            val context = appContext
            if (context == null) {
                onFailure(runBlocking { getString(Res.string.downloads_error_not_initialized) })
                return@launch
            }

            val target = createDownloadTarget(context, request.destinationFileName)
            var retryCount = 0

            while (true) {
            try {
                var resumeFromBytes = target.partialSize().coerceAtLeast(0L)

                fun buildRequest(rangeStart: Long?): Request {
                    val requestBuilder = Request.Builder().url(request.sourceUrl)
                    request.sourceHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }
                    if (rangeStart != null && rangeStart > 0L) {
                        requestBuilder.header("Range", "bytes=$rangeStart-")
                    }
                    return requestBuilder.get().build()
                }

                var attemptedRangeRequest = resumeFromBytes > 0L
                var httpRequest = buildRequest(if (attemptedRangeRequest) resumeFromBytes else null)
                call = downloadHttpClient.newCall(httpRequest)
                var response = call?.execute() ?: error(
                    runBlocking { getString(Res.string.downloads_error_request_failed) },
                )

                if (attemptedRangeRequest && response.code == 416) {
                    response.close()
                    target.deletePartial()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    httpRequest = buildRequest(null)
                    call = downloadHttpClient.newCall(httpRequest)
                    response = call?.execute() ?: error(
                        runBlocking { getString(Res.string.downloads_error_request_failed) },
                    )
                }

                response.use { activeResponse ->
                    if (!activeResponse.isSuccessful) {
                        error(
                            runBlocking {
                                getString(Res.string.downloads_error_http_failed, activeResponse.code)
                            },
                        )
                    }

                    val isPartialResume = attemptedRangeRequest && activeResponse.code == 206 && resumeFromBytes > 0L
                    val appendToTemp = isPartialResume
                    val startingBytes = if (appendToTemp) resumeFromBytes else 0L

                    if (!appendToTemp) {
                        target.deletePartial()
                    }

                    val body = activeResponse.body ?: error(
                        runBlocking { getString(Res.string.downloads_error_empty_body) },
                    )
                    val totalBytes = resolveTotalBytes(
                        startingBytes = startingBytes,
                        isPartialResume = isPartialResume,
                        contentRangeHeader = activeResponse.header("Content-Range"),
                        contentLength = body.contentLength().takeIf { it > 0L },
                    )
                    var downloadedBytes = startingBytes
                    onProgress(downloadedBytes, totalBytes)

                    body.byteStream().use { input ->
                        target.openPartialOutputStream(appendToTemp).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read.toLong()
                                onProgress(downloadedBytes, totalBytes)
                            }
                            output.flush()
                        }
                    }

                    val finalUri = target.commitPartial()
                    onSuccess(finalUri, totalBytes ?: target.finalSize().takeIf { it > 0L })
                    return@launch
                }
            } catch (error: Throwable) {
                if (job.isActive && error.isTransientDownloadFailure() && retryCount < 5) {
                    retryCount += 1
                    delay((retryCount * 2_000L).coerceAtMost(10_000L))
                    continue
                }
                onFailure(error.message ?: runBlocking { getString(Res.string.download_failed) })
                return@launch
            }
            }
        }

        job.invokeOnCompletion { call?.cancel() }

        return AndroidDownloadsTaskHandle(job)
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val context = appContext ?: return false
        if (localFileUri.startsWith("content://", ignoreCase = true)) {
            return runCatching { context.contentResolver.delete(localFileUri.toUri(), null, null) > 0 }
                .getOrDefault(false)
        }
        val file = localFileUri.toLocalFileOrNull() ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    actual fun removePartialFile(destinationFileName: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            createDownloadTarget(context, destinationFileName).deletePartial()
            true
        }.getOrDefault(false)
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        val context = appContext ?: return null
        localFileUri?.takeIf { it.isNotBlank() }?.let { uri ->
            if (uri.startsWith("content://", ignoreCase = true)) {
                if (context.contentResolver.openFileDescriptor(uri.toUri(), "r")?.use { it.statSize >= 0L } == true) {
                    return uri
                }
            } else {
                uri.toLocalFileOrNull()?.takeIf { it.exists() }?.let { return it.toURI().toString() }
            }
        }
        return createDownloadTarget(context, destinationFileName).resolveFinalUri()
    }

    actual fun getDownloadFolderUri(): String? =
        preferences()?.getString(downloadFolderUriKey, null)?.takeIf { it.isNotBlank() }

    actual fun setDownloadFolderUri(uri: String?) {
        preferences()?.edit()?.apply {
            val normalized = uri?.takeIf { it.isNotBlank() }
            if (normalized == null) remove(downloadFolderUriKey) else putString(downloadFolderUriKey, normalized)
        }?.apply()
    }

    actual fun downloadFolderLabel(uri: String?): String {
        if (uri.isNullOrBlank()) return "Android Downloads/Nelflix"
        return runCatching {
            val decoded = Uri.decode(uri)
            decoded.substringAfterLast(":").ifBlank { decoded.substringAfterLast("/") }
        }.getOrDefault(uri)
    }

    private fun preferences() =
        appContext?.getSharedPreferences(downloadsPreferencesName, Context.MODE_PRIVATE)
}

@Composable
internal actual fun PlatformDownloadFolderRow() {
    val context = LocalContext.current
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.persistDirectoryPermission(it)
            DownloadsRepository.setDownloadFolderUri(it.toString())
        }
    }

    SettingsSection(
        title = "Storage",
        isTablet = false,
    ) {
        SettingsGroup(isTablet = false) {
            SettingsNavigationRow(
                title = "Download Folder",
                description = DownloadsPlatformDownloader.downloadFolderLabel(uiState.downloadFolderUri),
                icon = Icons.Rounded.CloudDownload,
                isTablet = false,
                onClick = { picker.launch(uiState.downloadFolderUri?.toUri()) },
                trailingContent = {
                    if (!uiState.downloadFolderUri.isNullOrBlank()) {
                        IconButton(onClick = { DownloadsRepository.setDownloadFolderUri(null) }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Use default Downloads folder")
                        }
                    }
                },
            )
        }
    }
}

private interface AndroidDownloadTarget {
    fun partialSize(): Long
    fun openPartialOutputStream(append: Boolean): java.io.OutputStream
    fun deletePartial()
    fun commitPartial(): String
    fun finalSize(): Long
    fun resolveFinalUri(): String?
}

private fun createDownloadTarget(context: Context, fileName: String): AndroidDownloadTarget {
    val configuredTree = DownloadsPlatformDownloader.getDownloadFolderUri()
    if (!configuredTree.isNullOrBlank()) {
        DocumentFile.fromTreeUri(context, configuredTree.toUri())?.let { tree ->
            return SafDownloadTarget(context, tree, fileName)
        }
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStoreDownloadTarget(context, fileName)
    } else {
        FileDownloadTarget(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .resolve("Nelflix")
                .apply { mkdirs() },
            fileName,
        )
    }
}

private class SafDownloadTarget(
    private val context: Context,
    private val tree: DocumentFile,
    private val fileName: String,
) : AndroidDownloadTarget {
    private val partialName = "$fileName.part"
    private var activePartial: DocumentFile? = null

    override fun partialSize(): Long =
        (activePartial ?: tree.findFile(partialName))?.length()?.coerceAtLeast(0L) ?: 0L

    override fun openPartialOutputStream(append: Boolean): java.io.OutputStream {
        val partial = activePartial ?: tree.findFile(partialName) ?: tree.createFile("application/octet-stream", partialName)
            ?: error("Unable to create partial download file")
        activePartial = partial
        return context.contentResolver.openOutputStream(partial.uri, if (append) "wa" else "wt")
            ?: error("Unable to open partial download file")
    }

    override fun deletePartial() {
        (activePartial ?: tree.findFile(partialName))?.delete()
        activePartial = null
    }

    override fun commitPartial(): String {
        tree.findFile(fileName)?.delete()
        val partial = activePartial ?: tree.findFile(partialName) ?: error("Partial download file is missing")
        if (!partial.renameTo(fileName)) {
            val finalFile = tree.createFile("application/octet-stream", fileName)
                ?: error("Unable to create completed download file")
            context.contentResolver.openInputStream(partial.uri)?.use { input ->
                context.contentResolver.openOutputStream(finalFile.uri, "wt")?.use { output -> input.copyTo(output) }
                    ?: error("Unable to write completed download file")
            }
            partial.delete()
            activePartial = null
            return finalFile.uri.toString()
        }
        activePartial = null
        return tree.findFile(fileName)?.uri?.toString() ?: partial.uri.toString()
    }

    override fun finalSize(): Long = tree.findFile(fileName)?.length()?.coerceAtLeast(0L) ?: 0L

    override fun resolveFinalUri(): String? = tree.findFile(fileName)?.takeIf { it.isFile }?.uri?.toString()
}

@RequiresApi(Build.VERSION_CODES.Q)
private class MediaStoreDownloadTarget(
    private val context: Context,
    private val fileName: String,
) : AndroidDownloadTarget {
    private val partialName = "$fileName.part"
    private val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    private var activePartialUri: Uri? = null

    override fun partialSize(): Long =
        (activePartialUri ?: findByName(partialName))?.let(::sizeOf).takeIf { it != null && it >= 0L } ?: 0L

    override fun openPartialOutputStream(append: Boolean): java.io.OutputStream {
        val partialUri = activePartialUri ?: findByName(partialName) ?: createItem(partialName, pending = false)
        activePartialUri = partialUri
        return context.contentResolver.openOutputStream(partialUri, if (append) "wa" else "wt")
            ?: error("Unable to open partial download file")
    }

    override fun deletePartial() {
        (activePartialUri ?: findByName(partialName))?.let { context.contentResolver.delete(it, null, null) }
        activePartialUri = null
    }

    override fun commitPartial(): String {
        findByName(fileName)?.let { context.contentResolver.delete(it, null, null) }
        val partialUri = activePartialUri ?: findByName(partialName) ?: error("Partial download file is missing")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(partialUri, values, null, null)
        activePartialUri = null
        return findByName(fileName)?.toString() ?: partialUri.toString()
    }

    override fun finalSize(): Long = findByName(fileName)?.let(::sizeOf) ?: 0L

    override fun resolveFinalUri(): String? = findByName(fileName)?.toString()

    private fun createItem(name: String, pending: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, defaultRelativeDownloadPath)
            put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)
        }
        return context.contentResolver.insert(collection, values)
            ?: error("Unable to create download file")
    }

    private fun findByName(name: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, defaultRelativeDownloadPath)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        return null
    }

    private fun sizeOf(uri: Uri): Long =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
}

private class FileDownloadTarget(
    private val directory: File,
    private val fileName: String,
) : AndroidDownloadTarget {
    private val destination = directory.resolve(fileName)
    private val partial = directory.resolve("$fileName.part")

    override fun partialSize(): Long = partial.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

    override fun openPartialOutputStream(append: Boolean): java.io.OutputStream =
        FileOutputStream(partial, append)

    override fun deletePartial() {
        partial.delete()
    }

    override fun commitPartial(): String {
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        return destination.toURI().toString()
    }

    override fun finalSize(): Long = destination.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

    override fun resolveFinalUri(): String? = destination.takeIf { it.exists() }?.toURI()?.toString()
}

private class AndroidDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? =
    runCatching {
        if (startsWith("file:")) File(URI(this)) else File(this)
    }.getOrNull()

private fun Context.persistDirectoryPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    parseContentRangeTotal(contentRangeHeader)?.let { return it }
    val normalizedLength = contentLength?.takeIf { it > 0L } ?: return null
    return if (isPartialResume && startingBytes > 0L) {
        startingBytes + normalizedLength
    } else {
        normalizedLength
    }
}

private fun parseContentRangeTotal(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val slashIndex = value.lastIndexOf('/')
    if (slashIndex == -1 || slashIndex == value.lastIndex) return null
    val totalPart = value.substring(slashIndex + 1).trim()
    if (totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
}

private fun Throwable.isTransientDownloadFailure(): Boolean {
    val text = buildString {
        message?.let(::append)
        cause?.message?.let {
            append(' ')
            append(it)
        }
    }.lowercase()
    return text.contains("connection abort") ||
        text.contains("connection reset") ||
        text.contains("timeout") ||
        text.contains("unexpected end") ||
        text.contains("stream was reset")
}
