package com.nitomash.bpmplayer.domain.linkimport

interface PublicLinkProvider {
    val id: String

    fun canHandle(link: String): Boolean

    suspend fun inspect(link: String): RemoteItem

    suspend fun listChildren(directory: RemoteDirectory, pageToken: String?): RemotePage

    suspend fun openDownload(file: RemoteFile): RemoteDownload
}

interface PublicLinkProviderRegistry {
    fun find(link: String): PublicLinkProvider?
    fun findById(id: String): PublicLinkProvider?
}

interface ImportPlanBuilder {
    suspend fun build(root: RemoteItem, limits: ImportLimits): ImportPlan
}

interface DownloadedFileVerifier {
    suspend fun verify(entry: String, kind: ImportFileKind): Boolean
}

interface AppLibraryRootRepository {
    suspend fun getRoot(): AppLibraryRoot?
    suspend fun saveRoot(uri: String, displayName: String): AppLibraryRoot
    suspend fun clearRoot()
}

data class AppLibraryRoot(
    val uri: String,
    val displayName: String,
)
