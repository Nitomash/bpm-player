package com.nitomash.bpmplayer.domain.linkimport

@JvmInline
value class RelativeImportPath private constructor(val value: String) {

    companion object {
        val ROOT = RelativeImportPath("")

        fun of(raw: String): RelativeImportPath? {
            val segments = raw.split('/').mapNotNull(::sanitizeSegment)
            return RelativeImportPath(segments.joinToString("/"))
        }

        fun child(parent: RelativeImportPath, name: String): RelativeImportPath? {
            val segment = sanitizeSegment(name) ?: return null
            return RelativeImportPath(
                if (parent.value.isEmpty()) segment else "${parent.value}/$segment",
            )
        }

        fun parentOf(path: RelativeImportPath): RelativeImportPath =
            RelativeImportPath(path.value.substringBeforeLast('/', ""))

        fun nameOf(path: RelativeImportPath): String =
            path.value.substringAfterLast('/')

        private fun sanitizeSegment(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
            val cleaned = trimmed
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
                .trim()
                .trim('.')
                .trim()
            return cleaned.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        }
    }
}
