package de.thebaconing.beatreffer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollThumbTest {
    @Test fun nothingToScroll() {
        assertNull(scrollThumb(0.0, 0.0, 800.0))
    }

    @Test fun heightIsVisibleShare() {
        // Inhalt doppelt so hoch wie sichtbar: Anzeiger halb so hoch
        val t = scrollThumb(0.0, 800.0, 800.0)!!
        assertEquals(0.5, t.height, 1e-9)
        assertEquals(0.0, t.top, 1e-9)
    }

    @Test fun bottomReachesEnd() {
        val t = scrollThumb(800.0, 800.0, 800.0)!!
        assertEquals(1.0, t.top + t.height, 1e-9)
    }

    @Test fun minimumHeightAndClamping() {
        val t = scrollThumb(1e6, 100_000.0, 500.0)!!
        assertEquals(0.08, t.height, 1e-9)
        assertEquals(0.92, t.top, 1e-9)
    }
}
