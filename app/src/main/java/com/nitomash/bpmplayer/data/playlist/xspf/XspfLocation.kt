package com.nitomash.bpmplayer.data.playlist.xspf

object XspfLocation {

    data class Resolved(val value: String, val relative: Boolean)

    fun location(baseDir: StoragePath?, target: StoragePath?, fallback: String): Resolved = when {
        target == null -> Resolved(fallback, relative = false)
        baseDir == null -> Resolved(target.absolutePath, relative = false)
        else -> resolve(baseDir, target)
    }

    fun location(baseDir: StoragePath, target: StoragePath): String = resolve(baseDir, target).value

    fun isRelative(baseDir: StoragePath, target: StoragePath): Boolean =
        baseDir.volumeRoot == target.volumeRoot

    fun resolve(baseDir: StoragePath, location: String, knownRoots: List<String> = listOf(baseDir.volumeRoot)): StoragePath? {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("/")) return absolute(trimmed, knownRoots)
        val normalized = collapse(baseDir.segments + rawSegments(trimmed)) ?: return null
        return StoragePath.of(baseDir.volumeRoot, normalized.joinToString("/"))
    }

    private fun resolve(baseDir: StoragePath, target: StoragePath): Resolved {
        if (baseDir.volumeRoot != target.volumeRoot) {
            return Resolved(target.absolutePath, relative = false)
        }
        val base = baseDir.segments
        val dest = target.segments
        val common = commonPrefixSize(base, dest)
        val down = dest.drop(common)
        if (down.isEmpty()) {
            return Resolved(target.absolutePath, relative = false)
        }
        return Resolved("../".repeat(base.size - common) + down.joinToString("/"), relative = true)
    }

    private fun absolute(path: String, roots: List<String>): StoragePath? {
        val root = roots
            .filter { path.startsWith("$it/") }
            .maxByOrNull { it.length }
            ?: return null
        return StoragePath.of(root, path.removePrefix("$root/"))
    }

    private fun collapse(segments: List<String>): List<String>? {
        val out = ArrayDeque<String>()
        segments.forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isEmpty()) return null else out.removeLast()
                else -> out.addLast(segment)
            }
        }
        return out.toList()
    }

    private fun rawSegments(path: String): List<String> =
        path.trim().replace('\\', '/').split('/').filter { it.isNotEmpty() }

    private fun commonPrefixSize(a: List<String>, b: List<String>): Int {
        var i = 0
        while (i < a.size && i < b.size && a[i] == b[i]) i++
        return i
    }
}