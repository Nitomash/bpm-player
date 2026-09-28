package com.nitomash.bpmplayer.data.db.dao

internal const val SQL_CHUNK_SIZE = 500

internal fun <T> List<T>.sqlChunks(): List<List<T>> = chunked(SQL_CHUNK_SIZE)
