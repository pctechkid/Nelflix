package com.nuvio.app.features.player

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nuvio.app.R
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

private const val TAG = "NuvioMpvPlayer"

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    useNativeController: Boolean,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val sanitizedSourceHeaders = sanitizePlaybackHeaders(sourceHeaders)
    val sanitizedSourceResponseHeaders = sanitizePlaybackResponseHeaders(sourceResponseHeaders)
    val playerSourceKey = remember(
        sourceUrl,
        sourceAudioUrl,
        sanitizedSourceHeaders,
        sanitizedSourceResponseHeaders,
        useYoutubeChunkedPlayback,
    ) {
        MpvSource(
            url = sourceUrl,
            audioUrl = sourceAudioUrl,
            headers = sanitizedSourceHeaders,
            responseHeaders = sanitizedSourceResponseHeaders,
            useYoutubeChunkedPlayback = useYoutubeChunkedPlayback,
        )
    }
    var playerView by remember(playerSourceKey) { mutableStateOf<NuvioMpvView?>(null) }

    DisposableEffect(playerSourceKey) {
        val observer = NuvioMpvObserver(
            onSnapshot = { latestOnSnapshot.value(it) },
            onError = { latestOnError.value(it) },
        )
        MPVLib.addObserver(observer)
        onDispose {
            playerView?.isReleasing = true
            MPVLib.removeObserver(observer)
            runCatching { MPVLib.command("stop") }
            runCatching { MPVLib.destroy() }
        }
    }

    DisposableEffect(playerSourceKey, lifecycleOwner, playWhenReady) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (playWhenReady) MPVLib.setPropertyBoolean("pause", false)
                Lifecycle.Event.ON_STOP -> MPVLib.setPropertyBoolean("pause", true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playerSourceKey, playWhenReady) {
        MPVLib.setPropertyBoolean("pause", !playWhenReady)
        latestOnSnapshot.value(MPVLib.snapshot())
    }

    LaunchedEffect(playerSourceKey) {
        onControllerReady(MpvPlayerEngineController(context))
    }

    LaunchedEffect(playerSourceKey) {
        while (isActive) {
            latestOnSnapshot.value(MPVLib.snapshot())
            delay(250L)
        }
    }

    key(playerSourceKey) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.nuvio_mpv_player_view, null) as NuvioMpvView).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    playerView = this
                    initializeForNuvio(playerSourceKey, playWhenReady)
                }
            },
            update = { view ->
                playerView = view
                view.applyResizeMode(resizeMode)
            },
        )
    }
}

internal data class MpvSource(
    val url: String,
    val audioUrl: String?,
    val headers: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val useYoutubeChunkedPlayback: Boolean,
)

class NuvioMpvView(
    context: Context,
    attrs: AttributeSet,
) : BaseMPVView(context, attrs) {
    var isReleasing: Boolean = false

    internal fun initializeForNuvio(source: MpvSource, playWhenReady: Boolean) {
        NuvioMpvFiles.prepare(context)
        initialize(context.filesDir.path, context.cacheDir.path)
        applyHeaders(source.headers)
        applyResponseHeaderOverrides(source.responseHeaders)
        applyYoutubeChunkedCompat(source.useYoutubeChunkedPlayback)
        playFile(source.url.toPlayablePath(context))
        applyRegularSubtitleOverride()
        source.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
            MPVLib.command("audio-add", audioUrl.toPlayablePath(context), "auto")
        }
        MPVLib.setPropertyBoolean("pause", !playWhenReady)
    }

    override fun initOptions() {
        PlayerSettingsRepository.ensureLoaded()
        val playerSettings = PlayerSettingsRepository.uiState.value
        setVo("gpu-next")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", if (playerSettings.mpvHardwareDecodingEnabled) "auto" else "no")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("input-default-bindings", "yes")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
        val demuxerBytes = playerSettings.mpvDemuxerMaxBytesMiB.coerceIn(128, 4096) * 1024L * 1024L
        MPVLib.setOptionString("demuxer-max-bytes", "$demuxerBytes")
        MPVLib.setOptionString("demuxer-max-back-bytes", "$demuxerBytes")
        MPVLib.setOptionString("sub-fonts-dir", "${context.cacheDir.path}/fonts/")
        MPVLib.setOptionString("sub-ass-override", "force")
        MPVLib.setOptionString("sub-ass-justify", "yes")
        MPVLib.setOptionString("sub-font", "Helvetica")
        MPVLib.setOptionString("sub-ass-force-style", RegularSubtitleAssForceStyle)
        MPVLib.setOptionString("sub-bold", "no")
        MPVLib.setOptionString("sub-italic", "no")
    }

    override fun observeProperties() {
        observedProperties.forEach { (name, format) ->
            MPVLib.observeProperty(name, format)
        }
    }

    override fun postInitOptions() = Unit

    fun applyResizeMode(resizeMode: PlayerResizeMode) {
        when (resizeMode) {
            PlayerResizeMode.Fit -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
            PlayerResizeMode.Fill -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyString("video-aspect-override", "no")
            }
            PlayerResizeMode.Zoom -> {
                MPVLib.setPropertyDouble("panscan", 1.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
        }
    }

    companion object {
        private val observedProperties = mapOf(
            "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
            "eof-reached" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
            "duration" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
            "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
            "demuxer-cache-time" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
            "speed" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
            "track-list" to MPVLib.mpvFormat.MPV_FORMAT_NODE,
            "chapter-list" to MPVLib.mpvFormat.MPV_FORMAT_NODE,
            "chapter" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        )
    }
}

private class NuvioMpvObserver(
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String?) -> Unit,
) : MPVLib.EventObserver {
    override fun eventProperty(property: String) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun eventProperty(property: String, value: Long) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun eventProperty(property: String, value: Boolean) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun eventProperty(property: String, value: String) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun eventProperty(property: String, value: Double) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun eventProperty(property: String, value: MPVNode) {
        onSnapshot(MPVLib.snapshot())
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                applyRegularSubtitleOverride()
                onError(null)
            }
            MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN -> Unit
            MPVLib.mpvEventId.MPV_EVENT_END_FILE -> onSnapshot(MPVLib.snapshot())
            else -> Unit
        }
    }
}

private class MpvPlayerEngineController(
    private val context: Context,
) : PlayerEngineController {
    override fun play() {
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun seekTo(positionMs: Long) {
        MPVLib.command("seek", (positionMs / 1000.0).toString(), "absolute+exact")
    }

    override fun seekBy(offsetMs: Long) {
        MPVLib.command("seek", (offsetMs / 1000.0).toString(), "relative+exact")
    }

    override fun retry() {
        MPVLib.command("playlist-play-index", "current")
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun setPlaybackSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble().coerceIn(0.25, 4.0))
    }

    override fun getAudioTracks(): List<AudioTrack> =
        mpvTracks()
            .filter { it.isAudio }
            .mapIndexed { index, track ->
                AudioTrack(
                    index = index,
                    id = track.id.toString(),
                    label = track.displayLabel(index),
                    language = track.lang,
                    isSelected = track.selected == true,
                )
            }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        mpvTracks()
            .filter { it.isSubtitle }
            .mapIndexed { index, track ->
                SubtitleTrack(
                    index = index,
                    id = track.id.toString(),
                    label = track.displayLabel(index),
                    language = track.lang,
                    isSelected = track.selected == true,
                    isForced = track.forced == true || track.title?.contains("forced", ignoreCase = true) == true,
                )
            }

    override fun getChapters(): List<PlayerChapter> =
        mpvChapters().mapIndexed { index, chapter ->
            PlayerChapter(
                index = index,
                title = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                timeMs = (chapter.time * 1000.0).toLong().coerceAtLeast(0L),
            )
        }

    override fun selectAudioTrack(index: Int) {
        val track = mpvTracks().filter { it.isAudio }.getOrNull(index) ?: return
        MPVLib.setPropertyInt("aid", track.id)
    }

    override fun selectSubtitleTrack(index: Int) {
        if (index < 0) {
            MPVLib.setPropertyString("sid", "no")
            return
        }
        val track = mpvTracks().filter { it.isSubtitle }.getOrNull(index) ?: return
        MPVLib.setPropertyInt("sid", track.id)
        applyRegularSubtitleOverride()
    }

    override fun selectChapter(index: Int) {
        MPVLib.setPropertyInt("chapter", index.coerceAtLeast(0))
    }

    override fun setSubtitleUri(url: String) {
        MPVLib.command("sub-add", url.toPlayablePath(context), "select")
        applyRegularSubtitleOverride()
    }

    override fun clearExternalSubtitle() {
        MPVLib.command("sub-remove")
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    override fun getSubtitleDelayMs(): Long =
        ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000.0).toLong()

    override fun setSubtitleDelayMs(delayMs: Long) {
        MPVLib.setPropertyDouble("sub-delay", delayMs.toDouble() / 1000.0)
    }

    override fun applySubtitleStyle(style: SubtitleStyleState) {
        MPVLib.setPropertyString("sub-color", style.textColor.toMpvColor())
        MPVLib.setPropertyInt("sub-font-size", style.fontSizeSp)
        MPVLib.setPropertyString("sub-border-style", if (style.outlineEnabled) "outline-and-shadow" else "none")
        MPVLib.setPropertyInt("sub-pos", (1000 - style.bottomOffset).coerceIn(0, 1000) / 10)
        applyRegularSubtitleOverride()
    }

    override fun toggleSilenceSkip() {
        MPVLib.command("script-message", "toggle")
    }
}

private fun applyRegularSubtitleOverride() {
    runCatching {
        MPVLib.setPropertyString("sub-ass-override", "force")
        MPVLib.setPropertyString("sub-ass-force-style", RegularSubtitleAssForceStyle)
        MPVLib.setPropertyString("sub-bold", "no")
        MPVLib.setPropertyString("sub-italic", "no")
    }
}

private const val RegularSubtitleAssForceStyle = "Bold=0,Italic=0"

private fun String.withPersistentRegularSubtitleOverrides(): String {
    val trimmed = trimEnd()
    val block = """
        sub-ass-override=force
        sub-ass-force-style=${persistentRegularSubtitleForceStyle()}
        sub-bold=no
        sub-italic=no
    """.trimIndent()
    return if (trimmed.isBlank()) {
        block + "\n"
    } else {
        trimmed + "\n" + block + "\n"
    }
}

private fun String.persistentRegularSubtitleForceStyle(): String {
    val existingForceStyle = lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("#") }
        .lastOrNull { it.startsWith("sub-ass-force-style", ignoreCase = true) }
        ?.substringAfter("=", "")
        ?.trim()
        .orEmpty()

    val preserved = existingForceStyle
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { style ->
            style.startsWith("Bold=", ignoreCase = true) ||
                style.startsWith("Italic=", ignoreCase = true)
        }

    return (preserved + listOf("Bold=0", "Italic=0")).joinToString(",")
}

private object NuvioMpvFiles {
    fun prepare(context: Context) {
        PlayerSettingsRepository.ensureLoaded()
        val playerSettings = PlayerSettingsRepository.uiState.value
        runCatching { Utils.copyAssets(context) }
            .onFailure { Log.w(TAG, "Unable to copy mpv assets", it) }
        val scriptsDir = context.filesDir.resolve("scripts").apply { mkdirs() }
        context.cacheDir.resolve("fonts").mkdirs()
        context.filesDir.resolve("shaders").mkdirs()
        mirrorExternalMpvConfig(context, playerSettings)
        mirrorFonts(context, playerSettings.mpvFontsDirectoryUri)
        copyBundledDefaultFont(context)
        mirrorDefaultPublicMpvFolder(context)
        copyBundledSilenceSkipScript(context, scriptsDir)
    }

    private fun mirrorExternalMpvConfig(context: Context, playerSettings: PlayerSettingsUiState) {
        val configTree = playerSettings.mpvConfigDirectoryUri
            ?.let { uri -> runCatching { DocumentFile.fromTreeUri(context, uri.toUri()) }.getOrNull() }

        val externalMpvConf = configTree?.findFile("mpv.conf")?.readText(context)
        val externalInputConf = configTree?.findFile("input.conf")?.readText(context)
        val mpvConf = when {
            playerSettings.mpvConf.isBlank() -> externalMpvConf?.takeIf { it.isNotBlank() } ?: DefaultMpvConf
            playerSettings.mpvConf == DefaultMpvConf && externalMpvConf != null -> externalMpvConf
            else -> playerSettings.mpvConf
        }.withPersistentRegularSubtitleOverrides()
        val inputConf = playerSettings.mpvInputConf.ifBlank { externalInputConf.orEmpty() }

        context.filesDir.resolve("mpv.conf").writeText(mpvConf)
        context.filesDir.resolve("input.conf").writeText(inputConf)

        if (playerSettings.mpvConf.isBlank() && externalMpvConf.isNullOrBlank()) {
            PlayerSettingsRepository.setMpvConf(DefaultMpvConf)
        } else if ((playerSettings.mpvConf.isBlank() || playerSettings.mpvConf == DefaultMpvConf) && externalMpvConf != null) {
            PlayerSettingsRepository.setMpvConf(mpvConf)
        } else if (configTree != null) {
            configTree.writeText(context, "mpv.conf", mpvConf)
        }
        if (playerSettings.mpvInputConf.isBlank() && externalInputConf != null) {
            PlayerSettingsRepository.setMpvInputConf(externalInputConf)
        } else if (configTree != null) {
            configTree.writeText(context, "input.conf", inputConf)
        }

        configTree?.findFile("scripts")?.takeIf { it.isDirectory }?.let { externalScripts ->
            val scriptsDir = context.filesDir.resolve("scripts").apply { mkdirs() }
            externalScripts.copyContentTo(context, scriptsDir)
        }
        configTree?.findFile("shaders")?.takeIf { it.isDirectory }?.let { externalShaders ->
            val shadersDir = context.filesDir.resolve("shaders").apply { mkdirs() }
            externalShaders.copyContentTo(context, shadersDir)
        }
    }

    private fun mirrorFonts(context: Context, fontsDirectoryUri: String?) {
        val fontsDir = context.cacheDir.resolve("fonts").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = fontsDirectoryUri
            ?.let { uri -> runCatching { DocumentFile.fromTreeUri(context, uri.toUri()) }.getOrNull() }
            ?: return
        source.copyContentTo(context, fontsDir)
    }

    private fun copyBundledDefaultFont(context: Context) {
        val fontsDir = context.cacheDir.resolve("fonts").apply { mkdirs() }
        runCatching {
            context.assets.open("Helvetica.ttf").use { input ->
                fontsDir.resolve("Helvetica.ttf").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.recoverCatching {
            context.assets.open("subfont.ttf").use { input ->
                fontsDir.resolve("Helvetica.ttf").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { Log.w(TAG, "Unable to install bundled Helvetica fallback font", it) }
    }

    private fun mirrorDefaultPublicMpvFolder(context: Context) {
        val publicMpvDir = Environment.getExternalStorageDirectory()
            ?.resolve("mpv")
            ?: return
        runCatching {
            publicMpvDir.mkdirs()
            val publicConf = publicMpvDir.resolve("mpv.conf")
            if (!publicConf.exists()) {
                publicConf.writeText(DefaultMpvConf)
            }
            val publicFontsDir = publicMpvDir.resolve("fonts").apply { mkdirs() }
            val publicHelvetica = publicFontsDir.resolve("Helvetica.ttf")
            if (!publicHelvetica.exists()) {
                context.assets.open("Helvetica.ttf").use { input ->
                    publicHelvetica.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Unable to create public /mpv default configuration", it) }
    }

    private fun copyBundledSilenceSkipScript(context: Context, scriptsDir: File) {
        runCatching {
            context.assets.open("silence_skip.lua").use { input ->
                scriptsDir.resolve("silence_skip.lua").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { Log.w(TAG, "Unable to install silence_skip.lua", it) }
    }

    private fun DocumentFile.copyContentTo(context: Context, destination: File) {
        destination.mkdirs()
        listFiles().forEach { child ->
            if (child.name.isNullOrBlank()) return@forEach
            val target = destination.resolve(child.name!!)
            if (child.isDirectory) {
                child.copyContentTo(context, target)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun DocumentFile.readText(context: Context): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

    private fun DocumentFile.writeText(context: Context, name: String, text: String) {
        val file = findFile(name) ?: createFile("text/plain", name)?.also { it.renameTo(name) }
        if (file != null) {
            runCatching {
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                    output.write(text.toByteArray())
                }
            }.onFailure { Log.w(TAG, "Unable to write $name to mpv config directory", it) }
        }
    }
}

private fun MPVLib.snapshot(): PlayerPlaybackSnapshot {
    val durationMs = ((getPropertyDouble("duration") ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
    val positionMs = ((getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
    val cachePositionMs = ((getPropertyDouble("demuxer-cache-time") ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
    val paused = getPropertyBoolean("pause") ?: true
    val eofReached = getPropertyBoolean("eof-reached") ?: false
    val speed = (getPropertyDouble("speed") ?: 1.0).toFloat()
    return PlayerPlaybackSnapshot(
        isLoading = durationMs <= 0L && !eofReached,
        isPlaying = !paused && !eofReached,
        isEnded = eofReached,
        durationMs = durationMs,
        positionMs = positionMs,
        bufferedPositionMs = maxOf(positionMs, cachePositionMs),
        playbackSpeed = speed,
    )
}

private fun applyHeaders(headers: Map<String, String>) {
    val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    if (!userAgent.isNullOrBlank()) {
        MPVLib.setPropertyString("user-agent", userAgent)
    }
    val headerFields = headers
        .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        .map { (key, value) -> "${key.trim()}: ${value.trim().replace(",", "\\,")}" }
        .joinToString(",")
    if (headerFields.isNotBlank()) {
        MPVLib.setPropertyString("http-header-fields", headerFields)
    }
}

private fun applyResponseHeaderOverrides(headers: Map<String, String>) {
    if (headers.isNotEmpty()) {
        Log.d(TAG, "Ignoring response header overrides for mpv backend: ${headers.keys.joinToString()}")
    }
}

private fun applyYoutubeChunkedCompat(enabled: Boolean) {
    if (!enabled) return
    MPVLib.setPropertyString("demuxer-lavf-o", "http_seekable=0")
}

private fun String.toPlayablePath(context: Context): String {
    val trimmed = trim()
    if (trimmed.startsWith("file://", ignoreCase = true)) {
        return runCatching { File(URI(trimmed)).absolutePath }.getOrDefault(trimmed)
    }
    if (!trimmed.startsWith("content://", ignoreCase = true)) return trimmed
    return trimmed.toUri().openContentFd(context) ?: trimmed
}

private fun Uri.openContentFd(context: Context): String? =
    context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let { fd ->
        Utils.findRealPath(fd)?.also { ParcelFileDescriptor.adoptFd(fd).close() } ?: "fd://$fd"
    }

private val json = Json {
    ignoreUnknownKeys = true
}

private fun mpvTracks(): List<MpvTrackNode> =
    runCatching {
        MPVLib.getPropertyNode("track-list")?.toJson()?.let {
            json.decodeFromString<List<MpvTrackNode>>(it)
        }.orEmpty()
    }.getOrElse { error ->
        Log.w(TAG, "Unable to read mpv track list", error)
        emptyList()
    }

private fun mpvChapters(): List<MpvChapterNode> =
    runCatching {
        MPVLib.getPropertyNode("chapter-list")?.toJson()?.let {
            json.decodeFromString<List<MpvChapterNode>>(it)
        }.orEmpty()
    }.getOrElse { error ->
        Log.w(TAG, "Unable to read mpv chapter list", error)
        emptyList()
    }

@Serializable
private data class MpvChapterNode(
    val title: String? = null,
    val time: Double = 0.0,
)

@Serializable
private data class MpvTrackNode(
    val id: Int,
    val type: String,
    val title: String? = null,
    val lang: String? = null,
    val selected: Boolean? = null,
    val forced: Boolean? = null,
    val codec: String? = null,
    @SerialName("codec-desc") val codecDescription: String? = null,
) {
    val isAudio: Boolean get() = type == "audio"
    val isSubtitle: Boolean get() = type == "sub"

    fun displayLabel(index: Int): String =
        listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            lang?.takeIf { it.isNotBlank() },
            codecDescription?.takeIf { it.isNotBlank() } ?: codec?.takeIf { it.isNotBlank() },
        ).joinToString(" - ").ifBlank { "Track ${index + 1}" }
}

private fun androidx.compose.ui.graphics.Color.toMpvColor(): String {
    val red = (red * 255).toInt().coerceIn(0, 255)
    val green = (green * 255).toInt().coerceIn(0, 255)
    val blue = (blue * 255).toInt().coerceIn(0, 255)
    val alpha = (alpha * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X%02X".format(red, green, blue, alpha)
}
