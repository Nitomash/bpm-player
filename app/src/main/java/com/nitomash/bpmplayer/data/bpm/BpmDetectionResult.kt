package com.nitomash.bpmplayer.data.bpm

sealed interface BpmDetectionResult {
    data class Success(val bpm: Double, val quality: Float) : BpmDetectionResult
    data class Failure(val reason: BpmDetectionFailure) : BpmDetectionResult
}

enum class BpmDetectionFailure {
    DECODE_FAILED,
    NO_ONSETS,
    WEAK_BEAT,
    INTERNAL_ERROR,
}