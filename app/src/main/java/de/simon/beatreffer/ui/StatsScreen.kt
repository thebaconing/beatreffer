package de.simon.beatreffer.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.simon.beatreffer.R
import de.simon.beatreffer.core.Insight
import de.simon.beatreffer.core.PracticeType
import de.simon.beatreffer.core.Progress
import de.simon.beatreffer.core.SessionSummary
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class Range(val days: Int?) { D7(7), D30(30), D90(90), ALL(null) }
private enum class Scope { ALL, CURRENT, BEAT, SUBDIVISION, PATTERN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
    var range by rememberSaveable { mutableStateOf(Range.D30) }
    var scope by rememberSaveable { mutableStateOf(Scope.ALL) }
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    val now = System.currentTimeMillis()
    val currentKey = vm.config.comparisonKey()
    val filtered = vm.sessions.filter { s ->
        val inRange = range.days == null || now - s.timestampMillis <= range.days!! * 86_400_000L
        val inScope = when (scope) {
            Scope.ALL -> true
            Scope.CURRENT -> s.configKey == currentKey
            Scope.BEAT -> s.config.practiceType == PracticeType.BEAT
            Scope.SUBDIVISION -> s.config.practiceType == PracticeType.SUBDIVISION
            Scope.PATTERN -> s.config.practiceType == PracticeType.PATTERN
        }
        inRange && inScope
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress)) },
                navigationIcon = {
                    IconButton(onClick = { vm.open(Screen.HOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipChoice(Range.entries, range, {
                when (it) {
                    Range.D7 -> stringResource(R.string.range_7)
                    Range.D30 -> stringResource(R.string.range_30)
                    Range.D90 -> stringResource(R.string.range_90)
                    Range.ALL -> stringResource(R.string.range_all)
                }
            }) { range = it }
            ChipChoice(Scope.entries, scope, {
                when (it) {
                    Scope.ALL -> stringResource(R.string.scope_all)
                    Scope.CURRENT -> stringResource(R.string.scope_current)
                    Scope.BEAT -> practiceTypeLabel(PracticeType.BEAT)
                    Scope.SUBDIVISION -> practiceTypeLabel(PracticeType.SUBDIVISION)
                    Scope.PATTERN -> practiceTypeLabel(PracticeType.PATTERN)
                }
            }) { scope = it }
            if (scope == Scope.CURRENT) Hint(configSummary(vm.config))

            val usable = Progress.usable(filtered)
            if (usable.isEmpty()) {
                SectionCard(stringResource(R.string.no_data_title)) { Hint(stringResource(R.string.no_data_text)) }
            } else {
                OverviewCard(usable)
                val days = Progress.daily(usable, zone)
                SectionCard(stringResource(R.string.chart_score)) {
                    DayLineChart(days.map { it.day to it.score }, MaterialTheme.colorScheme.primary, 0.0, 100.0)
                }
                SectionCard(stringResource(R.string.chart_hit_rate)) {
                    DayLineChart(days.map { it.day to it.hitRate * 100 }, Good, 0.0, 100.0, "%")
                }
                SectionCard(stringResource(R.string.chart_deviation)) {
                    Hint(stringResource(R.string.chart_deviation_hint))
                    DayLineChart(days.map { it.day to it.meanAbsMs }, Warn, fixedMin = 0.0)
                }
                InsightCard(Progress.analyze(usable))
                BestsCard(Progress.bests(usable))
                HistoryCard(usable)
            }
            ExportCard(vm, context)
        }
    }
}

@Composable
private fun OverviewCard(list: List<SessionSummary>) {
    val a = Progress.averages(list)!!
    val minutes = list.sumOf { it.playedSec } / 60.0
    SectionCard(stringResource(R.string.overview)) {
        KeyValue(stringResource(R.string.sessions_count), list.size.toString())
        KeyValue(stringResource(R.string.practice_time), stringResource(R.string.minutes_short, minutes.toInt()))
        KeyValue(stringResource(R.string.avg_score), fmt(a.score), scoreColor(a.score))
        KeyValue(stringResource(R.string.hit_rate), "${fmt(a.hitRate * 100)} %")
        KeyValue(stringResource(R.string.mean_deviation), "${fmt(a.meanAbsMs)} ms")
    }
}

@Composable
private fun InsightCard(insights: List<Insight>) {
    SectionCard(stringResource(R.string.weaknesses)) {
        insights.forEach { ins ->
            val text = when (ins) {
                Insight.NotEnoughData -> stringResource(R.string.ins_not_enough)
                Insight.AllGood -> stringResource(R.string.ins_all_good)
                is Insight.WeakBeat -> stringResource(
                    R.string.ins_weak_beat, ins.beat, ins.timeSignature, fmt(ins.meanAbsMs), fmt(ins.avgAbsMs),
                    fmt(ins.hitRate * 100), fmt(ins.avgHitRate * 100),
                )
                is Insight.SilentWorse -> stringResource(
                    R.string.ins_silent, fmt(ins.silentHitRate * 100), fmt(ins.audibleHitRate * 100),
                    fmt(ins.silentAbsMs), fmt(ins.audibleAbsMs),
                )
                is Insight.TempoDrop -> stringResource(
                    R.string.ins_tempo, ins.fromBpm, fmt(ins.hitRateAbove * 100), fmt(ins.hitRateBelow * 100),
                )
                is Insight.Rushing -> stringResource(R.string.ins_rushing, fmt(abs(ins.meanSignedMs)))
                is Insight.Dragging -> stringResource(R.string.ins_dragging, fmt(ins.meanSignedMs))
                is Insight.SpeedingUp -> stringResource(R.string.ins_speeding_up, fmt(abs(ins.driftMsPerMin)))
                is Insight.SlowingDown -> stringResource(R.string.ins_slowing_down, fmt(ins.driftMsPerMin))
            }
            Text("• $text")
        }
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
private fun dateText(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

@Composable
private fun BestsCard(bests: List<SessionSummary>) {
    if (bests.isEmpty()) return
    SectionCard(stringResource(R.string.personal_bests)) {
        bests.take(10).forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            SessionRow(s)
        }
    }
}

@Composable
private fun HistoryCard(list: List<SessionSummary>) {
    SectionCard(stringResource(R.string.history)) {
        list.sortedByDescending { it.timestampMillis }.take(20).forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            SessionRow(s)
        }
    }
}

@Composable
private fun SessionRow(s: SessionSummary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(dateText(s.timestampMillis), style = MaterialTheme.typography.labelMedium)
            Text(configSummary(s.config), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(fmt(s.score), fontWeight = FontWeight.Bold, color = scoreColor(s.score),
                style = MaterialTheme.typography.titleMedium)
            Text("${fmt(s.hitRate * 100)} % · ${fmt(s.meanAbsMs)} ms", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ExportCard(vm: MainViewModel, context: Context) {
    SectionCard(stringResource(R.string.export)) {
        SwitchRow(stringResource(R.string.export_taps), vm.exportTaps, { vm.setExportTaps(it) },
            hint = stringResource(R.string.export_taps_hint))
        val title = stringResource(R.string.export_share_title)
        Button(
            onClick = { shareFiles(context, vm.exportFiles(), title) },
            enabled = vm.sessions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.export_csv)) }
    }
}

private fun shareFiles(context: Context, files: List<File>, title: String) {
    if (files.isEmpty()) return
    val authority = context.packageName + ".fileprovider"
    val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = "text/csv"
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, title))
}
