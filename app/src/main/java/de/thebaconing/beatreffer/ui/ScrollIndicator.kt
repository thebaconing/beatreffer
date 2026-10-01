package de.thebaconing.beatreffer.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.thebaconing.beatreffer.core.scrollThumb

/**
 * Ersatz für verticalScroll(rememberScrollState()) mit Anzeige der Scrollposition:
 * Balken am rechten Rand (Lage und Länge = Position und sichtbarer Anteil) und
 * Schatten oben/unten, solange in die Richtung noch Inhalt kommt.
 */
fun Modifier.verticalScrollWithIndicator(): Modifier = composed {
    val state = rememberScrollState()
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fadeColor = MaterialTheme.colorScheme.background
    this
        .drawWithContent {
            drawContent()
            val thumb = scrollThumb(state.value.toDouble(), state.maxValue.toDouble(), size.height.toDouble())
                ?: return@drawWithContent
            val fade = 24.dp.toPx()
            if (state.value > 0) {
                drawRect(Brush.verticalGradient(listOf(fadeColor, Color.Transparent), 0f, fade), size = Size(size.width, fade))
            }
            if (state.value < state.maxValue) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, fadeColor), size.height - fade, size.height),
                    topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade),
                )
            }
            val w = 4.dp.toPx()
            val x = size.width - w - 2.dp.toPx()
            val r = CornerRadius(w / 2)
            drawRoundRect(trackColor, Offset(x, 0f), Size(w, size.height), r)
            drawRoundRect(
                thumbColor,
                Offset(x, (thumb.top * size.height).toFloat()),
                Size(w, (thumb.height * size.height).toFloat()),
                r,
            )
        }
        .verticalScroll(state)
}
