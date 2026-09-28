package com.nitomash.bpmplayer.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.ui.editor.BpmEditorDialog
import com.nitomash.bpmplayer.ui.editor.formatBpm
import com.nitomash.bpmplayer.ui.library.BpmEditorViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    editorViewModel: BpmEditorViewModel = hiltViewModel(),
    onOpenPlaylist: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPreview by viewModel.isPreview.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()
    val currentBpm by viewModel.currentBpm.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val currentNumber by viewModel.currentNumber.collectAsStateWithLifecycle()
    val queueSize by viewModel.queueSize.collectAsStateWithLifecycle()
    val hasPrevious by viewModel.hasPrevious.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val currentPlaylistId by viewModel.currentPlaylistId.collectAsStateWithLifecycle()
    val selectedTrack by editorViewModel.selectedTrack.collectAsStateWithLifecycle()
    val editorAutoDetect by editorViewModel.autoDetectState.collectAsStateWithLifecycle()

    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_player)) },
                actions = {
                    val playlistId = currentPlaylistId
                    if (playlistId != null) {
                        TextButton(onClick = { onOpenPlaylist(playlistId) }) {
                            Text(stringResource(R.string.player_open_playlist))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (queueSize == 0) {
                Text(
                    text = stringResource(R.string.player_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.player_position, currentNumber, queueSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isPreview) {
                    Text(
                        text = stringResource(R.string.player_preview_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Text(
                    text = currentTitle ?: stringResource(R.string.player_no_track),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )

                currentTrack?.let { track ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentBpm?.let { bpm ->
                                stringResource(R.string.bpm_value, formatBpm(bpm))
                            } ?: stringResource(R.string.bpm_unknown),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { editorViewModel.show(track) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.player_edit_bpm),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                val sliderFraction = dragFraction
                    ?: if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatMillis(positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = sliderFraction.coerceIn(0f, 1f),
                        onValueChange = { dragFraction = it },
                        onValueChangeFinished = {
                            dragFraction?.let { fraction ->
                                viewModel.seekTo((fraction * durationMs).toLong())
                            }
                            dragFraction = null
                        },
                        enabled = durationMs > 0L,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Text(
                        text = formatMillis(durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = viewModel::toggleShuffle,
                    enabled = queueSize > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = viewModel::previous,
                    enabled = hasPrevious,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.player_previous),
                    )
                }
                IconButton(
                    onClick = viewModel::togglePlayback,
                    enabled = queueSize > 0,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.editor_pause),
                            modifier = Modifier.size(40.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.editor_play),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(
                    onClick = viewModel::next,
                    enabled = hasNext,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                    )
                }
                IconButton(
                    onClick = viewModel::cycleRepeat,
                    enabled = queueSize > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                        else Icons.Filled.Repeat,
                        contentDescription = stringResource(repeatLabel(repeatMode)),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    selectedTrack?.let { track ->
        BpmEditorDialog(
            track = track,
            isPlaying = isPlaying,
            autoDetectState = editorAutoDetect,
            onTogglePlay = editorViewModel::togglePlayback,
            onSkipForward = editorViewModel::skipForward,
            onSave = { bpm, source ->
                editorViewModel.saveBpm(bpm, source)
                editorViewModel.dismiss()
            },
            onAutoDetect = editorViewModel::autoDetectBpm,
            onCancelAutoDetect = editorViewModel::cancelAutoDetect,
            onDismiss = editorViewModel::dismiss,
        )
    }
}

private fun repeatLabel(repeatMode: Int): Int = when (repeatMode) {
    Player.REPEAT_MODE_ALL -> R.string.player_loop_all
    Player.REPEAT_MODE_ONE -> R.string.player_loop_one
    else -> R.string.player_loop_off
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}