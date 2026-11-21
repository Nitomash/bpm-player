package com.nitomash.bpmplayer.data.linkimport.yandex

import org.json.JSONObject
import java.time.Instant

data class YandexResource(
    val type: String?,
    val path: String?,
    val name: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val modifiedAtMs: Long?,
    val items: List<YandexResource>?,
    val error: String?,
    val message: String?,
) {
    val isDirectory: Boolean get() = type == "dir"
    val isFile: Boolean get() = type == "file"
}

object YandexJson {

    fun resource(json: JSONObject): YandexResource = YandexResource(
        type = json.optString("type").takeIf { it.isNotEmpty() },
        path = json.optString("path").takeIf { it.isNotEmpty() },
        name = json.optString("name").takeIf { it.isNotEmpty() },
        sizeBytes = json.optLong("size", -1L).takeIf { it >= 0L },
        mimeType = json.optString("mime_type").takeIf { it.isNotEmpty() },
        modifiedAtMs = json.optString("modified").takeIf { it.isNotEmpty() }?.let(::instantMs),
        items = json.optJSONObject("_embedded")
            ?.optJSONArray("items")
            ?.let { array -> (0 until array.length()).map { resource(array.getJSONObject(it)) } },
        error = json.optString("error").takeIf { it.isNotEmpty() },
        message = json.optString("message").takeIf { it.isNotEmpty() },
    )

    fun downloadHref(json: JSONObject): String? =
        json.optString("href").takeIf { it.isNotEmpty() }

    private fun instantMs(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
