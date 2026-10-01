package de.simon.beatreffer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.simon.beatreffer.R
import de.simon.beatreffer.core.AppMode
import de.simon.beatreffer.core.DurationType
import de.simon.beatreffer.core.MuteMode
import de.simon.beatreffer.core.PatternLevel
import de.simon.beatreffer.core.Patterns
import de.simon.beatreffer.core.PracticeConfig
import de.simon.beatreffer.core.PracticeType
import de.simon.beatreffer.core.TargetMode
import de.simon.beatreffer.core.TimeSignature
import de.simon.beatreffer.core.Tolerance
import de.simon.beatreffer.core.VisualHint
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val c = vm.config
    val advanced = c.appMode == AppMode.ADVANCED
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { vm.open(Screen.STATS) }) { Text(stringResource(R.string.progress)) }
                    IconButton(onClick = { vm.open(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { vm.startPractice() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(56.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  " + stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(
                options = listOf(AppMode.SIMPLE, AppMode.ADVANCED),
                selected = c.appMode,
                label = { stringResource(if (it == AppMode.SIMPLE) R.string.mode_simple else R.string.mode_advanced) },
                onSelect = { m -> vm.updateConfig { it.copy(appMode = m) } },
            )

            if (!vm.isCalibrated) {
                SectionCard(stringResource(R.string.calibration_missing_title)) {
                    Hint(stringResource(R.string.calibration_missing_text))
                    OutlinedButton(onClick = { vm.openCalibration() }) { Text(stringResource(R.string.calibrate_now)) }
                }
            }

            TempoCard(c, vm)
            DurationCard(c, vm)
            if (advanced) {
                PracticeTypeCard(c, vm)
                if (c.practiceType != PracticeType.PATTERN) {
                    TimeSignatureCard(c, vm)
                    TargetCard(c, vm)
                }
            }
            SoundCard(c, vm, advanced)
            if (advanced) EvaluationCard(c, vm)
        }
    }
}

@Composable
private fun TempoCard(c: PracticeConfig, vm: MainViewModel) {
    SectionCard(stringResource(R.string.tempo)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(c.bpm, { v -> vm.updateConfig { it.copy(bpm = v) } }, PracticeConfig.MIN_BPM..PracticeConfig.MAX_BPM,
                format = { "$it" })
            Text("  BPM", style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = c.bpm.toFloat(),
            onValueChange = { v -> vm.updateConfig { it.copy(bpm = v.roundToInt()) } },
            valueRange = PracticeConfig.MIN_BPM.toFloat()..PracticeConfig.MAX_BPM.toFloat(),
        )
        if (c.appMode == AppMode.ADVANCED && c.effective().timeSignature.denominator != 4) {
            Hint(stringResource(R.string.tempo_unit_hint, c.effective().timeSignature.denominator))
        }
    }
}

@Composable
private fun DurationCard(c: PracticeConfig, vm: MainViewModel) {
    SectionCard(stringResource(R.string.duration)) {
        Segmented(
            options = DurationType.entries,
            selected = c.durationType,
            label = { durationTypeLabel(it) },
            onSelect = { d -> vm.updateConfig { it.copy(durationType = d) } },
        )
        if (c.durationType == DurationType.MINUTES) {
            Stepper(c.durationMinutes, { v -> vm.updateConfig { it.copy(durationMinutes = v) } }, 1..60,
                format = { stringResource(R.string.minutes_short, it) })
        } else {
            Stepper(c.durationBars, { v -> vm.updateConfig { it.copy(durationBars = v) } }, 4..512, step = 4)
        }
    }
}

@Composable
private fun SoundCard(c: PracticeConfig, vm: MainViewModel, advanced: Boolean) {
    SectionCard(stringResource(R.string.sound_section)) {
        val modes = if (advanced) MuteMode.entries else MuteMode.SIMPLE_MODES
        val current = c.effective().muteMode
        modes.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateConfig { it.copy(muteMode = m) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = m == current, onClick = { vm.updateConfig { it.copy(muteMode = m) } })
                Column {
                    Text(muteLabel(m))
                    Hint(muteHint(m))
                }
            }
        }
        if (advanced) {
            when (current) {
                MuteMode.ALTERNATE -> {
                    Stepper(c.alternateOnBars, { v -> vm.updateConfig { it.copy(alternateOnBars = v) } }, 1..8,
                        label = stringResource(R.string.bars_on))
                    Stepper(c.alternateOffBars, { v -> vm.updateConfig { it.copy(alternateOffBars = v) } }, 1..8,
                        label = stringResource(R.string.bars_off))
                }
                MuteMode.RANDOM_GAPS -> Stepper(c.gapPercent, { v -> vm.updateConfig { it.copy(gapPercent = v) } }, 10..70,
                    step = 10, label = stringResource(R.string.gap_share), format = { "$it %" })
                else -> {}
            }
            HorizontalDivider()
            Stepper(c.countInBars, { v -> vm.updateConfig { it.copy(countInBars = v) } }, 1..4,
                label = stringResource(R.string.count_in_bars))
            Text(stringResource(R.string.visual_in_silence))
            ChipChoice(VisualHint.entries, c.visualHint, { visualLabel(it) }) { v -> vm.updateConfig { it.copy(visualHint = v) } }
        }
    }
}

@Composable
private fun PracticeTypeCard(c: PracticeConfig, vm: MainViewModel) {
    SectionCard(stringResource(R.string.practice_type)) {
        Segmented(PracticeType.entries, c.practiceType, { practiceTypeLabel(it) }) { t ->
            vm.updateConfig { it.copy(practiceType = t) }
        }
        when (c.practiceType) {
            PracticeType.SUBDIVISION -> {
                ChipChoice(listOf(2, 3, 4), c.subdivision, { subdivisionLabel(it) }) { s ->
                    vm.updateConfig { it.copy(subdivision = s) }
                }
                SwitchRow(stringResource(R.string.subdivision_audible), c.subdivisionAudible, { v ->
                    vm.updateConfig { it.copy(subdivisionAudible = v) }
                })
            }
            PracticeType.PATTERN -> PatternList(c, vm)
            PracticeType.BEAT -> Hint(stringResource(R.string.type_beat_hint))
        }
    }
}

@Composable
private fun PatternList(c: PracticeConfig, vm: MainViewModel) {
    val german = isGerman()
    PatternLevel.entries.forEach { level ->
        Text(levelLabel(level), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Patterns.ALL.filter { it.level == level }.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateConfig { it.copy(patternId = p.id) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = c.patternId == p.id, onClick = { vm.updateConfig { it.copy(patternId = p.id) } })
                Column {
                    Text("${p.name(german)}  (${p.timeSignature.label})")
                    Text(p.grid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Hint(stringResource(R.string.pattern_legend))
}

/** Alle Aufteilungen von n in Zweier und Dreier, plus "keine Gruppierung". */
private fun groupingOptions(n: Int): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    fun rec(rest: Int, acc: List<Int>) {
        if (out.size >= 8) return
        if (rest == 0) { out += acc; return }
        if (rest >= 3) rec(rest - 3, acc + 3)
        if (rest >= 2) rec(rest - 2, acc + 2)
    }
    if (n >= 4) rec(n, emptyList())
    return (listOf(listOf(n)) + out).distinct()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeSignatureCard(c: PracticeConfig, vm: MainViewModel) {
    val ts = c.timeSignature
    SectionCard(stringResource(R.string.time_signature)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeSignature.QUICK.forEach { q ->
                FilterChip(
                    selected = ts.numerator == q.numerator && ts.denominator == q.denominator,
                    onClick = { vm.updateConfig { it.copy(timeSignature = q, targetBeats = emptySet()) } },
                    label = { Text(q.label) },
                )
            }
        }
        Stepper(ts.numerator, { n ->
            vm.updateConfig { it.copy(timeSignature = TimeSignature.of(n, ts.denominator), targetBeats = emptySet()) }
        }, 1..16, label = stringResource(R.string.numerator))
        Text(stringResource(R.string.denominator))
        ChipChoice(listOf(2, 4, 8, 16), ts.denominator, { "/$it" }) { d ->
            vm.updateConfig { it.copy(timeSignature = TimeSignature.of(ts.numerator, d)) }
        }
        if (ts.numerator >= 4) {
            Text(stringResource(R.string.grouping))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groupingOptions(ts.numerator).forEach { g ->
                    FilterChip(
                        selected = ts.grouping == g,
                        onClick = { vm.updateConfig { it.copy(timeSignature = TimeSignature(ts.numerator, ts.denominator, g)) } },
                        label = { Text(if (g.size == 1) stringResource(R.string.grouping_none) else g.joinToString("+")) },
                    )
                }
            }
            GroupingField(ts, vm)
        }
    }
}

@Composable
private fun GroupingField(ts: TimeSignature, vm: MainViewModel) {
    var text by remember(ts) { mutableStateOf(ts.groupingLabel) }
    val parsed = TimeSignature.parseGrouping(text, ts.numerator)
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() || it == '+' }
            TimeSignature.parseGrouping(text, ts.numerator)?.let { g ->
                if (g != ts.grouping) vm.updateConfig { it.copy(timeSignature = TimeSignature(ts.numerator, ts.denominator, g)) }
            }
        },
        label = { Text(stringResource(R.string.grouping_custom)) },
        isError = parsed == null,
        supportingText = { if (parsed == null) Text(stringResource(R.string.grouping_error, ts.numerator)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetCard(c: PracticeConfig, vm: MainViewModel) {
    val n = c.timeSignature.numerator
    SectionCard(stringResource(R.string.target_beat)) {
        Segmented(TargetMode.entries, c.targetMode, { targetModeLabel(it) }) { m -> vm.updateConfig { it.copy(targetMode = m) } }
        if (c.targetMode == TargetMode.FIXED) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in 0 until n) {
                    FilterChip(
                        selected = b in c.targetBeats,
                        onClick = {
                            vm.updateConfig {
                                val s = if (b in it.targetBeats) it.targetBeats - b else it.targetBeats + b
                                // Alle ausgewaehlt ist dasselbe wie keiner ausgewaehlt
                                it.copy(targetBeats = if (s.size == n) emptySet() else s)
                            }
                        },
                        label = { Text("${b + 1}", fontWeight = FontWeight.Bold) },
                    )
                }
            }
            Hint(
                if (c.targetBeats.isEmpty()) stringResource(R.string.target_all_hint)
                else stringResource(R.string.target_some_hint)
            )
            if (c.targetBeats.isNotEmpty()) {
                TextButton(onClick = { vm.updateConfig { it.copy(targetBeats = emptySet()) } }) {
                    Text(stringResource(R.string.target_reset))
                }
            }
        } else {
            Hint(stringResource(R.string.target_rotate_hint, n))
        }
    }
}

@Composable
private fun EvaluationCard(c: PracticeConfig, vm: MainViewModel) {
    SectionCard(stringResource(R.string.evaluation)) {
        Text(stringResource(R.string.tolerance))
        Segmented(Tolerance.entries, c.tolerance, { toleranceLabel(it) }) { t -> vm.updateConfig { it.copy(tolerance = t) } }
        SwitchRow(
            stringResource(R.string.count_extra_taps), c.countExtraTaps,
            { v -> vm.updateConfig { it.copy(countExtraTaps = v) } },
            hint = stringResource(R.string.count_extra_taps_hint),
        )
    }
}
