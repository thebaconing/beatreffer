package de.simon.beatreffer.data

import de.simon.beatreffer.core.AppMode
import de.simon.beatreffer.core.ClickSound
import de.simon.beatreffer.core.DurationType
import de.simon.beatreffer.core.MuteMode
import de.simon.beatreffer.core.PracticeConfig
import de.simon.beatreffer.core.PracticeType
import de.simon.beatreffer.core.SessionSummary
import de.simon.beatreffer.core.Stat
import de.simon.beatreffer.core.TargetMode
import de.simon.beatreffer.core.TimeSignature
import de.simon.beatreffer.core.Tolerance
import de.simon.beatreffer.core.VisualHint
import org.json.JSONArray
import org.json.JSONObject

/** Umwandlung der Datenmodelle in JSON und zurueck. Unbekannte Werte fallen auf Standards zurueck. */
object JsonMapping {

    private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, def: E): E =
        optString(key, def.name).let { name -> enumValues<E>().firstOrNull { it.name == name } ?: def }

    fun configToJson(c: PracticeConfig): JSONObject = JSONObject().apply {
        put("appMode", c.appMode.name)
        put("bpm", c.bpm)
        put("tsNum", c.timeSignature.numerator)
        put("tsDen", c.timeSignature.denominator)
        put("tsGroup", JSONArray(c.timeSignature.grouping))
        put("practiceType", c.practiceType.name)
        put("subdivision", c.subdivision)
        put("subdivisionAudible", c.subdivisionAudible)
        put("patternId", c.patternId)
        put("targetMode", c.targetMode.name)
        put("targetBeats", JSONArray(c.targetBeats.sorted()))
        put("muteMode", c.muteMode.name)
        put("altOn", c.alternateOnBars)
        put("altOff", c.alternateOffBars)
        put("gapPercent", c.gapPercent)
        put("countInBars", c.countInBars)
        put("durationType", c.durationType.name)
        put("durationMinutes", c.durationMinutes)
        put("durationBars", c.durationBars)
        put("tolerance", c.tolerance.name)
        put("countExtraTaps", c.countExtraTaps)
        put("liveFeedback", c.liveFeedback)
        put("visualHint", c.visualHint.name)
        put("sound", c.sound.name)
    }

    fun configFromJson(o: JSONObject): PracticeConfig {
        val d = PracticeConfig()
        val num = o.optInt("tsNum", 4).coerceIn(1, 16)
        val den = o.optInt("tsDen", 4).let { if (it in setOf(2, 4, 8, 16)) it else 4 }
        val groupArr = o.optJSONArray("tsGroup")
        val group = if (groupArr == null) null else List(groupArr.length()) { groupArr.optInt(it) }
        val ts = if (group != null && group.all { it > 0 } && group.sum() == num) TimeSignature(num, den, group)
        else TimeSignature.of(num, den)
        val targetsArr = o.optJSONArray("targetBeats")
        val targets = if (targetsArr == null) emptySet() else List(targetsArr.length()) { targetsArr.optInt(it) }.toSet()
        return PracticeConfig(
            appMode = o.enumOr("appMode", d.appMode),
            bpm = o.optInt("bpm", d.bpm).coerceIn(PracticeConfig.MIN_BPM, PracticeConfig.MAX_BPM),
            timeSignature = ts,
            practiceType = o.enumOr("practiceType", d.practiceType),
            subdivision = o.optInt("subdivision", d.subdivision).coerceIn(2, 4),
            subdivisionAudible = o.optBoolean("subdivisionAudible", d.subdivisionAudible),
            patternId = o.optString("patternId", d.patternId),
            targetMode = o.enumOr("targetMode", d.targetMode),
            targetBeats = targets,
            muteMode = o.enumOr("muteMode", d.muteMode),
            alternateOnBars = o.optInt("altOn", d.alternateOnBars).coerceIn(1, 8),
            alternateOffBars = o.optInt("altOff", d.alternateOffBars).coerceIn(1, 8),
            gapPercent = o.optInt("gapPercent", d.gapPercent).coerceIn(10, 70),
            countInBars = o.optInt("countInBars", d.countInBars).coerceIn(1, 4),
            durationType = o.enumOr("durationType", d.durationType),
            durationMinutes = o.optInt("durationMinutes", d.durationMinutes).coerceIn(1, 60),
            durationBars = o.optInt("durationBars", d.durationBars).coerceIn(4, 512),
            tolerance = o.enumOr("tolerance", d.tolerance),
            countExtraTaps = o.optBoolean("countExtraTaps", d.countExtraTaps),
            liveFeedback = o.optBoolean("liveFeedback", d.liveFeedback),
            visualHint = o.enumOr("visualHint", d.visualHint),
            sound = o.enumOr("sound", d.sound),
        )
    }

    private fun statToJson(s: Stat) = JSONArray().put(s.expected).put(s.hits).put(s.sumAbsMs).put(s.sumSignedMs)
    private fun statFromJson(a: JSONArray?): Stat =
        if (a == null) Stat() else Stat(a.optInt(0), a.optInt(1), a.optDouble(2, 0.0), a.optDouble(3, 0.0))

    fun summaryToJson(s: SessionSummary): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("ts", s.timestampMillis)
        put("config", configToJson(s.config))
        put("key", s.configKey)
        put("completed", s.completed)
        put("played", s.playedSec)
        put("expected", s.expected)
        put("hits", s.hits)
        put("misses", s.misses)
        put("extras", s.extras)
        put("hitRate", s.hitRate)
        put("score", s.score)
        put("meanAbs", s.meanAbsMs)
        put("meanSigned", s.meanSignedMs)
        put("std", s.stdDevMs)
        s.driftMsPerMin?.let { put("drift", it) }
        put("perBeat", JSONArray().apply { s.perBeat.forEach { put(statToJson(it)) } })
        put("audible", statToJson(s.audible))
        put("silent", statToJson(s.silent))
    }

    fun summaryFromJson(o: JSONObject): SessionSummary {
        val pb = o.optJSONArray("perBeat")
        val config = configFromJson(o.optJSONObject("config") ?: JSONObject())
        return SessionSummary(
            id = o.getString("id"),
            timestampMillis = o.getLong("ts"),
            config = config,
            configKey = o.optString("key", config.comparisonKey()),
            completed = o.optBoolean("completed", true),
            playedSec = o.optDouble("played", 0.0),
            expected = o.optInt("expected"),
            hits = o.optInt("hits"),
            misses = o.optInt("misses"),
            extras = o.optInt("extras"),
            hitRate = o.optDouble("hitRate", 0.0),
            score = o.optDouble("score", 0.0),
            meanAbsMs = o.optDouble("meanAbs", 0.0),
            meanSignedMs = o.optDouble("meanSigned", 0.0),
            stdDevMs = o.optDouble("std", 0.0),
            driftMsPerMin = if (o.has("drift")) o.optDouble("drift") else null,
            perBeat = if (pb == null) emptyList() else List(pb.length()) { statFromJson(pb.optJSONArray(it)) },
            audible = statFromJson(o.optJSONArray("audible")),
            silent = statFromJson(o.optJSONArray("silent")),
        )
    }
}
