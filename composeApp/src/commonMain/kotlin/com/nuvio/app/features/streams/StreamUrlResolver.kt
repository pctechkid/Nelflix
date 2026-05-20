package com.nuvio.app.features.streams

data class ResolvedStreamUrl(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
)

expect object StreamUrlResolver {
    suspend fun resolve(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): ResolvedStreamUrl
}
