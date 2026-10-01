package de.simon.beatreffer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simon.beatreffer.R
import de.simon.beatreffer.core.SessionPlan
import de.simon.beatreffer.core.TapKind
import de.simon.beatreffer.core.VisualHint
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun PracticeScreen(vm: MainViewModel) {
    val session = vm.practice ?: return
    val plan = session.plan
    val showFeedback = plan.config.liveFeedback

    // Bildschirm waehrend der Uebung anlassen
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableDoubleStateOf(-1.0) }
    LaunchedEffect(plan) {
        while (true) withFrameNanos { now = vm.engine.nowSec() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(plan, now) { vm.stopPractice() }
        BeatDots(plan, now)
        FeedbackLine(vm.liveTap, showFeedback)
        TapPad(vm, showFeedback, Modifier.weight(1f))
    }
}

@Composable
private fun Header(plan: SessionPlan, now: Double, onStop: () -> Unit) {
    val bar = plan.barAt(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val title = when {
                now < 0 -> stringResource(R.string.get_ready)
                bar == null -> stringResource(R.string.finished)
                bar.isCountIn -> stringResource(R.string.count_in)
                else -> stringResource(R.string.bar_of, bar.index - plan.countInBars + 1, plan.practiceBars)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val remaining = ceil((plan.totalSec - now.coerceAtLeast(0.0)).coerceAtLeast(0.0)).toInt()
            Text(
                "${plan.config.bpm} BPM · ${plan.config.timeSignature.label} · " +
                    String.format("%d:%02d", remaining / 60, remaining % 60),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
    }
    val progress = ((now - plan.practiceStartSec) / (plan.totalSec - plan.practiceStartSec)).toFloat().coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun BeatDots(plan: SessionPlan, now: Double) {
    val bar = plan.barAt(now)
    val n = plan.beatsPerBar
    val visual = bar?.visual ?: VisualHint.TARGET_ONLY
    val beat = if (bar != null) plan.beatAt(now) else -1
    val phase = if (bar != null) ((now - bar.startSec) % plan.beatSec) / plan.beatSec else 1.0
    val groupStarts = plan.config.timeSignature.groupStarts
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.surfaceVariant
    val size = if (n <= 8) 30.dp else 18.dp

    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        if (visual == VisualHint.NONE) {
            Text(stringResource(R.string.no_visual), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(horizontalArrangement = Arrangement.spacedBy(if (n <= 8) 12.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (b in 0 until n) {
                if (b > 0 && b in groupStarts) Spacer(Modifier.size(6.dp))
                val isTarget = bar != null && b in bar.targets
                val lit = visual == VisualHint.PULSE && b == beat && phase < 0.3
                val fill = when {
                    lit && b == 0 -> primary
                    lit -> lerp(primary, Color.White, 0.35f)
                    else -> dim
                }
                Box(
                    Modifier
                        .size(if (b == 0) size * 1.15f else size)
                        .clip(CircleShape)
                        .background(fill)
                        .then(if (isTarget) Modifier.border(3.dp, primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bar?.isCountIn == true && lit) {
                        Text("${b + 1}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(live: LiveTap?, show: Boolean) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        if (show && live != null) FeedbackText(live)
    }
}

@Composable
private fun FeedbackText(live: LiveTap) {
    run {
        val o = live.outcome
        val off = o.offsetMs
        val (text, color) = when (o.kind) {
            TapKind.HIT -> {
                val v = off ?: 0.0
                when {
                    abs(v) <= live.windowMs * 0.4 -> stringResource(R.string.fb_exact) to Good
                    v < 0 -> stringResource(R.string.fb_bit_early) to Warn
                    else -> stringResource(R.string.fb_bit_late) to Warn
                }
            }
            TapKind.OUTSIDE -> stringResource(if ((off ?: 0.0) < 0) R.string.fb_too_early else R.string.fb_too_late) to Bad
            TapKind.DOUBLE -> stringResource(R.string.fb_double) to Bad
            TapKind.EXTRA -> stringResource(R.string.fb_extra) to Bad
            TapKind.IGNORED -> stringResource(R.string.fb_ignored) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        val ms = if (off != null && o.kind != TapKind.IGNORED) "   ${signedMs(off)}" else ""
        Text(text + ms, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TapPad(vm: MainViewModel, showFeedback: Boolean, modifier: Modifier) {
    val live = vm.liveTap
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val flash = remember { Animatable(0f) }
    val flashColor = when {
        live == null -> MaterialTheme.colorScheme.primary
        !showFeedback -> MaterialTheme.colorScheme.primary
        live.outcome.kind == TapKind.HIT && abs(live.outcome.offsetMs ?: 0.0) <= live.windowMs * 0.4 -> Good
        live.outcome.kind == TapKind.HIT -> Warn
        live.outcome.kind == TapKind.IGNORED -> MaterialTheme.colorScheme.primary
        else -> Bad
    }
    LaunchedEffect(live?.seq) {
        if (live != null) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(220))
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(lerp(base, flashColor, flash.value * 0.8f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { ch ->
                            if (ch.changedToDownIgnoreConsumed()) vm.onTap(ch.uptimeMillis)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.tap_here),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
