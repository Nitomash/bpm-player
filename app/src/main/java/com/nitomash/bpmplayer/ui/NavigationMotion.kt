package com.nitomash.bpmplayer.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal object NavigationMotion {
    const val DURATION_MS = 200
    val nestedOffset = 24.dp
    val fadeInSpec = tween<Float>(DURATION_MS, easing = LinearOutSlowInEasing)
    val fadeOutSpec = tween<Float>(DURATION_MS, easing = FastOutLinearInEasing)
    val slideSpec = tween<IntOffset>(DURATION_MS, easing = FastOutSlowInEasing)
}

@Composable
internal fun rememberNestedOffsetPx(): Int {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    return remember(layoutDirection, density) {
        with(density) { NavigationMotion.nestedOffset.roundToPx() } *
            if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    }
}
