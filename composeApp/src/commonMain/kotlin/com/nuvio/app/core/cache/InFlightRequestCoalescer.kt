package com.nuvio.app.core.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class InFlightRequestCoalescer<K, V> {
    private val mutex = Mutex()
    private val requests = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun runCoalesced(
        key: K,
        block: suspend () -> V,
    ): V {
        var ownsRequest = false
        val request = mutex.withLock {
            requests[key] ?: CompletableDeferred<V>().also { created ->
                requests[key] = created
                ownsRequest = true
            }
        }

        if (!ownsRequest) return request.await()

        try {
            val result = block()
            request.complete(result)
            return result
        } catch (error: Throwable) {
            request.completeExceptionally(error)
            throw error
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (requests[key] === request) {
                        requests.remove(key)
                    }
                }
            }
        }
    }
}
