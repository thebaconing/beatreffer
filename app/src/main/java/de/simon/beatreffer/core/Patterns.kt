package de.simon.beatreffer.core

enum class PatternLevel { EASY, MEDIUM, HARD }

/**
 * Ein Rhythmus-Muster als Raster.
 * [grid]: 'x' = Anschlag, '.' = Pause. Leerzeichen und '|' werden ignoriert.
 * [stepsPerBeat]: Rasterpunkte pro Schlag (Zaehlzeit des Nenners).
 * Die Rasterlaenge muss bars * numerator * stepsPerBeat sein.
 */
data class RhythmPattern(
    val id: String,
    val nameDe: String,
    val nameEn: String,
    val level: PatternLevel,
    val timeSignature: TimeSignature,
    val stepsPerBeat: Int,
    val grid: String,
) {
    val cleanGrid: String = grid.filter { it == 'x' || it == '.' }
    val stepsPerBar: Int = timeSignature.numerator * stepsPerBeat
    val bars: Int = cleanGrid.length / stepsPerBar

    init {
        require(cleanGrid.isNotEmpty() && cleanGrid.length % stepsPerBar == 0) {
            "Muster $id: Rasterlaenge ${cleanGrid.length} passt nicht zu $stepsPerBar Schritten pro Takt"
        }
        require('x' in cleanGrid) { "Muster $id hat keine Anschlaege" }
    }

    /** Anschlaege eines Takts (barInPattern 0-basiert) als Rasterschritt im Takt. */
    fun hitsInBar(barInPattern: Int): List<Int> {
        val offset = (barInPattern % bars) * stepsPerBar
        return (0 until stepsPerBar).filter { cleanGrid[offset + it] == 'x' }
    }

    fun name(german: Boolean) = if (german) nameDe else nameEn
}

object Patterns {
    private val FOUR = TimeSignature.of(4, 4)

    val ALL: List<RhythmPattern> = listOf(
        // Leicht
        RhythmPattern("half", "Halbe Noten", "Half notes", PatternLevel.EASY, FOUR, 1, "x.x."),
        RhythmPattern("quarter_rest", "Viertel mit Pause", "Quarters with rest", PatternLevel.EASY, FOUR, 1, "xx.x"),
        RhythmPattern("eighth_pairs", "Achtel-Paare", "Eighth pairs", PatternLevel.EASY, FOUR, 2, "x. xx x. xx"),
        RhythmPattern("waltz", "Walzer", "Waltz", PatternLevel.EASY, TimeSignature.of(3, 4), 2, "x. x. x."),
        // Mittel
        RhythmPattern("offbeats", "Offbeats", "Offbeats", PatternLevel.MEDIUM, FOUR, 2, ".x .x .x .x"),
        RhythmPattern("charleston", "Charleston", "Charleston", PatternLevel.MEDIUM, FOUR, 2, "x. .x .. .."),
        RhythmPattern("tresillo", "Tresillo", "Tresillo", PatternLevel.MEDIUM, FOUR, 2, "x. .x .. x."),
        RhythmPattern("son_clave", "Son-Clave 3-2", "Son clave 3-2", PatternLevel.MEDIUM, FOUR, 2,
            "x. .x .. x. | .. x. x. .."),
        RhythmPattern("shuffle", "Triolen-Shuffle", "Triplet shuffle", PatternLevel.MEDIUM, FOUR, 3,
            "x.x x.x x.x x.x"),
        // Schwer
        RhythmPattern("syncopation", "Synkopen-Kette", "Syncopation chain", PatternLevel.HARD, FOUR, 2,
            "x. .x .x .x"),
        RhythmPattern("funk16", "Sechzehntel-Funk", "Sixteenth funk", PatternLevel.HARD, FOUR, 4,
            "x..x ..x. x.x. .x.."),
        RhythmPattern("five_four", "5/4 (3+2)", "5/4 (3+2)", PatternLevel.HARD, TimeSignature(5, 4, listOf(3, 2)), 2,
            "x. .x x. x. .x"),
        RhythmPattern("balkan_78", "Balkan 7/8 (2+2+3)", "Balkan 7/8 (2+2+3)", PatternLevel.HARD,
            TimeSignature(7, 8, listOf(2, 2, 3)), 1, "x.xxx.x"),
        RhythmPattern("quarter_triplets", "Viertel-Triolen", "Quarter-note triplets", PatternLevel.HARD, FOUR, 3,
            "x.x .x. x.x .x."),
    )

    fun byId(id: String): RhythmPattern = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
