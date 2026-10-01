package de.simon.beatreffer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.simon.beatreffer.BuildConfig
import de.simon.beatreffer.R
import de.simon.beatreffer.core.Calibration
import de.simon.beatreffer.core.ClickSound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { vm.open(Screen.HOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScrollWithIndicator().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.calibration)) {
                Hint(stringResource(R.string.calibration_explain))
                KeyValue(
                    stringResource(R.string.calibration_value),
                    if (vm.isCalibrated) signedMs(vm.calibrationMs) else stringResource(R.string.not_calibrated),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.openCalibration() }) { Text(stringResource(R.string.calibrate_now)) }
                    if (vm.isCalibrated) OutlinedButton(onClick = { vm.resetCalibration() }) { Text(stringResource(R.string.reset)) }
                }
            }

            SectionCard(stringResource(R.string.click_sound)) {
                ChipChoice(ClickSound.entries, vm.config.sound, { soundLabel(it) }) { s -> vm.updateConfig { it.copy(sound = s) } }
                OutlinedButton(onClick = { vm.previewSound() }) { Text(stringResource(R.string.preview)) }
            }

            SectionCard(stringResource(R.string.display)) {
                SwitchRow(
                    stringResource(R.string.live_feedback), vm.config.liveFeedback,
                    { v -> vm.updateConfig { it.copy(liveFeedback = v) } },
                    hint = stringResource(R.string.live_feedback_hint),
                )
            }

            SectionCard(stringResource(R.string.data)) {
                Hint(stringResource(R.string.data_local_hint))
                OutlinedButton(onClick = { confirmDelete = true }, enabled = vm.sessions.isNotEmpty()) {
                    Text(stringResource(R.string.delete_all))
                }
            }

            SectionCard(stringResource(R.string.about)) {
                Hint(stringResource(R.string.about_text, BuildConfig.VERSION_NAME))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllData(); confirmDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(vm: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration)) },
                navigationIcon = {
                    IconButton(onClick = { vm.open(Screen.SETTINGS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.calibration_steps, Calibration.BEATS, Calibration.SKIP_FIRST))
            when {
                vm.calibrationRunning -> {
                    Text(stringResource(R.string.calibration_taps, vm.calibrationTapCount), fontWeight = FontWeight.Bold)
                }
                vm.calibrationResult != null -> {
                    SectionCard(stringResource(R.string.calibration_result)) {
                        Text(signedMs(vm.calibrationResult!!), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Hint(stringResource(R.string.calibration_result_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { vm.acceptCalibration() }) { Text(stringResource(R.string.apply)) }
                            OutlinedButton(onClick = { vm.startCalibration() }) { Text(stringResource(R.string.again)) }
                        }
                    }
                }
                else -> {
                    if (vm.calibrationFailed) Text(stringResource(R.string.calibration_failed), color = Bad)
                    Button(onClick = { vm.startCalibration() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.start))
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { ch ->
                                    if (ch.changedToDownIgnoreConsumed()) vm.onCalibrationTap(ch.uptimeMillis)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tap_here), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
