package com.nuvio.app.core.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InFlightRequestCoalescerTest {
    @Test
    fun concurrentCallersShareOneRequest() = runBlocking {
        val coalescer = InFlightRequestCoalescer<String, Int>()
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        var executions = 0

        val first = async {
            coalescer.runCoalesced("same") {
                executions += 1
                requestStarted.complete(Unit)
                releaseRequest.await()
                42
            }
        }
        requestStarted.await()
        val second = async {
            coalescer.runCoalesced("same") {
                executions += 1
                -1
            }
        }
        yield()
        releaseRequest.complete(Unit)

        assertEquals(42, first.await())
        assertEquals(42, second.await())
        assertEquals(1, executions)
    }

    @Test
    fun failedRequestDoesNotBlockRetry() = runBlocking {
        val coalescer = InFlightRequestCoalescer<String, Int>()
        var executions = 0

        assertFailsWith<IllegalStateException> {
            coalescer.runCoalesced("retry") {
                executions += 1
                error("first request failed")
            }
        }

        val result = coalescer.runCoalesced("retry") {
            executions += 1
            7
        }

        assertEquals(7, result)
        assertEquals(2, executions)
    }

    @Test
    fun cancelledOwnerDoesNotBlockRetry() = runBlocking {
        val coalescer = InFlightRequestCoalescer<String, Int>()
        val requestStarted = CompletableDeferred<Unit>()
        val owner = launch {
            coalescer.runCoalesced("cancelled") {
                requestStarted.complete(Unit)
                awaitCancellation()
            }
        }

        requestStarted.await()
        owner.cancelAndJoin()

        assertEquals(
            9,
            coalescer.runCoalesced("cancelled") { 9 },
        )
    }
}
