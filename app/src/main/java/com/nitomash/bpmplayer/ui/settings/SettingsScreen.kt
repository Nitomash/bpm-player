package com.nitomash.bpmplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.bpm.PcmBpmDetector
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.ui.common.LoadingPlaceholder

const val SETTINGS_ROUTE = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings?,
    onDetectorRangeChange: (Double, Double) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var minText by remember(settings?.detectorMinBpm) {
        mutableStateOf((settings?.detectorMinBpm ?: PcmBpmDetector.DEFAULT_MIN_BPM).toInt().toString())
    }
    var maxText by remember(settings?.detectorMaxBpm) {
        mutableStateOf((settings?.detectorMaxBpm ?: PcmBpmDetector.DEFAULT_MAX_BPM).toInt().toString())
    }
    var minError by remember { mutableStateOf(false) }
    var maxError by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf(false) }

    fun save() {
        if (settings == null) return
        val form = parseDetectorRangeForm(minText, maxText)
        minError = form.minError
        maxError = form.maxError
        rangeError = form.rangeError
        val range = form.bpmRange ?: return
        onDetectorRangeChange(range.first, range.second)
        onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { save() },
                        enabled = settings != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.settings_save),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (settings == null) {
            LoadingPlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_detector_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_detector_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = {
                        minText = it
                        minError = false
                        rangeError = false
                    },
                    label = { Text(stringResource(R.string.settings_min_bpm)) },
                    isError = minError,
                    supportingText = {
                        if (minError) {
                            Text(stringResource(R.string.settings_bpm_error))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = {
                        maxText = it
                        maxError = false
                        rangeError = false
                    },
                    label = { Text(stringResource(R.string.settings_max_bpm)) },
                    isError = maxError,
                    supportingText = {
                        if (maxError) {
                            Text(stringResource(R.string.settings_bpm_error))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (rangeError) {
                Text(
                    text = stringResource(R.string.settings_range_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        minText = PcmBpmDetector.DEFAULT_MIN_BPM.toInt().toString()
                        maxText = PcmBpmDetector.DEFAULT_MAX_BPM.toInt().toString()
                        minError = false
                        maxError = false
                        rangeError = false
                    },
                ) {
                    Text(stringResource(R.string.settings_detector_reset))
                }
            }
        }
    }
}
