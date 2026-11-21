package com.nitomash.bpmplayer.domain.linkimport

object ImportFolderName {

    private const val FALLBACK = "Импорт"

    fun resolve(requested: String?, root: RemoteItem): String =
        from(requested) ?: from(sourceName(root)) ?: FALLBACK

    fun from(value: String?): String? =
        RelativeImportPath.of(value ?: "")?.value
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotEmpty() }

    private fun sourceName(root: RemoteItem): String? =
        when (root) {
            is RemoteDirectory -> root.displayName
            is RemoteFile -> root.displayName.substringBeforeLast('.')
        }
}
