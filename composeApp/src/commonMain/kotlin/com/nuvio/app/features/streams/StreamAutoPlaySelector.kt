package com.nuvio.app.features.streams

object StreamAutoPlaySelector {

    fun selectAutoPlayStream(
        streams: List<StreamItem>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        maxFileSizeBytes: Long? = null,
    ): StreamItem? {
        if (streams.isEmpty()) return null

        val sourceScopedStreams = when (source) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }.filter { stream ->
            stream.matchesAutoPlayMaxFileSize(maxFileSizeBytes)
        }
        if (candidateStreams.isEmpty()) return null
        if (mode == StreamAutoPlayMode.MANUAL) return null

        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = candidateStreams.firstOrNull { stream ->
                stream.behaviorHints.bingeGroup == targetBingeGroup && stream.isAutoPlayable()
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
        }

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams.firstOrNull { it.isAutoPlayable() }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()

                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                    ?: return null

                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                } else null

                val matchingStreams = candidateStreams.filter { stream ->
                    if (!stream.isAutoPlayable()) return@filter false
                    val url = stream.directPlaybackUrl.orEmpty()

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.streamLabel).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(url)
                    }

                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.firstOrNull { it.isAutoPlayable() }
            }
        }
    }

    private fun StreamItem.isAutoPlayable(): Boolean =
        directPlaybackUrl != null || isDirectDebridStream

    private fun StreamItem.matchesAutoPlayMaxFileSize(maxFileSizeBytes: Long?): Boolean {
        val limit = maxFileSizeBytes?.takeIf { it > 0L } ?: return true
        val detectedSize = detectFileSizeBytes()
        return detectedSize == null || detectedSize <= limit
    }

    private fun StreamItem.detectFileSizeBytes(): Long? {
        val raw = clientResolve?.stream?.raw
        val explicitSize = listOfNotNull(
            behaviorHints.videoSize,
            raw?.size,
            raw?.folderSize,
        )
            .filter { it > 0L }
            .maxOrNull()
        if (explicitSize != null) return explicitSize

        return listOfNotNull(
            name,
            title,
            description,
            behaviorHints.filename,
            clientResolve?.torrentName,
            clientResolve?.filename,
            raw?.torrentName,
            raw?.filename,
            raw?.parsed?.rawTitle,
            raw?.parsed?.parsedTitle,
        )
            .mapNotNull { text -> text.extractFileSizeBytes() }
            .maxOrNull()
    }

    private fun String.extractFileSizeBytes(): Long? =
        FileSizePattern.findAll(this)
            .mapNotNull { match ->
                val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    ?: return@mapNotNull null
                val multiplier = when (match.groupValues[2].lowercase()) {
                    "g", "gb", "gib" -> DecimalGigabyteBytes
                    "m", "mb", "mib" -> DecimalMegabyteBytes
                    else -> return@mapNotNull null
                }
                (value * multiplier).toLong().takeIf { it > 0L }
            }
            .maxOrNull()

    private const val DecimalMegabyteBytes = 1_000_000L
    private const val DecimalGigabyteBytes = 1_000_000_000L
    private val FileSizePattern = Regex("""(\d+(?:[\.,]\d+)?)\s*(gib|gb|g|mib|mb|m)\b""", RegexOption.IGNORE_CASE)
}
