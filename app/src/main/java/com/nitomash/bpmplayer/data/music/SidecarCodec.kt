package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource

data class SidecarEnvelope(
    val version: Int,
    val records: List<SidecarTrackRecord>,
)

object SidecarCodec {

    fun encodeAll(records: List<SidecarTrackRecord>): String = buildString { encodeTo(records, this) }

    fun encodeTo(records: List<SidecarTrackRecord>, out: Appendable) {
        out.append(SIDECAR_MAGIC)
        out.append('\n')
        records.forEach { record ->
            encodeRecordTo(record, out)
            out.append('\n')
        }
    }

    fun encodeRecord(record: SidecarTrackRecord): String = buildString { encodeRecordTo(record, this) }

    private fun encodeRecordTo(record: SidecarTrackRecord, out: Appendable) {
        out.append('{')
        out.append("\"path\":")
        out.append(Json.encodeString(record.path))
        record.title?.takeIf { it.isNotEmpty() }?.let {
            out.append(",\"title\":")
            out.append(Json.encodeString(it))
        }
        record.artist?.takeIf { it.isNotEmpty() }?.let {
            out.append(",\"artist\":")
            out.append(Json.encodeString(it))
        }
        record.durationMs?.takeIf { it > 0 }?.let {
            out.append(",\"duration_ms\":")
            out.append(it.toString())
        }
        record.bpm?.let {
            out.append(",\"bpm\":")
            out.append(formatNumber(it))
        }
        record.source?.let {
            out.append(",\"source\":")
            out.append(Json.encodeString(sourceKey(it)))
        }
        record.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            out.append(",\"tags\":[")
            tags.forEachIndexed { index, tag ->
                if (index > 0) out.append(',')
                out.append("{\"name\":")
                out.append(Json.encodeString(tag.name))
                out.append(",\"rating\":")
                out.append(tag.rating.toString())
                out.append('}')
            }
            out.append(']')
        }
        out.append('}')
    }

    fun decode(text: String): SidecarEnvelope? {
        val lines = text.lineSequence()
            .map { it.trimEnd('\r', '\n') }
            .toList()
        if (lines.isEmpty()) return null
        val version = when (lines[0]) {
            SIDECAR_MAGIC -> VERSION
            SIDECAR_LEGACY_MAGIC -> LEGACY_VERSION
            else -> return null
        }
        val records = mutableListOf<SidecarTrackRecord>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val parsed = runCatching { Json.parse(line) }.getOrNull() ?: continue
            toRecord(parsed)?.let(records::add)
        }
        return SidecarEnvelope(version, records)
    }

    private fun toRecord(node: JsonValue): SidecarTrackRecord? {
        val obj = node as? JsonValue.Object ?: return null
        val path = obj.string("path") ?: return null
        val tags = obj.list("tags")?.mapNotNull { tagNode ->
            val tagObj = tagNode as? JsonValue.Object ?: return@mapNotNull null
            val name = tagObj.string("name") ?: return@mapNotNull null
            val rating = tagObj.rawInt("rating") ?: return@mapNotNull null
            SidecarTag(name, rating)
        }
        return SidecarTrackRecord(
            path = path,
            title = obj.stringOrNull("title"),
            artist = obj.stringOrNull("artist"),
            durationMs = obj.rawLong("duration_ms"),
            bpm = obj.rawDouble("bpm"),
            source = sourceFromKey(obj.stringOrNull("source")),
            tags = tags,
        )
    }

    private fun formatNumber(value: Double): String {
        val rounded = value.toLong()
        return if (value == rounded.toDouble()) rounded.toString() else value.toString()
    }

    private fun sourceKey(source: BpmSource): String = when (source) {
        BpmSource.MANUAL -> "manual"
        BpmSource.TAP -> "tap"
        BpmSource.AUTO -> "auto"
    }

    private fun sourceFromKey(key: String?): BpmSource? = when (key) {
        "manual" -> BpmSource.MANUAL
        "tap" -> BpmSource.TAP
        "auto" -> BpmSource.AUTO
        else -> null
    }

    private const val VERSION = 2
    private const val LEGACY_VERSION = 1
}

internal sealed class JsonValue {
    data class Object(val fields: Map<String, JsonValue>) : JsonValue()
    data class Array(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val raw: String) : JsonValue()
    data object True : JsonValue()
    data object False : JsonValue()
    data object Null : JsonValue()
}

internal fun JsonValue.Object.string(key: String): String? =
    (fields[key] as? JsonValue.Str)?.value

internal fun JsonValue.Object.stringOrNull(key: String): String? = string(key)

internal fun JsonValue.Object.rawLong(key: String): Long? =
    (fields[key] as? JsonValue.Num)?.raw?.toLongOrNull()

internal fun JsonValue.Object.rawDouble(key: String): Double? =
    (fields[key] as? JsonValue.Num)?.raw?.toDoubleOrNull()

internal fun JsonValue.Object.rawInt(key: String): Int? =
    (fields[key] as? JsonValue.Num)?.raw?.toIntOrNull()

internal fun JsonValue.Object.list(key: String): List<JsonValue>? =
    (fields[key] as? JsonValue.Array)?.items

internal object Json {

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw IllegalArgumentException("Trailing characters at ${parser.index}")
        return value
    }

    fun encodeString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        var index = 0

        fun atEnd(): Boolean = index >= text.length

        private fun current(): Char {
            if (atEnd()) throw IllegalArgumentException("Unexpected end of input")
            return text[index]
        }

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            return when (current()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> {
                    expect("true")
                    JsonValue.True
                }
                'f' -> {
                    expect("false")
                    JsonValue.False
                }
                'n' -> {
                    expect("null")
                    JsonValue.Null
                }
                else -> JsonValue.Num(parseNumber())
            }
        }

        private fun expect(word: String) {
            if (index + word.length > text.length || text.substring(index, index + word.length) != word) {
                throw IllegalArgumentException("Expected \"$word\" at $index")
            }
            index += word.length
        }

        private fun parseObject(): JsonValue.Object {
            index++
            skipWhitespace()
            val fields = linkedMapOf<String, JsonValue>()
            if (current() == '}') {
                index++
                return JsonValue.Object(fields)
            }
            while (true) {
                skipWhitespace()
                if (current() != '"') throw IllegalArgumentException("Expected object key at $index")
                val key = parseString()
                skipWhitespace()
                if (current() != ':') throw IllegalArgumentException("Expected ':' at $index")
                index++
                fields[key] = parseValue()
                skipWhitespace()
                when (current()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Object(fields)
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): JsonValue.Array {
            index++
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (current() == ']') {
                index++
                return JsonValue.Array(items)
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (current()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Array(items)
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            index++
            val result = StringBuilder()
            while (true) {
                val ch = current()
                if (ch == '"') {
                    index++
                    break
                }
                if (ch == '\\') {
                    index++
                    when (val escaped = current()) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            val hex = text.substring(index + 1, (index + 5).coerceAtMost(text.length))
                            if (hex.length != 4) throw IllegalArgumentException("Invalid unicode escape at $index")
                            result.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> throw IllegalArgumentException("Invalid escape at $index")
                    }
                    index++
                } else {
                    result.append(ch)
                    index++
                }
            }
            return result.toString()
        }

        private fun parseNumber(): String {
            val start = index
            while (index < text.length && text[index] in NUMBER_CHARS) index++
            if (index == start) throw IllegalArgumentException("Invalid value at $start")
            return text.substring(start, index)
        }

        private companion object {
            val NUMBER_CHARS = "0123456789-+.eE".toSet()
        }
    }
}