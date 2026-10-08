package app.expenses.core.domain

import kotlin.math.pow

enum class ThemeMode(val label: String) { SYSTEM("Follow the phone"), LIGHT("Light"), DARK("Dark") }
enum class Density(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

/** An accent colour offered in settings, as 0xRRGGBB. */
data class Accent(val key: String, val label: String, val rgb: Int)

/** How the app looks: light or dark (or the phone's choice), an accent colour, and how tightly lists are spaced. Stored as one line of text. */
data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Appearances.ACCENTS.first(), val density: Density = Density.COMFORTABLE) {
    fun encode() = "${mode.name};${accent.key};${density.name}"
}

object Appearances {
    val ACCENTS = listOf(
        Accent("violet", "Violet", 0x6750A4), Accent("blue", "Blue", 0x1565C0), Accent("teal", "Teal", 0x00796B),
        Accent("green", "Green", 0x2E7D32), Accent("orange", "Orange", 0xBF4F00), Accent("red", "Red", 0xB3261E),
    )

    /** Reads the stored text; anything missing or unknown falls back to the default for that part, so a bad value never breaks the screen. */
    fun decode(text: String?): Appearance {
        val p = text.orEmpty().split(';')
        return Appearance(
            ThemeMode.entries.firstOrNull { it.name == p.getOrNull(0) } ?: ThemeMode.SYSTEM,
            ACCENTS.firstOrNull { it.key == p.getOrNull(1) } ?: ACCENTS.first(),
            Density.entries.firstOrNull { it.name == p.getOrNull(2) } ?: Density.COMFORTABLE,
        )
    }

    fun isDark(mode: ThemeMode, systemDark: Boolean) = when (mode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }

    private fun channel(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }

    /** WCAG relative luminance of 0xRRGGBB. */
    fun luminance(rgb: Int) = 0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    /** WCAG contrast ratio between two colours (1 to 21). */
    fun contrast(a: Int, b: Int): Double { val x = luminance(a); val y = luminance(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Int): Int = if (contrast(background, 0xFFFFFF) >= contrast(background, 0x000000)) 0xFFFFFF else 0x000000

    /** The accent as used for the primary colour: itself in light mode; lightened in dark mode until text on a dark surface reads at 4.5:1 or better. */
    fun primaryFor(accent: Accent, dark: Boolean): Int {
        if (!dark) return accent.rgb
        var c = accent.rgb
        var step = 0
        while (contrast(c, DARK_SURFACE) < 4.5 && step < 20) { c = mix(c, 0xFFFFFF, 0.12); step++ }
        return c
    }

    const val DARK_SURFACE = 0x141218
    private const val LIGHT_SURFACE = 0xFFFFFF

    /** The soft background used behind selected chips and cards: the primary colour thinned into the surface. */
    fun containerFor(primary: Int, dark: Boolean): Int = if (dark) mix(DARK_SURFACE, primary, 0.30) else mix(LIGHT_SURFACE, primary, 0.14)

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
