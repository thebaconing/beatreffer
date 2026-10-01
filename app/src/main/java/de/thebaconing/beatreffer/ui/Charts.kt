package de.thebaconing.beatreffer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Einfaches Liniendiagramm ueber Tage. Luecken zwischen Tagen werden
 * maszstabsgetreu dargestellt.
 */
@Composable
fun DayLineChart(
    points: List<Pair<LocalDate, Double>>,
    color: Color,
    fixedMin: Double? = null,
    fixedMax: Double? = null,
    unit: String = "",
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val first = points.first().first
    val last = points.last().first
    val spanDays = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val rawMin = points.minOf { it.second }
    val rawMax = points.maxOf { it.second }
    val yMin = fixedMin ?: floor((rawMin - 5) / 10) * 10
    val yMax = (fixedMax ?: ceil((rawMax + 5) / 10) * 10).let { if (it <= yMin) yMin + 10 else it }
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.")

    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val left = 40.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            fun x(d: LocalDate): Float =
                if (points.size == 1) (left + right) / 2
                else left + (right - left) * ChronoUnit.DAYS.between(first, d).toFloat() / spanDays
            fun y(v: Double): Float = (bottom - (bottom - top) * ((v - yMin) / (yMax - yMin))).toFloat()

            // Gitter und Achsenbeschriftung
            for (i in 0..2) {
                val v = yMin + (yMax - yMin) * i / 2
                val yy = y(v)
                drawLine(gridColor, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
                val layout = measurer.measure("${fmt(v)}$unit", TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(layout, topLeft = Offset(0f, yy - layout.size.height / 2))
            }

            val path = Path()
            points.forEachIndexed { i, (d, v) ->
                val p = Offset(x(d), y(v))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (points.size > 1) drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            points.forEach { (d, v) -> drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(d), y(v))) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(first.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = labelColor,
                modifier = Modifier.weight(1f))
            if (last != first) Text(last.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}
