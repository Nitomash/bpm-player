package com.nitomash.bpmplayer.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

internal object NavigationMotion {
    const val DURATION_MS = 200
    val nestedOffset = 24.dp
    val fadeInSpec = tween<Float>(DURATION_MS, easing = LinearOutSlowInEasing)
    val fadeOutSpec = tween<Float>(DURATION_MS, easing = FastOutLinearInEasing)
    val slideSpec = tween<IntOffset>(DURATION_MS, easing = FastOutSlowInEasing)
}
