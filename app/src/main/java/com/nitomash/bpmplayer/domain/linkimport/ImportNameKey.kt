package com.nitomash.bpmplayer.domain.linkimport

import java.text.Normalizer

object ImportNameKey {

    fun of(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)

    fun matches(left: String, right: String): Boolean = of(left) == of(right)

    fun index(entries: List<ImportEntry>): Map<String, ImportEntry> =
        entries.associateBy { of(it.name) }
}
