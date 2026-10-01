package de.thebaconing.beatreffer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import de.thebaconing.beatreffer.R
import de.thebaconing.beatreffer.core.ClickSound
import de.thebaconing.beatreffer.core.DurationType
import de.thebaconing.beatreffer.core.MuteMode
import de.thebaconing.beatreffer.core.PatternLevel
import de.thebaconing.beatreffer.core.Patterns
import de.thebaconing.beatreffer.core.PracticeConfig
import de.thebaconing.beatreffer.core.PracticeType
import de.thebaconing.beatreffer.core.TargetMode
import de.thebaconing.beatreffer.core.Tolerance
import de.thebaconing.beatreffer.core.VisualHint
import java.util.Locale

/** Uebersetzte Bezeichnungen fuer Enums und Einstellungen. */

@Composable
fun isGerman(): Boolean = LocalConfiguration.current.locales[0].language == "de"

@Composable
fun muteLabel(m: MuteMode): String = stringResource(
    when (m) {
        MuteMode.ALWAYS_ON -> R.string.mute_always_on
        MuteMode.ACCENT_ONLY -> R.string.mute_accent_only
        MuteMode.ALTERNATE -> R.string.mute_alternate
        MuteMode.RANDOM_GAPS -> R.string.mute_random_gaps
        MuteMode.COUNT_IN_SILENT -> R.string.mute_count_in_silent
        MuteMode.FULL_SILENT -> R.string.mute_full_silent
    }
)

@Composable
fun muteHint(m: MuteMode): String = stringResource(
    when (m) {
        MuteMode.ALWAYS_ON -> R.string.mute_always_on_hint
        MuteMode.ACCENT_ONLY -> R.string.mute_accent_only_hint
        MuteMode.ALTERNATE -> R.string.mute_alternate_hint
        MuteMode.RANDOM_GAPS -> R.string.mute_random_gaps_hint
        MuteMode.COUNT_IN_SILENT -> R.string.mute_count_in_silent_hint
        MuteMode.FULL_SILENT -> R.string.mute_full_silent_hint
    }
)

@Composable
fun practiceTypeLabel(t: PracticeType): String = stringResource(
    when (t) {
        PracticeType.BEAT -> R.string.type_beat
        PracticeType.SUBDIVISION -> R.string.type_subdivision
        PracticeType.PATTERN -> R.string.type_pattern
    }
)

@Composable
fun subdivisionLabel(s: Int): String = stringResource(
    when (s) {
        2 -> R.string.sub_eighths
        3 -> R.string.sub_triplets
        else -> R.string.sub_sixteenths
    }
)

@Composable
fun toleranceLabel(t: Tolerance): String = stringResource(
    when (t) {
        Tolerance.EASY -> R.string.tol_easy
        Tolerance.MEDIUM -> R.string.tol_medium
        Tolerance.HARD -> R.string.tol_hard
    }, t.windowMs
)

@Composable
fun soundLabel(s: ClickSound): String = stringResource(
    when (s) {
        ClickSound.WOODBLOCK -> R.string.sound_woodblock
        ClickSound.BEEP -> R.string.sound_beep
        ClickSound.HIHAT -> R.string.sound_hihat
    }
)

@Composable
fun visualLabel(v: VisualHint): String = stringResource(
    when (v) {
        VisualHint.PULSE -> R.string.visual_pulse
        VisualHint.TARGET_ONLY -> R.string.visual_target_only
        VisualHint.NONE -> R.string.visual_none
    }
)

@Composable
fun targetModeLabel(t: TargetMode): String = stringResource(
    when (t) {
        TargetMode.FIXED -> R.string.target_fixed
        TargetMode.ROTATE -> R.string.target_rotate
    }
)

@Composable
fun durationTypeLabel(d: DurationType): String = stringResource(
    when (d) {
        DurationType.MINUTES -> R.string.duration_minutes
        DurationType.BARS -> R.string.duration_bars
    }
)

@Composable
fun levelLabel(l: PatternLevel): String = stringResource(
    when (l) {
        PatternLevel.EASY -> R.string.level_easy
        PatternLevel.MEDIUM -> R.string.level_medium
        PatternLevel.HARD -> R.string.level_hard
    }
)

/** Kurze Beschreibung einer Einstellung, z. B. "4/4 · 120 BPM · Grundschlag · Ton an". */
@Composable
fun configSummary(raw: PracticeConfig): String {
    val c = raw.effective()
    val parts = ArrayList<String>()
    val what = when (c.practiceType) {
        PracticeType.PATTERN -> Patterns.byId(c.patternId).name(isGerman())
        PracticeType.SUBDIVISION -> subdivisionLabel(c.subdivision)
        PracticeType.BEAT -> practiceTypeLabel(c.practiceType)
    }
    val ts = c.timeSignature
    parts += if (ts.grouping.size > 1) "${ts.label} (${ts.groupingLabel})" else ts.label
    parts += "${c.bpm} BPM"
    parts += what
    if (c.practiceType != PracticeType.PATTERN) {
        if (c.targetMode == TargetMode.ROTATE) parts += stringResource(R.string.target_rotate)
        else if (c.targetBeats.isNotEmpty()) parts += stringResource(R.string.summary_beats, c.targetBeats.sorted().joinToString("+") { (it + 1).toString() })
    }
    parts += muteLabel(c.muteMode)
    parts += "±${c.tolerance.windowMs} ms"
    return parts.joinToString(" · ")
}

fun fmt(v: Double, digits: Int = 0): String = String.format(Locale.getDefault(), "%.${digits}f", v)

fun signedMs(v: Double): String {
    val r = Math.round(v)
    return if (r > 0) "+$r ms" else "$r ms"
}
