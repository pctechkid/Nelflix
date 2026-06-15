package com.nuvio.app.features.addons

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import platform.Foundation.NSUserDefaults

actual object AddonStorage {
    private const val addonUrlsKey = "installed_manifest_urls"
    private const val addonNamesKey = "installed_manifest_names"

    actual fun loadInstalledAddonUrls(profileId: Int): List<String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonUrlsKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    actual fun saveInstalledAddonUrls(profileId: Int, urls: List<String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            urls.joinToString(separator = "\n"),
            forKey = "${addonUrlsKey}_$profileId",
        )
    }

    actual fun loadInstalledAddonNames(profileId: Int): Map<String, String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonNamesKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('\t')
                if (separatorIndex <= 0) return@mapNotNull null
                val url = line.substring(0, separatorIndex).trim()
                val name = line.substring(separatorIndex + 1).trim()
                if (url.isEmpty() || name.isEmpty()) null else url to name
            }
            .toMap()

    actual fun saveInstalledAddonNames(profileId: Int, namesByUrl: Map<String, String>) {
        val payload = namesByUrl.entries
            .mapNotNull { (url, name) ->
                val safeUrl = url.trim()
                val safeName = name.replace('\n', ' ').replace('\t', ' ').trim()
                if (safeUrl.isEmpty() || safeName.isEmpty()) null else "$safeUrl\t$safeName"
            }
            .joinToString(separator = "\n")

        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${addonNamesKey}_$profileId",
        )
    }
}

private val addonHttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
    expectSuccess = false
}

actual suspend fun httpGetText(url: String): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Request failed with HTTP ${response.status.value}")
            }
            if (payload.isBlank()) {
                throw IllegalStateException("Empty response body")
            }
            payload
        }

actual suspend fun httpPostJson(url: String, body: String): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Request failed with HTTP ${response.status.value}")
            }
            if (payload.isBlank()) {
                throw IllegalStateException("Empty response body")
            }
            payload
        }

actual suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Request failed with HTTP ${response.status.value}")
            }
            if (payload.isBlank()) {
                throw IllegalStateException("Empty response body")
            }
            payload
        }

actual suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Request failed with HTTP ${response.status.value}")
            }
            if (payload.isBlank()) {
                throw IllegalStateException("Empty response body")
            }
            payload
        }

actual suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean,
): RawHttpResponse =
    addonHttpClient
        .request {
            url(url)
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (this.method == HttpMethod.Post || this.method == HttpMethod.Put || this.method == HttpMethod.Patch) {
                setBody(body)
            }
        }
        .let { response ->
            RawHttpResponse(
                status = response.status.value,
                statusText = response.status.description,
                url = response.call.request.url.toString(),
                body = response.bodyAsText(),
                headers = response.headers.entries().associate { (name, values) ->
                    name.lowercase() to values.joinToString(",")
                },
            )
        }

actual suspend fun httpRequestRawBytes(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: ByteArray,
    followRedirects: Boolean,
): RawHttpResponse =
    addonHttpClient
        .request {
            url(url)
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (this.method == HttpMethod.Post || this.method == HttpMethod.Put || this.method == HttpMethod.Patch) {
                setBody(body)
            }
        }
        .let { response ->
            RawHttpResponse(
                status = response.status.value,
                statusText = response.status.description,
                url = response.call.request.url.toString(),
                body = response.bodyAsText(),
                headers = response.headers.entries().associate { (name, values) ->
                    name.lowercase() to values.joinToString(",")
                },
            )
        }

actual suspend fun httpGetBytesWithHeaders(
    url: String,
    headers: Map<String, String>,
    maxBytes: Int,
): RawBinaryHttpResponse =
    addonHttpClient
        .get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        .let { response ->
            val payload = response.body<ByteArray>()
            if (payload.size > maxBytes) {
                error("Response exceeded maximum size")
            }
            RawBinaryHttpResponse(
                status = response.status.value,
                statusText = response.status.description,
                url = response.call.request.url.toString(),
                body = payload,
                headers = response.headers.entries().associate { (name, values) ->
                    name.lowercase() to values.joinToString(",")
                },
            )
        }
