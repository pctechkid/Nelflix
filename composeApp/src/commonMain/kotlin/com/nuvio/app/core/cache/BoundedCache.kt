package com.nuvio.app.core.cache

internal fun <K, V> Map<K, V>.withBoundedEntry(
    key: K,
    value: V,
    maxEntries: Int,
): Map<K, V> {
    require(maxEntries > 0) { "maxEntries must be greater than zero" }

    val result = LinkedHashMap<K, V>(minOf(size + 1, maxEntries + 1))
    result.putAll(this)
    result.putBoundedEntry(key = key, value = value, maxEntries = maxEntries)
    return result
}

internal fun <K, V> MutableMap<K, V>.putBoundedEntry(
    key: K,
    value: V,
    maxEntries: Int,
) {
    require(maxEntries > 0) { "maxEntries must be greater than zero" }

    remove(key)
    this[key] = value
    while (size > maxEntries) {
        val oldestKey = keys.firstOrNull() ?: break
        remove(oldestKey)
    }
}
