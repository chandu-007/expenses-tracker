package app.expenses.core.domain

import kotlin.math.pow

enum class ThemeMode(val label: String) { SYSTEM("Follow the phone"), LIGHT("Light"), DARK("Dark") }
enum class Density(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

/** An accent colour offered in settings, as 0xRRGGBB. */
data class Accent(val key: String, val label: String, val rgb: Int)

/**
 * How the app looks: light or dark (or the phone's choice), an accent colour, and how tightly lists are spaced. Stored as one line of text (the setting `appearance`, which backups carry).
 * UI S9 (D18): [black] draws the dark theme on true black (OLED); it changes nothing while the app is light. The accent may be [Appearances.WALLPAPER] (Android 12 and later: the phone's own colour).
 */
data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Appearances.ACCENTS.first(), val density: Density = Density.COMFORTABLE, val black: Boolean = false) {
    /** The three old fields exactly as before, so an older app reading a newer backup keeps the mode, the accent (an unknown one falls back to violet) and the spacing; the black theme is a fourth field it ignores. */
    fun encode() = "${mode.name};${accent.key};${density.name}" + if (black) ";BLACK" else ""
}

object Appearances {
    val ACCENTS = listOf(
        Accent("violet", "Violet", 0x6750A4), Accent("blue", "Blue", 0x1565C0), Accent("teal", "Teal", 0x00796B),
        Accent("green", "Green", 0x2E7D32), Accent("orange", "Orange", 0xBF4F00), Accent("red", "Red", 0xB3261E),
    )

    /**
     * UI S9 (D18): "Match my wallpaper": the phone's own accent (Android 12 and later, from the system's dynamic colours). Its [Accent.rgb] is only the fallback (violet) for where the phone has none
     * (an older Android, the iPhone, a backup restored on such a phone); the real colour comes from [resolve]. Money colours (green in, red over) never follow it.
     */
    val WALLPAPER = Accent("wallpaper", "Wallpaper", 0x6750A4)

    /** Reads the stored text; anything missing or unknown falls back to the default for that part, so a bad value never breaks the screen. */
    fun decode(text: String?): Appearance {
        val p = text.orEmpty().split(';')
        return Appearance(
            ThemeMode.entries.firstOrNull { it.name == p.getOrNull(0) } ?: ThemeMode.SYSTEM,
            (ACCENTS + WALLPAPER).firstOrNull { it.key == p.getOrNull(1) } ?: ACCENTS.first(),
            Density.entries.firstOrNull { it.name == p.getOrNull(2) } ?: Density.COMFORTABLE,
            black = p.getOrNull(3) == "BLACK",
        )
    }

    /** The accent to draw: the chosen one, or for [WALLPAPER] the phone's colour [systemRgb] (0xRRGGBB), or the default violet where the phone has none. */
    fun resolve(accent: Accent, systemRgb: Int?): Accent = if (accent.key != WALLPAPER.key) accent else if (systemRgb != null) accent.copy(rgb = systemRgb and 0xFFFFFF) else ACCENTS.first()

    fun isDark(mode: ThemeMode, systemDark: Boolean) = when (mode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }

    private fun channel(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }

    /** WCAG relative luminance of 0xRRGGBB. */
    fun luminance(rgb: Int) = 0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    /** WCAG contrast ratio between two colours (1 to 21). */
    fun contrast(a: Int, b: Int): Double { val x = luminance(a); val y = luminance(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Int): Int = if (contrast(background, 0xFFFFFF) >= contrast(background, 0x000000)) 0xFFFFFF else 0x000000

    /**
     * The accent as used for the primary colour: in light mode itself, darkened only if text in it would read under 4.5:1 on the light background (none of the six offered accents is: they
     * are unchanged; a pale wallpaper colour is not); in dark mode lightened until text on a dark surface reads at 4.5:1 or better (true black is darker still, so it reads at least as well).
     */
    fun primaryFor(accent: Accent, dark: Boolean): Int {
        var c = accent.rgb and 0xFFFFFF
        var step = 0
        if (!dark) {
            while (contrast(c, LIGHT_BACKGROUND) < 4.5 && step < 20) { c = mix(c, 0x000000, 0.12); step++ }
            return c
        }
        while (contrast(c, DARK_SURFACE) < 4.5 && step < 20) { c = mix(c, 0xFFFFFF, 0.12); step++ }
        return c
    }

    const val DARK_SURFACE = 0x141218
    private const val LIGHT_SURFACE = 0xFFFFFF
    /** Material's light background, the lightest thing a light primary is read on. */
    const val LIGHT_BACKGROUND = 0xFEF7FF
    /** UI S9: the true-black theme's background and surface. */
    const val BLACK_SURFACE = 0x000000

    /** The soft background used behind selected chips and cards: the primary colour thinned into the surface ([black]: into true black). */
    fun containerFor(primary: Int, dark: Boolean, black: Boolean = false): Int =
        if (dark) mix(if (black) BLACK_SURFACE else DARK_SURFACE, primary, 0.30) else mix(LIGHT_SURFACE, primary, 0.14)

    internal fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * The surface, text and outline colours that differ from Material's baseline (UI S9, U-25), as 0xRRGGBB. The Compose theme copies them over its baseline scheme; everything else (the accent roles,
 * the money colours) is decided elsewhere.
 */
data class SurfaceRoles(
    val background: Int, val surface: Int, val onSurface: Int, val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
    val containerLowest: Int, val containerLow: Int, val container: Int, val containerHigh: Int, val containerHighest: Int,
)

object ThemeRoles {
    /** Material 3's baseline roles (the colours the app has drawn with until UI S9), light and dark. */
    val LIGHT = SurfaceRoles(0xFEF7FF, 0xFEF7FF, 0x1D1B20, 0xE7E0EC, 0x49454F, 0x79747E, 0xCAC4D0, 0xFFFFFF, 0xF7F2FA, 0xF3EDF7, 0xECE6F0, 0xE6E0E9)
    val DARK = SurfaceRoles(0x141218, 0x141218, 0xE6E0E9, 0x49454F, 0xCAC4D0, 0x938F99, 0x49454F, 0x0F0D13, 0x1D1B20, 0x211F26, 0x2B2930, 0x36343B)

    /** True black (OLED): the background and every surface black or nearly; raised surfaces step up in small greys so cards and sheets still separate from the screen. */
    val BLACK = SurfaceRoles(0x000000, 0x000000, 0xE6E0E9, 0x2B2930, 0xCAC4D0, 0x938F99, 0x3A3740, 0x000000, 0x0E0D10, 0x141218, 0x1D1B20, 0x2B2930)

    /**
     * The roles for a theme, or null where Material's baseline stays as it is (light or dark, without high contrast: nothing changes for anyone who chose neither). [highContrast] (the phone's
     * high-contrast text or contrast level, the iPhone's Increase Contrast) makes secondary text as strong as body text and every outline a visible line (3:1 or better).
     */
    fun surfaces(dark: Boolean, black: Boolean, highContrast: Boolean): SurfaceRoles? {
        val trueBlack = dark && black
        if (!trueBlack && !highContrast) return null
        val base = if (trueBlack) BLACK else if (dark) DARK else LIGHT
        if (!highContrast) return base
        return if (dark) base.copy(onSurfaceVariant = 0xE6E0E9, outline = 0xCAC4D0, outlineVariant = 0xCAC4D0)
        else base.copy(onSurfaceVariant = 0x2E2B33, outline = 0x49454F, outlineVariant = 0x49454F)
    }
}
