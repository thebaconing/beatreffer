package de.thebaconing.beatreffer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    private fun simple(bpm: Int = 120, mute: MuteMode = MuteMode.ALWAYS_ON, bars: Int = 4) = PracticeConfig(
        appMode = AppMode.SIMPLE, bpm = bpm, muteMode = mute,
        durationType = DurationType.BARS, durationBars = bars,
    )

    @Test
    fun defaultGroupings() {
        assertEquals(listOf(4), TimeSignature.defaultGrouping(4, 4))
        assertEquals(listOf(3, 3), TimeSignature.defaultGrouping(6, 8))
        assertEquals(listOf(2, 2, 3), TimeSignature.defaultGrouping(7, 8))
        assertEquals(listOf(3, 3, 3, 3), TimeSignature.defaultGrouping(12, 8))
        assertEquals(listOf(3, 2), TimeSignature.defaultGrouping(5, 4))
        assertEquals(listOf(2, 2, 3), TimeSignature.parseGrouping("2+2+3", 7))
        assertNull(TimeSignature.parseGrouping("2+2", 7))
        assertEquals(setOf(0, 2, 4), TimeSignature(7, 8, listOf(2, 2, 3)).groupStarts)
    }

    @Test
    fun patternsAreValid() {
        // init-Bloecke pruefen die Rasterlaenge, hier nur alle einmal anfassen
        Patterns.ALL.forEach { assertTrue(it.bars >= 1) }
        assertEquals(Patterns.ALL.size, Patterns.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun simplePlanHasCountInAndAllBeats() {
        val plan = PlanBuilder.build(simple(), seed = 1)
        assertEquals(1, plan.countInBars)
        assertEquals(4, plan.practiceBars)
        assertEquals(0.5, plan.beatSec, 1e-9)
        assertEquals(16, plan.expected.size)
        assertEquals(20, plan.clicks.size)
        assertEquals(2.0, plan.expected.first().timeSec, 1e-9)
        assertEquals(ClickLevel.ACCENT, plan.clicks.first().level)
    }

    @Test
    fun startCueCountsDownToFirstTap() {
        // 120 BPM, 1 Vorzähltakt: erster Tipp bei 2,0 s, Schlag = 0,5 s
        val plan = PlanBuilder.build(simple(), seed = 1)
        val start = plan.startCue(0.0)!!
        assertEquals(4, start.beatsLeft)
        assertEquals(0.0, start.phase, 1e-9)
        assertEquals(false, start.listening)
        val mid = plan.startCue(1.75)!!
        assertEquals(1, mid.beatsLeft)
        assertEquals(0.5, mid.phase, 1e-9)
        assertEquals(0, plan.startCue(2.1)!!.beatsLeft)
        assertNull(plan.startCue(2.6))
    }

    @Test
    fun startCueListensDuringEarlierCountInBars() {
        val plan = PlanBuilder.build(simple().copy(appMode = AppMode.ADVANCED, countInBars = 2), seed = 1)
        assertTrue(plan.startCue(0.1)!!.listening)
        assertEquals(false, plan.startCue(2.1)!!.listening)
    }

    @Test
    fun simpleModeIgnoresAdvancedSettings() {
        val c = simple().copy(timeSignature = TimeSignature.of(7, 8), targetMode = TargetMode.ROTATE,
            muteMode = MuteMode.RANDOM_GAPS, tolerance = Tolerance.HARD).effective()
        assertEquals(4, c.timeSignature.numerator)
        assertEquals(TargetMode.FIXED, c.targetMode)
        assertEquals(MuteMode.ALWAYS_ON, c.muteMode)
        assertEquals(Tolerance.MEDIUM, c.tolerance)
    }

    @Test
    fun countInThenSilent() {
        val plan = PlanBuilder.build(simple(mute = MuteMode.COUNT_IN_SILENT), seed = 1)
        assertEquals(4, plan.clicks.size) // nur der Vorzaehltakt
        assertTrue(plan.expected.none { it.audible })
        assertEquals(VisualHint.TARGET_ONLY, plan.bars[1].visual)
    }

    @Test
    fun fullSilentHasNoSoundAndNoVisualAfterCountIn() {
        val plan = PlanBuilder.build(simple(mute = MuteMode.FULL_SILENT), seed = 1)
        assertEquals(0, plan.clicks.size)
        assertEquals(VisualHint.PULSE, plan.bars[0].visual)
        assertTrue(plan.bars.drop(1).all { it.visual == VisualHint.NONE })
    }

    @Test
    fun rotatingTargetMovesEachBar() {
        val c = simple(bars = 6).copy(appMode = AppMode.ADVANCED, targetMode = TargetMode.ROTATE)
        val plan = PlanBuilder.build(c, seed = 1)
        assertEquals(listOf(0, 1, 2, 3, 0, 1), plan.expected.map { it.beat })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), plan.expected.map { it.bar })
    }

    @Test
    fun fixedBackbeatTargets() {
        val c = simple().copy(appMode = AppMode.ADVANCED, targetBeats = setOf(1, 3))
        val plan = PlanBuilder.build(c, seed = 1)
        assertEquals(8, plan.expected.size)
        assertTrue(plan.expected.all { it.beat == 1 || it.beat == 3 })
    }

    @Test
    fun alternateBars() {
        val c = simple(bars = 8).copy(appMode = AppMode.ADVANCED, muteMode = MuteMode.ALTERNATE,
            alternateOnBars = 2, alternateOffBars = 2)
        val plan = PlanBuilder.build(c, seed = 1)
        val audible = plan.bars.drop(1).map { it.audible }
        assertEquals(listOf(true, true, false, false, true, true, false, false), audible)
    }

    @Test
    fun subdivisionAndPatternExpectations() {
        val sub = PlanBuilder.build(simple(bars = 1).copy(appMode = AppMode.ADVANCED,
            practiceType = PracticeType.SUBDIVISION, subdivision = 3), seed = 1)
        assertEquals(12, sub.expected.size)
        val pat = PlanBuilder.build(simple(bars = 2).copy(appMode = AppMode.ADVANCED,
            practiceType = PracticeType.PATTERN, patternId = "son_clave"), seed = 1)
        assertEquals(5, pat.expected.size)
        assertEquals(0.25, pat.pulseSec, 1e-9)
    }

    @Test
    fun durationInMinutes() {
        val c = simple().copy(durationType = DurationType.MINUTES, durationMinutes = 1)
        assertEquals(30, PlanBuilder.practiceBarCount(c)) // 120 BPM, 4/4 = 2 s pro Takt
    }

    @Test
    fun evaluatorPerfectAndLate() {
        val plan = PlanBuilder.build(simple(), seed = 1)
        val ev = TapEvaluator(plan)
        plan.expected.forEachIndexed { i, e ->
            val off = if (i % 2 == 0) 0.0 else 0.020
            val o = ev.onTap(e.timeSec + off)
            assertEquals(TapKind.HIT, o.kind)
        }
        val r = ev.finish()
        assertEquals(16, r.hits)
        assertEquals(1.0, r.hitRate, 1e-9)
        assertEquals(10.0, r.meanAbsMs, 1e-6)
        assertEquals(10.0, r.meanSignedMs, 1e-6)
        assertTrue(r.score > 85)
    }

    @Test
    fun evaluatorClassifiesTaps() {
        val plan = PlanBuilder.build(simple(), seed = 1) // Fenster 50 ms
        val ev = TapEvaluator(plan)
        val t0 = plan.expected[0].timeSec
        assertEquals(TapKind.IGNORED, ev.onTap(0.5).kind)            // Vorzaehlen
        assertEquals(TapKind.HIT, ev.onTap(t0 - 0.030).kind)         // 30 ms zu frueh
        assertEquals(TapKind.DOUBLE, ev.onTap(t0 + 0.010).kind)      // doppelt
        assertEquals(TapKind.OUTSIDE, ev.onTap(plan.expected[1].timeSec + 0.120).kind)
        val r = ev.finish()
        assertEquals(1, r.hits)
        assertEquals(2, r.extras)
        assertEquals(15, r.misses)
    }

    @Test
    fun extraTapsOnNonTargetBeats() {
        val c = simple().copy(appMode = AppMode.ADVANCED, targetBeats = setOf(0), countExtraTaps = true)
        val plan = PlanBuilder.build(c, seed = 1)
        val ev = TapEvaluator(plan)
        val start = plan.practiceStartSec
        assertEquals(TapKind.HIT, ev.onTap(start).kind)
        assertEquals(TapKind.EXTRA, ev.onTap(start + plan.beatSec).kind)
        val r = ev.finish()
        assertEquals(1, r.extras)
        // Strafe fuer Extra-Tipp: 1 Punkt auf 4 + 1 Nenner
        assertEquals(20.0, r.score, 1e-6)
    }

    @Test
    fun windowIsClampedForFastSubdivisions() {
        val c = simple(bpm = 240).copy(appMode = AppMode.ADVANCED, practiceType = PracticeType.SUBDIVISION,
            subdivision = 4, tolerance = Tolerance.EASY)
        val ev = TapEvaluator(PlanBuilder.build(c, seed = 1))
        assertTrue(ev.windowSec < 0.0625 * 0.5)
    }

    @Test
    fun driftDetected() {
        val plan = PlanBuilder.build(simple(bars = 30), seed = 1)
        val ev = TapEvaluator(plan)
        // Schleppt: jede Sekunde 0,5 ms spaeter
        plan.expected.forEach { ev.onTap(it.timeSec + (it.timeSec - plan.practiceStartSec) * 0.0005) }
        val r = ev.finish()
        assertNotNull(r.driftMsPerMin)
        assertEquals(30.0, r.driftMsPerMin!!, 0.5)
    }

    @Test
    fun earlyStopCountsOnlyPlayedBeats() {
        val plan = PlanBuilder.build(simple(bars = 10), seed = 1)
        val ev = TapEvaluator(plan)
        val r = ev.finish(stoppedAtSec = plan.practiceStartSec + 3.9)
        assertEquals(8, r.expected)
    }

    @Test
    fun calibrationMedian() {
        val beats = Calibration.beatTimes()
        val taps = beats.map { it + 0.040 }
        assertEquals(40.0, Calibration.latencyMs(beats, taps)!!, 1e-6)
        assertNull(Calibration.latencyMs(beats, taps.take(5)))
    }

    private fun summary(id: String, daysAgo: Int, score: Double, hitRate: Double, cfg: PracticeConfig = simple(),
                        signed: Double = 0.0, now: Long = 1_800_000_000_000L): SessionSummary {
        val perBeat = List(4) { Stat(10, (10 * hitRate).toInt(), 100.0, signed * 10) }
        return SessionSummary(id, now - daysAgo * 86_400_000L, cfg.effective(), cfg.comparisonKey(), true, 60.0,
            40, (40 * hitRate).toInt(), 40 - (40 * hitRate).toInt(), 0, hitRate, score, 10.0, signed, 5.0, null,
            perBeat, perBeat.fold(Stat()) { a, b -> a + b }, Stat())
    }

    @Test
    fun comparisonAndBest() {
        val hist = listOf(summary("a", 2, 70.0, 0.8), summary("b", 20, 60.0, 0.7), summary("c", 40, 90.0, 0.9))
        val cur = summary("d", 0, 80.0, 0.85)
        val cmp = Progress.compare(cur, hist)
        assertEquals(1, cmp.last7!!.count)
        assertEquals(2, cmp.last30!!.count)
        assertEquals(90.0, cmp.previousBest!!, 1e-9)
        assertFalse(cmp.isNewBest)
        assertTrue(Progress.compare(summary("e", 0, 95.0, 0.95), hist).isNewBest)
    }

    @Test
    fun suggestionsAfterStableSessions() {
        val hist = listOf(summary("a", 2, 90.0, 0.95), summary("b", 1, 88.0, 0.92))
        val cur = summary("c", 0, 91.0, 0.96)
        val s = Progress.suggestions(cur, hist)
        assertTrue(s.any { it is Suggestion.TempoUp && it.bpm == 125 })
        assertTrue(s.any { it is Suggestion.MoreSilence && it.muteMode == MuteMode.COUNT_IN_SILENT })
        assertTrue(s.none { it is Suggestion.TighterTolerance }) // nur im erweiterten Modus
        assertTrue(Progress.suggestions(summary("d", 0, 70.0, 0.8), hist).isEmpty())
    }

    @Test
    fun analysisFindsRushingAndTempoDrop() {
        val list = listOf(
            summary("a", 1, 80.0, 0.95, simple(bpm = 90), signed = -15.0),
            summary("b", 2, 80.0, 0.95, simple(bpm = 95), signed = -15.0),
            summary("c", 3, 60.0, 0.70, simple(bpm = 150), signed = -15.0),
            summary("d", 4, 60.0, 0.70, simple(bpm = 155), signed = -15.0),
        )
        val ins = Progress.analyze(list)
        assertTrue(ins.any { it is Insight.Rushing })
        assertTrue(ins.any { it is Insight.TempoDrop && it.fromBpm == 140 })
        assertEquals(listOf(Insight.NotEnoughData), Progress.analyze(list.take(2)))
    }

    @Test
    fun csvUsesGermanFormat() {
        val csv = CsvWriter().sessions(listOf(summary("a", 0, 81.25, 0.9)))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("81,3"))
        assertEquals(lines[0].count { it == ';' }, lines[1].count { it == ';' })
    }
}
