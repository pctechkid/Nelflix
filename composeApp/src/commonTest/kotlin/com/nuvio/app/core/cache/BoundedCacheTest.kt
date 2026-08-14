package com.nuvio.app.core.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedCacheTest {
    @Test
    fun evictsOldestEntriesWhenLimitIsExceeded() {
        val result = linkedMapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
        ).withBoundedEntry(
            key = "four",
            value = 4,
            maxEntries = 3,
        )

        assertEquals(listOf("two", "three", "four"), result.keys.toList())
    }

    @Test
    fun replacingEntryMakesItNewestWithoutGrowingCache() {
        val result = linkedMapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
        ).withBoundedEntry(
            key = "one",
            value = 10,
            maxEntries = 3,
        )

        assertEquals(listOf("two", "three", "one"), result.keys.toList())
        assertEquals(10, result.getValue("one"))
    }

    @Test
    fun mutableCacheEvictsOldestAndRefreshesReplacementOrder() {
        val cache = linkedMapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
        )

        cache.putBoundedEntry(key = "one", value = 10, maxEntries = 3)
        cache.putBoundedEntry(key = "four", value = 4, maxEntries = 3)

        assertEquals(listOf("three", "one", "four"), cache.keys.toList())
        assertEquals(10, cache.getValue("one"))
    }

    @Test
    fun rejectsNonPositiveLimits() {
        assertFailsWith<IllegalArgumentException> {
            emptyMap<String, Int>().withBoundedEntry(
                key = "one",
                value = 1,
                maxEntries = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            mutableMapOf<String, Int>().putBoundedEntry(
                key = "one",
                value = 1,
                maxEntries = 0,
            )
        }
    }
}
