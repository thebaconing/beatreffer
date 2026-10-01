package de.simon.beatreffer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.simon.beatreffer.R
import de.simon.beatreffer.core.Averages
import de.simon.beatreffer.core.SessionSummary
import de.simon.beatreffer.core.Stat
import de.simon.beatreffer.core.Suggestion
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: MainViewModel) {
    val r = vm.result ?: return
    val s = r.summary
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.result)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { vm.open(Screen.HOME) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.done))
                    }
                    Button(onClick = { vm.startPractice() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.again))
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(configSummary(s.config), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!s.completed) Hint(stringResource(R.string.stopped_early))
            if (!r.saved) Hint(stringResource(R.string.not_saved))

            ScoreCard(s, r.comparison.isNewBest)
            DetailsCard(s)
            if (s.audible.expected > 0 && s.silent.expected > 0) SilentCard(s.audible, s.silent)
            if (s.perBeat.size > 1) PerBeatCard(s.perBeat)
            CompareCard(s, r.comparison.last7, r.comparison.last30)
            if (r.suggestions.isNotEmpty()) SuggestionCard(r.suggestions) { vm.applySuggestion(it) }
        }
    }
}

@Composable
private fun ScoreCard(s: SessionSummary, newBest: Boolean) {
    SectionCard(stringResource(R.string.score)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmt(s.score), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                color = scoreColor(s.score))
            Text("  / 100", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (newBest) Text(stringResource(R.string.new_best), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Hint(stringResource(R.string.score_hint))
    }
}

fun scoreColor(score: Double): Color = when {
    score >= 85 -> Good
    score >= 60 -> Warn
    else -> Bad
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun DetailsCard(s: SessionSummary) {
    SectionCard(stringResource(R.string.details)) {
        KeyValue(stringResource(R.string.hit_rate), "${fmt(s.hitRate * 100)} %  (${s.hits}/${s.expected})")
        KeyValue(stringResource(R.string.mean_deviation), if (s.hits == 0) "-" else "${fmt(s.meanAbsMs)} ms")
        KeyValue(stringResource(R.string.spread), if (s.hits < 2) "-" else "±${fmt(s.stdDevMs)} ms")
        KeyValue(stringResource(R.string.tendency), tendencyText(s.meanSignedMs, s.hits))
        s.driftMsPerMin?.let { KeyValue(stringResource(R.string.drift), driftText(it)) }
        KeyValue(stringResource(R.string.misses), s.misses.toString())
        KeyValue(stringResource(R.string.extra_taps), s.extras.toString())
    }
}

@Composable
fun tendencyText(signed: Double, hits: Int): String = when {
    hits == 0 -> "-"
    signed <= -5 -> stringResource(R.string.tendency_early, fmt(abs(signed)))
    signed >= 5 -> stringResource(R.string.tendency_late, fmt(signed))
    else -> stringResource(R.string.tendency_centered)
}

@Composable
fun driftText(d: Double): String = when {
    d <= -10 -> stringResource(R.string.drift_faster, fmt(abs(d)))
    d >= 10 -> stringResource(R.string.drift_slower, fmt(d))
    else -> stringResource(R.string.drift_stable)
}

@Composable
private fun SilentCard(aud: Stat, sil: Stat) {
    SectionCard(stringResource(R.string.with_vs_without_sound)) {
        KeyValue(stringResource(R.string.with_sound), "${fmt(aud.hitRate * 100)} % · ${fmt(aud.meanAbsMs)} ms")
        KeyValue(stringResource(R.string.without_sound), "${fmt(sil.hitRate * 100)} % · ${fmt(sil.meanAbsMs)} ms")
    }
}

@Composable
fun PerBeatCard(perBeat: List<Stat>) {
    SectionCard(stringResource(R.string.per_beat)) {
        Hint(stringResource(R.string.per_beat_hint))
        val maxAbs = (perBeat.maxOfOrNull { it.meanAbsMs } ?: 1.0).coerceAtLeast(10.0)
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            perBeat.forEachIndexed { i, st ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    if (st.expected > 0) {
                        Text(if (st.hits == 0) "-" else fmt(st.meanAbsMs), style = MaterialTheme.typography.labelSmall)
                        val frac = if (st.hits == 0) 0.02f else (st.meanAbsMs / maxAbs).toFloat().coerceIn(0.05f, 1f)
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(frac * 0.7f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(scoreColor(st.hitRate * 100)),
                        )
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompareCard(s: SessionSummary, last7: Averages?, last30: Averages?) {
    SectionCard(stringResource(R.string.compare_title)) {
        if (last7 == null && last30 == null) {
            Hint(stringResource(R.string.compare_none))
        } else {
            Hint(stringResource(R.string.compare_hint))
            last7?.let { CompareRow(stringResource(R.string.last_7_days, it.count), s, it) }
            last30?.let { CompareRow(stringResource(R.string.last_30_days, it.count), s, it) }
        }
    }
}

@Composable
private fun CompareRow(label: String, s: SessionSummary, a: Averages) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        val dScore = s.score - a.score
        val dHit = (s.hitRate - a.hitRate) * 100
        val dAbs = s.meanAbsMs - a.meanAbsMs
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Delta(stringResource(R.string.score), dScore, higherIsBetter = true, unit = "")
            Delta(stringResource(R.string.hits_short), dHit, higherIsBetter = true, unit = " %")
            Delta(stringResource(R.string.deviation_short), dAbs, higherIsBetter = false, unit = " ms")
        }
    }
}

@Composable
private fun Delta(label: String, d: Double, higherIsBetter: Boolean, unit: String) {
    val better = if (higherIsBetter) d > 0.5 else d < -0.5
    val worse = if (higherIsBetter) d < -0.5 else d > 0.5
    val color = when {
        better -> Good
        worse -> Bad
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (d > 0) "+" else ""
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$sign${fmt(d)}$unit", color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SuggestionCard(list: List<Suggestion>, onApply: (Suggestion) -> Unit) {
    SectionCard(stringResource(R.string.next_level_title)) {
        Hint(stringResource(R.string.next_level_text))
        list.forEach { sug ->
            val text = when (sug) {
                is Suggestion.TempoUp -> stringResource(R.string.suggest_tempo, sug.bpm)
                is Suggestion.MoreSilence -> stringResource(R.string.suggest_silence, muteLabel(sug.muteMode))
                is Suggestion.TighterTolerance -> stringResource(R.string.suggest_tolerance, sug.tolerance.windowMs)
            }
            FilledTonalButton(onClick = { onApply(sug) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
        }
    }
}
