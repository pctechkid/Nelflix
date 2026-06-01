package com.nuvio.app.features.debrid

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamClientResolve
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException

private const val TorboxReusableFileIdxParam = "nuvio_file_idx"
private const val TorboxReusableFilenameParam = "nuvio_filename"
private const val TorboxCacheCheckChunkSize = 100

object TorboxP2PStreamFilter {
    suspend fun filterCachedOnly(streams: List<StreamItem>): List<StreamItem> {
        if (streams.isEmpty()) return streams
        val apiKey = DebridSettingsRepository.snapshot().torboxApiKey.trim()
        val regularStreams = streams.filterNot { it.isPlainP2PStream() }
        val p2pStreams = streams.filter { it.isPlainP2PStream() }
        if (apiKey.isBlank() || p2pStreams.isEmpty()) return regularStreams

        val hashes = p2pStreams
            .mapNotNull { it.torboxInfoHash() }
            .map { it.lowercase() }
            .distinct()
        if (hashes.isEmpty()) return regularStreams

        val cachedHashes = mutableSetOf<String>()
        try {
            hashes.chunked(TorboxCacheCheckChunkSize).forEach { chunk ->
                val response = TorboxApiClient.checkCached(apiKey = apiKey, hashes = chunk)
                if (response.isSuccessful) {
                    response.body
                        ?.data
                        .orEmpty()
                        .keys
                        .mapTo(cachedHashes) { it.lowercase() }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return regularStreams
        }

        val cachedP2PStreams = p2pStreams.mapNotNull { stream ->
            val hash = stream.torboxInfoHash()?.lowercase() ?: return@mapNotNull null
            if (hash !in cachedHashes) return@mapNotNull null
            stream.asTorboxDirectDebridStream()
        }

        return (regularStreams + cachedP2PStreams).distinctBy {
            listOf(
                it.addonId,
                it.streamLabel,
                it.streamSubtitle.orEmpty(),
                it.torboxInfoHash().orEmpty().lowercase(),
                it.fileIdx?.toString().orEmpty(),
                it.clientResolve?.fileIdx?.toString().orEmpty(),
            ).joinToString("|")
        }
    }
}

fun StreamItem.asTorboxDirectDebridStream(): StreamItem {
    if (isDirectDebridStream) return this
    val hash = torboxInfoHash()
    val magnet = reusableTorboxSourceUrl() ?: directPlaybackUrl?.takeIf { it.isMagnetUri() }
    if (hash.isNullOrBlank() && magnet.isNullOrBlank()) return this
    val filename = behaviorHints.filename
        ?: title
        ?: name
    return copy(
        url = null,
        externalUrl = null,
        clientResolve = StreamClientResolve(
            type = "debrid",
            service = DebridProviders.TORBOX_ID,
            isCached = true,
            infoHash = hash,
            fileIdx = fileIdx,
            magnetUri = magnet,
            sources = sources,
            torrentName = title ?: name,
            filename = filename,
        ),
    )
}

fun StreamItem.reusableTorboxSourceUrl(): String? {
    if (!isPlainP2PStream() && clientResolve?.service != DebridProviders.TORBOX_ID) return null
    val existingMagnet = clientResolve?.magnetUri
        ?: directPlaybackUrl?.takeIf { it.isMagnetUri() }
    val hash = torboxInfoHash()
    val baseMagnet = existingMagnet
        ?: hash?.takeIf { it.isNotBlank() }?.let { buildMagnetUri(it, sources) }
        ?: return null
    val fileIndex = clientResolve?.fileIdx ?: fileIdx
    val filename = clientResolve?.filename ?: behaviorHints.filename ?: title ?: name
    return baseMagnet.withTorboxReusableParams(fileIndex = fileIndex, filename = filename)
}

fun String.isReusableTorboxP2PSource(): Boolean =
    isMagnetUri() && extractBtihHash() != null

fun String.withoutTorboxReusableParams(): String {
    if (!isMagnetUri() || !contains("nuvio_", ignoreCase = true)) return this
    val prefix = substringBefore('?', missingDelimiterValue = this)
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return this
    val cleaned = query
        .split('&')
        .filterNot { part ->
            part.substringBefore('=', missingDelimiterValue = "").percentDecode()
                .startsWith("nuvio_", ignoreCase = true)
        }
        .joinToString("&")
    return if (cleaned.isBlank()) prefix else "$prefix?$cleaned"
}

fun String.toTorboxReusableStreamItem(
    streamName: String,
    addonName: String,
    addonId: String,
    bingeGroup: String? = null,
): StreamItem? {
    val hash = extractBtihHash() ?: return null
    val params = magnetQueryParams()
    val filename = params.firstValue(TorboxReusableFilenameParam)
    val fileIndex = params.firstValue(TorboxReusableFileIdxParam)?.toIntOrNull()
    val trackers = params.getAll("tr")
    return StreamItem(
        name = streamName,
        title = streamName,
        infoHash = hash,
        fileIdx = fileIndex,
        sources = trackers,
        addonName = addonName,
        addonId = addonId,
        behaviorHints = StreamBehaviorHints(
            filename = filename,
            bingeGroup = bingeGroup,
        ),
    ).asTorboxDirectDebridStream()
}

fun StreamItem.torboxInfoHash(): String? =
    infoHash?.trim()?.takeIf { it.isNotBlank() }
        ?: clientResolve?.infoHash?.trim()?.takeIf { it.isNotBlank() }
        ?: clientResolve?.magnetUri?.extractBtihHash()
        ?: directPlaybackUrl?.extractBtihHash()

private fun StreamItem.isPlainP2PStream(): Boolean =
    !isDirectDebridStream &&
        (
            !infoHash.isNullOrBlank() ||
                directPlaybackUrl.isMagnetUri()
        )

private fun buildMagnetUri(hash: String, trackers: List<String>): String =
    buildString {
        append("magnet:?xt=urn:btih:")
        append(hash.trim())
        trackers
            .filter { it.isNotBlank() }
            .forEach { tracker ->
                append("&tr=")
                append(encodePathSegment(tracker))
            }
    }

private fun String.withTorboxReusableParams(fileIndex: Int?, filename: String?): String =
    buildString {
        val params = queryString(
            TorboxReusableFileIdxParam to fileIndex?.toString(),
            TorboxReusableFilenameParam to filename?.takeIf { it.isNotBlank() },
        )
        if (params.isBlank()) {
            append(this@withTorboxReusableParams)
            return@buildString
        }
        append(this@withTorboxReusableParams)
        if (!contains("?")) append("?")
        val separator = if (endsWith("?") || endsWith("&")) "" else "&"
        append(separator)
        append(params)
    }

private fun String?.isMagnetUri(): Boolean =
    this?.trimStart()?.startsWith("magnet:", ignoreCase = true) == true

private fun String.extractBtihHash(): String? {
    if (!isMagnetUri()) return null
    val xtValues = magnetQueryParams().getAll("xt")
    return xtValues
        .firstNotNullOfOrNull { value ->
            value.substringAfter("urn:btih:", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
        }
        ?.trim()
}

private fun String.magnetQueryParams(): Map<String, List<String>> {
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return emptyMap()
    return query
        .split('&')
        .mapNotNull { part ->
            val key = part.substringBefore('=', missingDelimiterValue = "").percentDecode()
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter('=', missingDelimiterValue = "").percentDecode()
            key to value
        }
        .groupBy({ it.first }, { it.second })
}

private fun Map<String, List<String>>.firstValue(key: String): String? =
    this[key]?.firstOrNull()

private fun Map<String, List<String>>.getAll(key: String): List<String> =
    get(key).orEmpty()

private fun String.percentDecode(): String {
    if (indexOf('%') < 0 && indexOf('+') < 0) return this
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '%' && index + 2 < length -> {
                val hex = substring(index + 1, index + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    index += 3
                } else {
                    bytes += char.code.toByte()
                    index++
                }
            }
            char == '+' -> {
                bytes += ' '.code.toByte()
                index++
            }
            else -> {
                char.toString().encodeToByteArray().forEach { bytes += it }
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
