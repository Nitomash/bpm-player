package com.nitomash.bpmplayer.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.key
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
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.ui.common.AddToPlaylistDropdown
import com.nitomash.bpmplayer.ui.editor.BpmEditorDialog
import com.nitomash.bpmplayer.ui.editor.formatBpm
import com.nitomash.bpmplayer.ui.library.BpmEditorViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    editorViewModel: BpmEditorViewModel = hiltViewModel(),
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenGenerated: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val queueSize by viewModel.queueSize.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPlaylistId by viewModel.currentPlaylistId.collectAsStateWithLifecycle()
    val isGeneratedQueue by viewModel.isGeneratedQueue.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val selectedTrack by editorViewModel.selectedTrack.collectAsStateWithLifecycle()
    val editorAutoDetect by editorViewModel.autoDetectState.collectAsStateWithLifecycle()

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_player)) },
                actions = {
                    val playlistId = currentPlaylistId
                    if (playlistId != null) {
                        TextButton(onClick = { onOpenPlaylist(playlistId) }) {
                            Text(stringResource(R.string.player_open_playlist))
                        }
                    } else if (isGeneratedQueue) {
                        TextButton(onClick = onOpenGenerated) {
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
                NowPlayingInfo(viewModel)
                CurrentBpmBadge(
                    currentTrack = viewModel.currentTrack,
                    currentBpm = viewModel.currentBpm,
                    playlists = viewModel.playlists,
                    playlistsContainingTrack = viewModel.playlistsContainingCurrentTrack,
                    onEditBpm = editorViewModel::show,
                    onToggleInPlaylist = viewModel::toggleCurrentTrackInPlaylist,
                )
                SeekBarRow(
                    positionMs = viewModel.positionMs,
                    durationMs = viewModel.durationMs,
                    onSeek = viewModel::seekTo,
                )
            }

            TransportBar(
                modifier = Modifier.padding(top = 32.dp),
                isPlaying = viewModel.isPlaying,
                queueSize = viewModel.queueSize,
                hasPrevious = viewModel.hasPrevious,
                hasNext = viewModel.hasNext,
                shuffleEnabled = viewModel.shuffleEnabled,
                repeatMode = viewModel.repeatMode,
                onToggleShuffle = viewModel::toggleShuffle,
                onPrevious = viewModel::previous,
                onTogglePlayback = viewModel::togglePlayback,
                onNext = viewModel::next,
                onCycleRepeat = viewModel::cycleRepeat,
            )

            IntervalPlaybackControl(
                settings = appSettings,
                onEnabledChange = viewModel::setPlaybackProcessingEnabled,
                onLimitCommit = viewModel::setPlaybackLimitSeconds,
                onGapCommit = viewModel::setPlaybackGapSeconds,
                modifier = Modifier.padding(top = 24.dp),
            )
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

@Composable
private fun NowPlayingInfo(viewModel: PlayerViewModel) {
    val currentNumber by viewModel.currentNumber.collectAsStateWithLifecycle()
    val queueSize by viewModel.queueSize.collectAsStateWithLifecycle()
    val isPreview by viewModel.isPreview.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()

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

    key(currentTitle) {
        Text(
            text = currentTitle ?: stringResource(R.string.player_no_track),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .basicMarquee(repeatDelayMillis = 2000),
        )
    }
}

@Composable
private fun CurrentBpmBadge(
    currentTrack: StateFlow<TrackEntity?>,
    currentBpm: StateFlow<Double?>,
    playlists: StateFlow<List<PlaylistSummary>>,
    playlistsContainingTrack: StateFlow<Set<Long>>,
    onEditBpm: (TrackEntity) -> Unit,
    onToggleInPlaylist: (Long) -> Unit,
) {
    val track by currentTrack.collectAsStateWithLifecycle()
    val bpm by currentBpm.collectAsStateWithLifecycle()
    val playlistItems by playlists.collectAsStateWithLifecycle()
    val containing by playlistsContainingTrack.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    track?.let { current ->
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bpm?.let { value ->
                    stringResource(R.string.bpm_value, formatBpm(value))
                } ?: stringResource(R.string.bpm_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onEditBpm(current) }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.player_edit_bpm),
                    modifier = Modifier.size(20.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        modifier = Modifier.size(20.dp),
                    )
                }
                AddToPlaylistDropdown(
                    expanded = menuOpen,
                    playlists = playlistItems,
                    containing = containing,
                    onDismiss = { menuOpen = false },
                    onToggle = { playlistId ->
                        menuOpen = false
                        onToggleInPlaylist(playlistId)
                    },
                )
            }
        }
    }
}

@Composable
private fun SeekBarRow(
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    onSeek: (Long) -> Unit,
) {
    val position by positionMs.collectAsStateWithLifecycle()
    val duration by durationMs.collectAsStateWithLifecycle()
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val sliderFraction = dragFraction
        ?: if (duration > 0L) position.toFloat() / duration.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMillis(position),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderFraction.coerceIn(0f, 1f),
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { fraction ->
                    onSeek((fraction * duration).toLong())
                }
                dragFraction = null
            },
            enabled = duration > 0L,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = formatMillis(duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportBar(
    isPlaying: StateFlow<Boolean>,
    queueSize: StateFlow<Int>,
    hasPrevious: StateFlow<Boolean>,
    hasNext: StateFlow<Boolean>,
    shuffleEnabled: StateFlow<Boolean>,
    repeatMode: StateFlow<Int>,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing by isPlaying.collectAsStateWithLifecycle()
    val size by queueSize.collectAsStateWithLifecycle()
    val previousAvailable by hasPrevious.collectAsStateWithLifecycle()
    val nextAvailable by hasNext.collectAsStateWithLifecycle()
    val shuffle by shuffleEnabled.collectAsStateWithLifecycle()
    val repeat by repeatMode.collectAsStateWithLifecycle()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleShuffle,
            enabled = size > 0,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.player_shuffle),
                tint = if (shuffle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onPrevious,
            enabled = previousAvailable,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
            )
        }
        IconButton(
            onClick = onTogglePlayback,
            enabled = size > 0,
            modifier = Modifier.size(72.dp),
        ) {
            if (playing) {
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
            onClick = onNext,
            enabled = nextAvailable,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
            )
        }
        IconButton(
            onClick = onCycleRepeat,
            enabled = size > 0,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                else Icons.Filled.Repeat,
                contentDescription = stringResource(repeatLabel(repeat)),
                tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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