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
        assertEquals(Appearance(ThemeMode.SYSTEM, Appearances.ACCENTS.first { it.key == "teal" }, Density.COMFORTABLE), Appearances.decode("LIGHTISH;teal"))
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

    @Test
    fun aChartBarThatIsNotBeingReadStillReachesThreeToOneAgainstThePageForEveryAccentInLightDarkAndBlack() {
        fun over(fg: Int, bg: Int, alpha: Double): Int {
            fun ch(shift: Int) = (((fg shr shift) and 0xFF) * alpha + ((bg shr shift) and 0xFF) * (1 - alpha)).toInt()
            return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        for (a in Appearances.ACCENTS) for ((dark, page) in listOf(false to Appearances.LIGHT_BACKGROUND, true to Appearances.DARK_SURFACE, true to 0x000000)) {
            val bar = over(Appearances.primaryFor(a, dark), page, Appearances.IDLE_BAR_ALPHA)
            assertTrue(Appearances.contrast(bar, page) >= 3.0, "${a.key} dark=$dark page=${page.toString(16)}: ${Appearances.contrast(bar, page)}")
        }
    }

    @Test
    fun theAccentsAreTheBoardsFourAndTealIsTheDefault() {
        assertEquals(listOf("teal", "indigo", "plum", "ochre"), Appearances.ACCENTS.map { it.key })
        assertEquals(listOf(0x0E6B5C, 0x3949AB, 0x7B3F8C, 0x8A5A06), Appearances.ACCENTS.map { it.rgb })
        assertEquals(listOf(0x4CC2A6, 0x8C9EFF, 0xD29BE3, 0xE3B45A), Appearances.ACCENTS.map { it.darkRgb })
        assertEquals(listOf(0xE3F1EC, 0xE7E9F8, 0xF3E8F6, 0xF6EED8), Appearances.ACCENTS.map { it.soft })
        assertEquals(Appearances.ACCENTS.first(), Appearance().accent, "a fresh install is teal")
        for (a in Appearances.ACCENTS) for (dark in listOf(false, true)) assertEquals(if (dark) a.darkRgb else a.rgb, Appearances.primaryFor(a, dark), "${a.key} dark=$dark is the board's colour untouched")
    }

    @Test
    fun aSavedAccentOfEarlierVersionsBecomesTheNearestOfTheNewFourAndTheRestOfTheLineIsKept() {
        val expected = mapOf("violet" to "teal", "blue" to "indigo", "green" to "teal", "teal" to "teal", "orange" to "ochre", "red" to "ochre", "indigo" to "indigo", "plum" to "plum", "ochre" to "ochre", "wallpaper" to "wallpaper")
        for ((old, new) in expected) for (mode in ThemeMode.entries) for (d in Density.entries) for (black in listOf(false, true)) {
            val read = Appearances.decode("${mode.name};$old;${d.name}" + if (black) ";BLACK" else "")
            assertEquals(new, read.accent.key, "$old becomes $new")
            assertEquals(mode, read.mode); assertEquals(d, read.density); assertEquals(black, read.black)
            // read again after writing it back: the migration is stable and the line is the new key
            assertEquals(read, Appearances.decode(read.encode())); assertEquals(new, read.encode().split(';')[1])
        }
        assertEquals(Appearances.WALLPAPER, Appearances.decode("DARK;wallpaper;COMPACT").accent, "wallpaper is unchanged")
        for (junk in listOf("chartreuse", "", "Teal", "VIOLET2")) assertEquals(Appearances.ACCENTS.first(), Appearances.decode("LIGHT;$junk;COMFORTABLE").accent, "unknown '$junk' is the default")
        assertEquals(Appearances.ACCENTS.first(), Appearances.decode("LIGHT").accent); assertEquals(Appearances.ACCENTS.first(), Appearances.decode(null).accent)
        assertEquals(setOf("violet", "blue", "green", "orange", "red"), Appearances.MIGRATED.keys, "every accent of 0.16 that is gone has a target")
        assertTrue(Appearances.MIGRATED.values.all { v -> Appearances.ACCENTS.any { it.key == v } }, "and the target exists")
    }

    @Test
    fun theSignalColoursAreTheBoardsAndReadOnTheirOwnFillsAndOnTheCards() {
        assertEquals(listOf(0xA34A06, 0xF2A65A), listOf(Signals.watch(false), Signals.watch(true)))
        assertEquals(listOf(0xB42318, 0xF28B82), listOf(Signals.over(false), Signals.over(true)))
        assertEquals(listOf(0xFBEBD9, 0x3A2A17), listOf(Signals.watchSoft(false), Signals.watchSoft(true)))
        assertEquals(listOf(0xFCE8E6, 0x3D1A17), listOf(Signals.overSoft(false), Signals.overSoft(true)))
        for (dark in listOf(false, true)) {
            val card = if (dark) ThemeRoles.DARK.container else ThemeRoles.LIGHT.container
            assertTrue(Appearances.contrast(Signals.watch(dark), card) >= 4.5 && Appearances.contrast(Signals.over(dark), card) >= 4.5, "dark=$dark on a card")
            assertTrue(Appearances.contrast(Signals.watch(dark), Signals.watchSoft(dark)) >= 4.5, "watch on its fill dark=$dark")
            assertTrue(Appearances.contrast(Signals.over(dark), Signals.overSoft(dark)) >= 4.5, "over on its fill dark=$dark")
        }
    }

    @Test
    fun everyAccentsTextOnItsOwnSoftFillAndOnTheGroundReadsInLightDarkAndBlack() {
        for (a in Appearances.ACCENTS) {
            val light = Appearances.primaryFor(a, false); val dark = Appearances.primaryFor(a, true)
            assertTrue(Appearances.contrast(light, ThemeRoles.LIGHT.background) >= 4.5, "${a.key} on the light ground")
            assertTrue(Appearances.contrast(light, a.soft!!) >= 4.5, "${a.key} on its light soft fill ${Appearances.contrast(light, a.soft!!)}")
            for (g in listOf(ThemeRoles.DARK, ThemeRoles.BLACK)) assertTrue(Appearances.contrast(dark, g.background) >= 4.5, "${a.key} on the dark ground")
            assertTrue(Appearances.contrast(dark, a.softDark!!) >= 4.5, "${a.key} on its dark soft fill ${Appearances.contrast(dark, a.softDark!!)}")
            assertTrue(Appearances.contrast(Appearances.onColor(light), light) >= 4.5 && Appearances.contrast(Appearances.onColor(dark), dark) >= 4.5, "${a.key} text on the accent")
        }
    }
}
