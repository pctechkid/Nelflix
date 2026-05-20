package com.nuvio.app.features.streams

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

actual object StreamUrlResolver {
    private const val maxRedirects = 8

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    actual suspend fun resolve(
        url: String,
        requestHeaders: Map<String, String>,
    ): ResolvedStreamUrl = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext ResolvedStreamUrl(url = url, requestHeaders = requestHeaders)
        }

        runCatching {
            resolveWithMethod(url = url, requestHeaders = requestHeaders, method = ResolveMethod.Head)
        }.recoverCatching {
            resolveWithMethod(url = url, requestHeaders = requestHeaders, method = ResolveMethod.RangeGet)
        }.getOrElse {
            ResolvedStreamUrl(url = url, requestHeaders = requestHeaders)
        }
    }

    private fun resolveWithMethod(
        url: String,
        requestHeaders: Map<String, String>,
        method: ResolveMethod,
    ): ResolvedStreamUrl {
        var currentUrl = url
        val cookies = linkedSetOf<String>()

        repeat(maxRedirects) {
            val request = Request.Builder()
                .url(currentUrl)
                .applyHeaders(requestHeaders, cookies)
                .apply {
                    when (method) {
                        ResolveMethod.Head -> head()
                        ResolveMethod.RangeGet -> {
                            get()
                            header("Range", "bytes=0-0")
                        }
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                cookies += response.setCookiePairs()
                val location = response.redirectLocation()
                if (location.isNullOrBlank()) {
                    if (method == ResolveMethod.Head && response.code >= 400) {
                        error("HEAD stream resolution failed with HTTP ${response.code}")
                    }
                    val resolvedHeaders = requestHeaders.withCookies(cookies)
                    return ResolvedStreamUrl(url = currentUrl, requestHeaders = resolvedHeaders)
                }
                currentUrl = location
            }
        }

        return ResolvedStreamUrl(url = currentUrl, requestHeaders = requestHeaders.withCookies(cookies))
    }

    private fun Request.Builder.applyHeaders(
        headers: Map<String, String>,
        cookies: Set<String>,
    ): Request.Builder = apply {
        headers.forEach { (name, value) ->
            if (!name.equals("Range", ignoreCase = true) && !name.equals("Host", ignoreCase = true)) {
                header(name, value)
            }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            header("User-Agent", "Mozilla/5.0")
        }
        if (cookies.isNotEmpty()) {
            val existingCookie = headers.entries.firstOrNull { (name, _) ->
                name.equals("Cookie", ignoreCase = true)
            }?.value
            header("Cookie", listOfNotNull(existingCookie, cookies.joinToString("; ")).joinToString("; "))
        }
    }

    private fun Map<String, String>.withCookies(cookies: Set<String>): Map<String, String> {
        if (cookies.isEmpty()) return this
        val existingCookie = entries.firstOrNull { (name, _) ->
            name.equals("Cookie", ignoreCase = true)
        }?.value
        val withoutCookie = filterKeys { name -> !name.equals("Cookie", ignoreCase = true) }
        return withoutCookie + ("Cookie" to listOfNotNull(existingCookie, cookies.joinToString("; ")).joinToString("; "))
    }

    private fun Response.redirectLocation(): String? {
        if (code !in 300..399) return null
        val location = header("Location")?.takeIf { it.isNotBlank() } ?: return null
        return request.url.resolve(location)?.toString() ?: location
    }

    private fun Response.setCookiePairs(): List<String> =
        headers("Set-Cookie").mapNotNull { header ->
            header.substringBefore(";").takeIf { it.contains("=") && it.isNotBlank() }
        }

    private enum class ResolveMethod {
        Head,
        RangeGet,
    }
}
