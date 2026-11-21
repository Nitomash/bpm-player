package com.nitomash.bpmplayer.data.linkimport.yandex

import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface YandexDiskApi {
    suspend fun resource(publicKey: String, path: String?, limit: Int? = null, offset: Int? = null): YandexResource
    suspend fun downloadHref(publicKey: String, path: String): String
    suspend fun open(url: String): Response
}

@Singleton
class YandexDiskApiImpl @Inject constructor(
    private val client: OkHttpClient,
) : YandexDiskApi {

    override suspend fun resource(
        publicKey: String,
        path: String?,
        limit: Int?,
        offset: Int?,
    ): YandexResource = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("v1/disk/public/resources")
            .addQueryParameter("public_key", publicKey)
            .apply {
                path?.let { addQueryParameter("path", it) }
                limit?.let { addQueryParameter("limit", it.toString()) }
                offset?.let { addQueryParameter("offset", it.toString()) }
            }
            .build()
        val result = execute(url)
        if (!result.resource.isDirectory && !result.resource.isFile) {
            throw PublicLinkException.Protocol("unknown resource type")
        }
        result.resource
    }

    override suspend fun downloadHref(publicKey: String, path: String): String = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("v1/disk/public/resources/download")
            .addQueryParameter("public_key", publicKey)
            .addQueryParameter("path", path)
            .build()
        val result = execute(url)
        YandexJson.downloadHref(result.json)
            ?: throw PublicLinkException.Protocol("download href missing")
    }

    override suspend fun open(url: String): Response = withContext(Dispatchers.IO) {
        val parsed = runCatching { url.toHttpUrl() }
            .getOrElse { throw PublicLinkException.Protocol("invalid download url") }
        if (parsed.scheme != "https") throw PublicLinkException.Protocol("insecure download url")
        client.newCall(Request.Builder().url(parsed).get().build()).awaitResponse()
    }

    private suspend fun execute(url: HttpUrl): ApiResult = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).get().build()).awaitResponse().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw PublicLinkException.Protocol("invalid API response") }
            if (!response.isSuccessful) {
                val error = json.optString("error").takeIf { it.isNotEmpty() }
                val message = json.optString("message").takeIf { it.isNotEmpty() }
                throw statusError(response.code, error, message)
            }
            ApiResult(json, YandexJson.resource(json))
        }
    }

    private fun statusError(status: Int, error: String?, message: String?): PublicLinkException =
        when {
            status == 404 || error == "resource_not_found" -> PublicLinkException.LinkNotFound
            status == 403 || status == 401 || error == "Forbidden" -> PublicLinkException.AccessDenied
            status == 429 -> PublicLinkException.RateLimited
            error == "invalid_public_key" || error == "public_link_not_found" -> PublicLinkException.LinkNotFound
            else -> PublicLinkException.Protocol(message ?: error ?: "HTTP $status")
        }

    private data class ApiResult(val json: JSONObject, val resource: YandexResource)

    private companion object {
        const val HOST = "cloud-api.yandex.net"
    }
}
