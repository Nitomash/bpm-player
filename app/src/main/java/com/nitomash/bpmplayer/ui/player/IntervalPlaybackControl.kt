package com.nitomash.bpmplayer.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.settings.AppSettings
import kotlin.math.roundToInt

private const val MAX_INPUT_LENGTH = 4
private const val SWITCH_SCALE = 0.75f

private fun Modifier.scaledLayout(scale: Float): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = (placeable.width * scale).roundToInt()
        val height = (placeable.height * scale).roundToInt()
        layout(width, height) {
            placeable.place(-(placeable.width - width) / 2, -(placeable.height - height) / 2)
        }
    }.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

internal fun parseSecondsInput(text: String, maxValue: Int): Int? =
    text.trim()
        .takeIf { it.isNotEmpty() }
        ?.toIntOrNull()
        ?.coerceIn(0, maxValue)

@Composable
internal fun IntervalPlaybackControl(
    settings: AppSettings,
    onEnabledChange: (Boolean) -> Unit,
    onLimitCommit: (Int) -> Unit,
    onGapCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = settings.playbackProcessingEnabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scaledLayout(SWITCH_SCALE),
        )
        if (settings.playbackProcessingEnabled) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_interval_prefix),
                    style = MaterialTheme.typography.bodySmall,
                )
                IntervalSecondsField(
                    value = settings.playbackLimitSeconds,
                    maxValue = AppSettings.PLAYBACK_LIMIT_MAX,
                    onCommit = onLimitCommit,
                )
                Text(
                    text = stringResource(R.string.player_interval_middle),
                    style = MaterialTheme.typography.bodySmall,
                )
                IntervalSecondsField(
                    value = settings.playbackGapSeconds,
                    maxValue = AppSettings.PLAYBACK_GAP_MAX,
                    onCommit = onGapCommit,
                )
                Text(
                    text = stringResource(R.string.player_interval_end),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.player_interval_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun IntervalSecondsField(
    value: Int,
    maxValue: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var wasFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val borderColor = if (wasFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val commit = {
        val parsed = parseSecondsInput(text, maxValue)
        if (parsed == null) {
            text = value.toString()
        } else {
            text = parsed.toString()
            if (parsed != value) onCommit(parsed)
        }
    }

    BasicTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= MAX_INPUT_LENGTH && input.all(Char::isDigit)) {
                text = input
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commit()
                focusManager.clearFocus()
            },
        ),
        modifier = modifier
            .widthIn(min = 36.dp)
            .width(IntrinsicSize.Min)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused) {
                    commit()
                }
                wasFocused = focusState.isFocused
            },
    )
}
