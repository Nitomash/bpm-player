package com.nitomash.bpmplayer.domain.linkimport

object ImportDuplicateNames {

    fun extraCount(entries: List<ImportEntry>): Int =
        groups(entries).sumOf { group -> group.size - 1 }

    fun toDelete(entries: List<ImportEntry>): List<ImportEntry> =
        groups(entries).flatMap { group ->
            val keeperIndex = group.indexOf(keeperOf(group))
            group.filterIndexed { index, _ -> index != keeperIndex }
        }

    fun keeperOf(entries: List<ImportEntry>): ImportEntry =
        entries.firstOrNull { it.name == ImportNameKey.of(it.name) } ?: entries.first()

    private fun groups(entries: List<ImportEntry>): List<List<ImportEntry>> =
        entries.groupBy { ImportNameKey.of(it.name) }.values.filter { it.size > 1 }
}
