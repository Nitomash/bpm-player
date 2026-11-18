package com.nitomash.bpmplayer.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.player.TempoAdjustment
import com.nitomash.bpmplayer.data.player.TempoOffset
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.ui.editor.formatBpm

@Composable
internal fun TempoAdjustmentControl(
    settings: AppSettings,
    currentBpm: Double?,
    onEnabledChange: (Boolean) -> Unit,
    onPositionPreview: (Float) -> Unit,
    onPositionCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = settings.tempoAdjustmentEnabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scaledLayout(SWITCH_SCALE),
        )
        if (settings.tempoAdjustmentEnabled) {
            var position by remember(settings.tempoAdjustmentPosition) {
                mutableFloatStateOf(settings.tempoAdjustmentPosition)
            }
            val offset = TempoAdjustment.displayOffset(currentBpm, position)
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = tempoLabel(offset),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = position,
                    onValueChange = { value ->
                        position = value
                        onPositionPreview(value)
                    },
                    onValueChangeFinished = { onPositionCommit(position) },
                    valueRange = -1f..1f,
                )
            }
            IconButton(
                onClick = {
                    position = 0f
                    onPositionPreview(0f)
                    onPositionCommit(0f)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.player_tempo_adjustment_reset),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.player_tempo_adjustment_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun tempoLabel(offset: TempoOffset): String = when (offset) {
    is TempoOffset.Bpm -> stringResource(
        R.string.player_tempo_adjustment_bpm,
        signedValue(offset.value),
    )
    is TempoOffset.Percent -> stringResource(
        R.string.player_tempo_adjustment_percent,
        signedValue(offset.value),
    )
}

private fun signedValue(value: Float): String =
    formatBpm(value.toDouble()).let { if (value > 0f) "+$it" else it }
