package com.nitomash.bpmplayer.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.settings.AppSettings

@Composable
internal fun PlaybackStartDelayControl(
    settings: AppSettings,
    onEnabledChange: (Boolean) -> Unit,
    onSecondsCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = settings.playbackStartDelayEnabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scaledLayout(SWITCH_SCALE),
        )
        if (settings.playbackStartDelayEnabled) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_start_delay_prefix),
                    style = MaterialTheme.typography.bodySmall,
                )
                IntervalSecondsField(
                    value = settings.playbackStartDelaySeconds,
                    maxValue = AppSettings.PLAYBACK_START_DELAY_MAX,
                    onCommit = onSecondsCommit,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = stringResource(R.string.player_start_delay_end),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.player_start_delay_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
