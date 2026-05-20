package com.nuvio.app.features.streams

actual object StreamUrlResolver {
    actual suspend fun resolve(
        url: String,
        requestHeaders: Map<String, String>,
    ): ResolvedStreamUrl = ResolvedStreamUrl(url = url, requestHeaders = requestHeaders)
}
