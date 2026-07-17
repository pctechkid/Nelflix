package com.nuvio.app.features.trailer

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

actual object TrailerPlaybackResolver {
    private const val PLAYBACK_SOURCE_CACHE_LIMIT = 12
    private val playbackSourceCacheTtl = 5.minutes

    private val extractor by lazy { InAppYouTubeExtractor() }
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<TrailerPlaybackSource?>>()
    private val playbackSourceCache = LinkedHashMap<String, CachedPlaybackSource>()

    actual suspend fun resolveFromYouTubeUrl(youtubeUrl: String): TrailerPlaybackSource? {
        val cacheKey = youtubeUrl.trim()
        if (cacheKey.isBlank()) return null

        readCachedPlaybackSource(cacheKey)?.let { return it }

        return coroutineScope {
            var owner = false
            val sharedResolution = lock.withLock {
                inFlight[cacheKey] ?: async(start = CoroutineStart.LAZY) {
                    extractor.extractPlaybackSource(cacheKey)
                }.also {
                    inFlight[cacheKey] = it
                    owner = true
                }
            }

            if (owner) {
                sharedResolution.start()
            }

            try {
                sharedResolution.await()?.also { resolved ->
                    if (owner) {
                        rememberPlaybackSource(cacheKey, resolved)
                    }
                }
            } finally {
                if (owner) {
                    lock.withLock {
                        if (inFlight[cacheKey] === sharedResolution) {
                            inFlight.remove(cacheKey)
                        }
                    }
                }
            }
        }
    }

    private suspend fun readCachedPlaybackSource(cacheKey: String): TrailerPlaybackSource? {
        return lock.withLock {
            val entry = playbackSourceCache[cacheKey] ?: return@withLock null
            if (entry.createdAt.elapsedNow() >= playbackSourceCacheTtl) {
                playbackSourceCache.remove(cacheKey)
                return@withLock null
            }
            playbackSourceCache.remove(cacheKey)
            playbackSourceCache[cacheKey] = entry
            entry.source
        }
    }

    private suspend fun rememberPlaybackSource(
        cacheKey: String,
        source: TrailerPlaybackSource,
    ) {
        lock.withLock {
            playbackSourceCache.remove(cacheKey)
            playbackSourceCache[cacheKey] = CachedPlaybackSource(
                source = source,
                createdAt = TimeSource.Monotonic.markNow(),
            )
            while (playbackSourceCache.size > PLAYBACK_SOURCE_CACHE_LIMIT) {
                val oldestKey = playbackSourceCache.keys.firstOrNull() ?: break
                playbackSourceCache.remove(oldestKey)
            }
        }
    }

    private data class CachedPlaybackSource(
        val source: TrailerPlaybackSource,
        val createdAt: TimeMark,
    )
}
