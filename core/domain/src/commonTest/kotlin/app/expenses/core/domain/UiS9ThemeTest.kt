package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI S9: the true-black theme, the wallpaper accent, the high-contrast roles and the large-window rules. Pure; no screen. */
class UiS9ThemeTest {
    @Test
    fun theBlackThemeAndTheWallpaperAccentAreStoredInTheOneAppearanceLineAndOldLinesReadAsBefore() {
        val a = Appearance(ThemeMode.DARK, Appearances.WALLPAPER, Density.COMPACT, black = true)
        assertEquals("DARK;wallpaper;COMPACT;BLACK", a.encode())
        assertEquals(a, Appearances.decode(a.encode()))
        assertEquals("SYSTEM;violet;COMFORTABLE", Appearance().encode(), "without the black theme the line is exactly what older versions wrote")
        assertFalse(Appearances.decode("DARK;teal;COMFORTABLE").black, "a line from before UI S9 is not black")
        assertEquals(Appearances.ACCENTS[2], Appearances.decode("DARK;teal;COMFORTABLE;").accent)
        for (m in ThemeMode.entries) for (black in listOf(false, true)) for (acc in Appearances.ACCENTS + Appearances.WALLPAPER) {
            val x = Appearance(m, acc, Density.COMFORTABLE, black); assertEquals(x, Appearances.decode(x.encode()))
        }
        // what an older app does with a newer line: it splits on ';' and reads the first three fields; an unknown accent falls back to its first
        val oldReading = "LIGHT;wallpaper;COMPACT;BLACK".split(';').take(3)
        assertEquals(listOf("LIGHT", "wallpaper", "COMPACT"), oldReading)
    }

    @Test
    fun theWallpaperAccentIsThePhonesColourAndVioletWhereThePhoneHasNone() {
        assertEquals(0x3366AA, Appearances.resolve(Appearances.WALLPAPER, 0xFF3366AA.toInt()).rgb, "the alpha of an ARGB colour is dropped")
        assertEquals(Appearances.ACCENTS.first(), Appearances.resolve(Appearances.WALLPAPER, null), "an older Android or the iPhone: the default violet")
        assertEquals(Appearances.ACCENTS[3], Appearances.resolve(Appearances.ACCENTS[3], 0x123456), "a chosen accent ignores the wallpaper")
    }

    @Test
    fun anyWallpaperColourBecomesAPrimaryThatReadsOnEveryBackground() {
        val samples = listOf(0xFFEB3B, 0xFFFFFF, 0x80DEEA, 0xF8BBD0, 0x000000, 0x1A1A1A, 0x0D47A1, 0x7CB342, 0xFF6D00, 0x9E9E9E)
        for (rgb in samples) {
            val acc = Appearances.resolve(Appearances.WALLPAPER, rgb)
            val light = Appearances.primaryFor(acc, dark = false)
            assertTrue(Appearances.contrast(light, Appearances.LIGHT_BACKGROUND) >= 4.5, "light ${rgb.toString(16)} -> ${light.toString(16)}")
            val dark = Appearances.primaryFor(acc, dark = true)
            assertTrue(Appearances.contrast(dark, Appearances.DARK_SURFACE) >= 4.5, "dark ${rgb.toString(16)} -> ${dark.toString(16)}")
            assertTrue(Appearances.contrast(dark, Appearances.BLACK_SURFACE) >= 4.5, "black ${rgb.toString(16)} -> ${dark.toString(16)}")
            for (d in listOf(false, true)) for (black in listOf(false, true)) {
                val p = Appearances.primaryFor(acc, d); val c = Appearances.containerFor(p, d, black)
                assertTrue(Appearances.contrast(Appearances.onColor(p), p) >= 4.5 && Appearances.contrast(Appearances.onColor(c), c) >= 4.5, "text on ${rgb.toString(16)} dark=$d black=$black")
            }
        }
        for (a in Appearances.ACCENTS) assertEquals(a.rgb, Appearances.primaryFor(a, false), "the six offered accents are not changed in light mode")
    }

    @Test
    fun nothingChangesForAnyoneWhoChoseNeitherBlackNorHighContrast() {
        assertNull(ThemeRoles.surfaces(dark = false, black = false, highContrast = false))
        assertNull(ThemeRoles.surfaces(dark = true, black = false, highContrast = false))
        assertNull(ThemeRoles.surfaces(dark = false, black = true, highContrast = false), "black changes nothing while the app is light")
    }

    @Test
    fun trueBlackIsBlackAndItsTextAndOutlinesStillRead() {
        val r = ThemeRoles.surfaces(dark = true, black = true, highContrast = false)!!
        assertEquals(0x000000, r.background); assertEquals(0x000000, r.surface); assertEquals(0x000000, r.containerLowest)
        assertTrue(Appearances.contrast(r.onSurface, r.background) >= 7.0)
        assertTrue(Appearances.contrast(r.onSurfaceVariant, r.background) >= 4.5)
        assertTrue(Appearances.contrast(r.onSurfaceVariant, r.surfaceVariant) >= 4.5, "secondary text on a card")
        assertTrue(Appearances.contrast(r.outline, r.background) >= 3.0)
        val steps = listOf(r.containerLowest, r.containerLow, r.container, r.containerHigh, r.containerHighest).map { Appearances.luminance(it) }
        assertEquals(steps.sorted(), steps, "raised surfaces get lighter step by step, so they separate from the black screen")
    }

    @Test
    fun highContrastMakesSecondaryTextAsStrongAsBodyTextAndOutlinesVisibleInEveryTheme() {
        for (dark in listOf(false, true)) for (black in listOf(false, true)) {
            val r = ThemeRoles.surfaces(dark, black, highContrast = true)!!
            val where = "dark=$dark black=$black"
            assertTrue(Appearances.contrast(r.onSurfaceVariant, r.background) >= 7.0, "$where secondary text ${Appearances.contrast(r.onSurfaceVariant, r.background)}")
            assertTrue(Appearances.contrast(r.onSurfaceVariant, r.surfaceVariant) >= 4.5, "$where secondary text on a card")
            assertTrue(Appearances.contrast(r.outline, r.background) >= 4.5, "$where outline")
            assertEquals(r.outline, r.outlineVariant, "$where no faint outlines")
        }
        // the baseline roles themselves: secondary text reads at 4.5:1 in both themes (what high contrast starts from)
        for (r in listOf(ThemeRoles.LIGHT, ThemeRoles.DARK)) assertTrue(Appearances.contrast(r.onSurfaceVariant, r.background) >= 4.5)
    }

    @Test
    fun theRailAndTheAddPaneFollowTheTwoPaneWindowAndTheBookFoldKeepsTheBar() {
        val fold = WindowLayout.of(690, 829); val phone = WindowLayout.of(393, 851); val landscape = WindowLayout.of(851, 393); val tablet = WindowLayout.of(864, 600)
        assertTrue(WindowLayout.usesRail(fold, verticalHinge = false)); assertTrue(WindowLayout.usesRail(tablet, false))
        assertFalse(WindowLayout.usesRail(phone, false)); assertFalse(WindowLayout.usesRail(landscape, false), "a phone on its side keeps the bar")
        assertFalse(WindowLayout.usesRail(fold, verticalHinge = true), "half open like a book: the panes sit either side of the hinge and the bar stays")
        assertTrue(WindowLayout.usesRail(WindowLayout.of(600, 480), false), "from 600 dp")
        assertFalse(WindowLayout.usesRail(WindowLayout.of(599, 900), false))
        assertTrue(WindowLayout.addBesideList(fold, adding = true, otherScreenOpen = false))
        assertFalse(WindowLayout.addBesideList(fold, adding = true, otherScreenOpen = true), "Add opened over another screen (a loan, a bill) covers it, as before")
        assertFalse(WindowLayout.addBesideList(phone, adding = true, otherScreenOpen = false))
        assertFalse(WindowLayout.addBesideList(fold, adding = false, otherScreenOpen = false))
        assertEquals(480, WindowLayout.PAD_MAX_WIDTH_DP); assertEquals(80, WindowLayout.RAIL_WIDTH_DP)
    }
}
