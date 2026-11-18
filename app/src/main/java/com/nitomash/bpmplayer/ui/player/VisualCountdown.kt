package com.nitomash.bpmplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun VisualCountdown(
    remainingMs: Long?,
    modifier: Modifier = Modifier,
) {
    val seconds = remainingMs?.let(::countdownSeconds) ?: return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(initialScale = 0.7f, animationSpec = tween(180)))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 1.15f, animationSpec = tween(90)))
            },
            label = "gapCountdown",
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

internal fun countdownSeconds(remainingMs: Long): Long =
    ((remainingMs.coerceAtLeast(1L) - 1L) / 1_000L) + 1L
