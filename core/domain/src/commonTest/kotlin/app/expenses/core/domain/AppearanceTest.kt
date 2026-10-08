package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceTest {
    @Test
    fun aSavedLookRoundTripsAndAnythingUnknownFallsBackPartByPart() {
        for (m in ThemeMode.entries) for (a in Appearances.ACCENTS) for (d in Density.entries) { val x = Appearance(m, a, d); assertEquals(x, Appearances.decode(x.encode())) }
        assertEquals(Appearance(), Appearances.decode(null)); assertEquals(Appearance(), Appearances.decode("")); assertEquals(Appearance(), Appearances.decode("junk;;;;"))
        assertEquals(Appearance(ThemeMode.DARK, Appearances.ACCENTS.first(), Density.COMPACT), Appearances.decode("DARK;purple-from-the-future;COMPACT"))
        assertEquals(Appearance(ThemeMode.SYSTEM, Appearances.ACCENTS[2], Density.COMFORTABLE), Appearances.decode("LIGHTISH;teal"))
    }

    @Test
    fun darkFollowsTheModeAndOnlySystemLooksAtThePhone() {
        for (sys in listOf(true, false)) { assertEquals(sys, Appearances.isDark(ThemeMode.SYSTEM, sys)); assertEquals(false, Appearances.isDark(ThemeMode.LIGHT, sys)); assertEquals(true, Appearances.isDark(ThemeMode.DARK, sys)) }
    }

    @Test
    fun contrastMatchesTheKnownWcagValues() {
        assertEquals(21.0, Appearances.contrast(0xFFFFFF, 0x000000), 0.001); assertEquals(1.0, Appearances.contrast(0x777777, 0x777777), 0.001)
        assertEquals(4.48, Appearances.contrast(0x777777, 0xFFFFFF), 0.02, "the classic #777 on white")
        assertEquals(0.0722 * (1.0 / 255) / 12.92, Appearances.luminance(0x000001), 1e-9, "the linear part of the formula for very dark channels")
        assertEquals(0xFFFFFF, Appearances.onColor(0x000000)); assertEquals(0x000000, Appearances.onColor(0xFFFFFF)); assertEquals(0x000000, Appearances.onColor(0xFFEB3B))
    }

    @Test
    fun everyAccentIsReadableOnWhiteInLightAndOnTheDarkSurfaceInDarkAndTextOnItReads() {
        for (a in Appearances.ACCENTS) {
            assertTrue(Appearances.contrast(a.rgb, 0xFFFFFF) >= 4.5, "${a.key} as text on white: ${Appearances.contrast(a.rgb, 0xFFFFFF)}")
            val dark = Appearances.primaryFor(a, true)
            assertTrue(Appearances.contrast(dark, Appearances.DARK_SURFACE) >= 4.5, "${a.key} on the dark surface: ${Appearances.contrast(dark, Appearances.DARK_SURFACE)}")
            for (p in listOf(a.rgb, dark)) assertTrue(Appearances.contrast(p, Appearances.onColor(p)) >= 4.5, "${a.key} button text")
            assertEquals(a.rgb, Appearances.primaryFor(a, false))
        }
        for (a in Appearances.ACCENTS) for (dark in listOf(false, true)) { val p = Appearances.primaryFor(a, dark); val c = Appearances.containerFor(p, dark); assertTrue(Appearances.contrast(c, Appearances.onColor(c)) >= 7.0, "${a.key} dark=$dark: text on the selected-chip background") }
        assertEquals(Appearances.ACCENTS.size, Appearances.ACCENTS.map { it.key }.toSet().size)
    }
}
