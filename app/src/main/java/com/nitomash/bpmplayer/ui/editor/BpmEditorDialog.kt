package com.nitomash.bpmplayer.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.bpm.BpmDetectionFailure
import com.nitomash.bpmplayer.data.bpm.TapBpmDetector
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.ui.library.AutoDetectUiState
import kotlinx.coroutines.delay

private const val INTERNAL_ERROR_DISMISS_MS = 1500L

@Composable
fun BpmEditorDialog(
    track: TrackEntity,
    isPlaying: Boolean,
    autoDetectState: AutoDetectUiState,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onSave: (Double, BpmSource) -> Unit,
    onAutoDetect: () -> Unit,
    onCancelAutoDetect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val detector = remember(track.id) { TapBpmDetector() }
    var bpm by remember(track.id) { mutableStateOf(track.bpm) }
    var manualText by remember(track.id) { mutableStateOf(track.bpm?.let(::formatBpm) ?: "") }
    var tapCount by remember(track.id) { mutableIntStateOf(0) }
    var source by remember(track.id) {
        mutableStateOf(track.bpmSource ?: if (track.bpm != null) BpmSource.TAP else null)
    }
    var showDetectHint by remember { mutableStateOf(false) }

    LaunchedEffect(autoDetectState) {
        if (autoDetectState is AutoDetectUiState.Detected) {
            bpm = autoDetectState.bpm
            manualText = formatBpm(autoDetectState.bpm)
            source = BpmSource.AUTO
        }
        if (autoDetectState is AutoDetectUiState.Failed &&
            autoDetectState.reason == BpmDetectionFailure.INTERNAL_ERROR
        ) {
            delay(INTERNAL_ERROR_DISMISS_MS)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSkipForward) {
                        Text(
                            text = stringResource(R.string.editor_skip_forward),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.editor_pause else R.string.editor_play,
                            ),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.editor_bpm_value, formatBpmOrDefault(bpm)),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.editor_taps_label, tapCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TapTempoArea(
                    onTap = { timestampMs ->
                        source = BpmSource.TAP
                        detector.tap(timestampMs)?.let { result ->
                            bpm = result.toDouble()
                            manualText = formatBpm(result.toDouble())
                        }
                        tapCount = detector.tapCount
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(88.dp),
                )

                Text(
                    text = stringResource(R.string.editor_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedTextField(
                    value = manualText,
                    onValueChange = { input ->
                        manualText = input
                        bpm = parseBpm(input)?.toDouble()
                        source = BpmSource.MANUAL
                    },
                    label = { Text(stringResource(R.string.editor_manual_label)) },
                    isError = manualText.isNotEmpty() && bpm == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                when (autoDetectState) {
                    AutoDetectUiState.Idle -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onAutoDetect,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.editor_detect_label))
                        }
                        Box {
                            IconButton(onClick = { showDetectHint = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = stringResource(R.string.editor_detect_hint_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = showDetectHint,
                                onDismissRequest = { showDetectHint = false },
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_detect_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .widthIn(max = 320.dp)
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                    AutoDetectUiState.Detecting -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.editor_detect_running),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelAutoDetect) {
                            Text(stringResource(R.string.editor_cancel))
                        }
                    }
                    is AutoDetectUiState.Detected -> Text(
                        text = stringResource(R.string.editor_detect_done, formatBpm(autoDetectState.bpm)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is AutoDetectUiState.Failed -> Text(
                        text = stringResource(detectErrorText(autoDetectState.reason)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_cancel))
                    }
                    Button(
                        onClick = { bpm?.let { onSave(it, source ?: BpmSource.TAP) } },
                        enabled = bpm != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.editor_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun TapTempoArea(
    onTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                        .also { down -> onTap(down.uptimeMillis) }
                    waitForUpOrCancellation()
                }
            }
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.editor_tap_button),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatBpmOrDefault(bpm: Double?): String = bpm?.let(::formatBpm) ?: "—"

private fun detectErrorText(reason: BpmDetectionFailure): Int = when (reason) {
    BpmDetectionFailure.DECODE_FAILED -> R.string.editor_detect_error_decode
    BpmDetectionFailure.NO_ONSETS -> R.string.editor_detect_error_no_onsets
    BpmDetectionFailure.WEAK_BEAT -> R.string.editor_detect_error_weak
    BpmDetectionFailure.INTERNAL_ERROR -> R.string.editor_detect_error_internal
}