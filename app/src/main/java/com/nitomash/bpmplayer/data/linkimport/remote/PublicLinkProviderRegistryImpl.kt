package com.nitomash.bpmplayer.data.linkimport.remote

import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicLinkProviderRegistryImpl @Inject constructor(
    providers: Set<@JvmSuppressWildcards PublicLinkProvider>,
) : PublicLinkProviderRegistry {

    private val byId = providers.associateBy { it.id }

    override fun find(link: String): PublicLinkProvider? =
        byId.values.firstOrNull { it.canHandle(link) }

    override fun findById(id: String): PublicLinkProvider? = byId[id]
}
