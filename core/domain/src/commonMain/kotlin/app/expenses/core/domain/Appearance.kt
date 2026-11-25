package app.expenses.core.domain

import kotlin.math.pow

enum class ThemeMode(val label: String) { SYSTEM("Follow the phone"), LIGHT("Light"), DARK("Dark") }
enum class Density(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

/** An accent colour offered in settings, as 0xRRGGBB. */
data class Accent(
    val key: String, val label: String, val rgb: Int,
    /** The accent as drawn on the dark themes (the boards' own pair: teal 0E6B5C and 4CC2A6); null: lightened from [rgb] until it reads. */
    val darkRgb: Int? = null,
    /** The soft fill behind a chosen chip, a pill or a tile, light and dark (the boards' "accent soft"); null: thinned from the primary. */
    val soft: Int? = null, val softDark: Int? = null,
)

/**
 * How the app looks: light or dark (or the phone's choice), an accent colour, and how tightly lists are spaced. Stored as one line of text (the setting `appearance`, which backups carry).
 * UI S9 (D18): [black] draws the dark theme on true black (OLED); it changes nothing while the app is light. The accent may be [Appearances.WALLPAPER] (Android 12 and later: the phone's own colour).
 */
data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Appearances.ACCENTS.first(), val density: Density = Density.COMFORTABLE, val black: Boolean = false) {
    /** The three old fields exactly as before, so an older app reading a newer backup keeps the mode, the accent (an unknown one falls back to violet) and the spacing; the black theme is a fourth field it ignores. */
    fun encode() = "${mode.name};${accent.key};${density.name}" + if (black) ";BLACK" else ""
}

object Appearances {
    /** The four accents of the redesign (`docs/redesign/screens/Appearance.dc.html`): Teal is the default. "From the wallpaper" is [WALLPAPER]. */
    val ACCENTS = listOf(
        Accent("teal", "Teal", 0x0E6B5C, 0x4CC2A6, 0xE3F1EC, 0x143A33),
        Accent("indigo", "Indigo", 0x3949AB, 0x8C9EFF, 0xE7E9F8, 0x1E2448),
        Accent("plum", "Plum", 0x7B3F8C, 0xD29BE3, 0xF3E8F6, 0x33203A),
        Accent("ochre", "Ochre", 0x8A5A06, 0xE3B45A, 0xF6EED8, 0x33280F),
    )

    /** Accents of earlier versions, and the one each becomes when a saved choice names it (the old "teal" is the new Teal: same key). Anything else unknown becomes the default. */
    val MIGRATED: Map<String, String> = mapOf("violet" to "teal", "blue" to "indigo", "green" to "teal", "orange" to "ochre", "red" to "ochre")

    /**
     * UI S9 (D18): "Match my wallpaper": the phone's own accent (Android 12 and later, from the system's dynamic colours). Its [Accent.rgb] is only the fallback (violet) for where the phone has none
     * (an older Android, the iPhone, a backup restored on such a phone); the real colour comes from [resolve]. Money colours (green in, red over) never follow it.
     */
    val WALLPAPER = Accent("wallpaper", "Wallpaper", 0x0E6B5C, 0x4CC2A6)

    /** Reads the stored text; anything missing or unknown falls back to the default for that part, so a bad value never breaks the screen. */
    fun decode(text: String?): Appearance {
        val p = text.orEmpty().split(';')
        return Appearance(
            ThemeMode.entries.firstOrNull { it.name == p.getOrNull(0) } ?: ThemeMode.SYSTEM,
            p.getOrNull(1).let { k -> (ACCENTS + WALLPAPER).firstOrNull { it.key == k } ?: ACCENTS.firstOrNull { it.key == MIGRATED[k] } } ?: ACCENTS.first(),
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
        var c = ((if (dark) accent.darkRgb else null) ?: accent.rgb) and 0xFFFFFF
        var step = 0
        if (!dark) {
            while (contrast(c, LIGHT_BACKGROUND) < 4.5 && step < 20) { c = mix(c, 0x000000, 0.12); step++ }
            return c
        }
        while (contrast(c, DARK_SURFACE) < 4.5 && step < 20) { c = mix(c, 0xFFFFFF, 0.12); step++ }
        return c
    }

    const val DARK_SURFACE = 0x0F1112
    private const val LIGHT_SURFACE = 0xFFFFFF
    /** Material's light background, the lightest thing a light primary is read on. */
    const val LIGHT_BACKGROUND = 0xF5F5F2

    /** How strongly a chart bar that is not the one being read is drawn: the accent at this share over the page, which keeps 3:1 against the page (WCAG 1.4.11) for every accent in light, dark and black. */
    const val IDLE_BAR_ALPHA = 0.8
    /** UI S9: the true-black theme's background and surface. */
    const val BLACK_SURFACE = 0x000000

    /** The soft background used behind selected chips and cards: the primary colour thinned into the surface ([black]: into true black). */
    fun containerFor(primary: Int, dark: Boolean, black: Boolean = false): Int =
        if (dark) mix(if (black) BLACK_SURFACE else DARK_SURFACE, primary, 0.30) else mix(LIGHT_SURFACE, primary, 0.14)

    /** The soft fill of a chosen accent: the board's own colour for the four accents, thinned from the primary for the wallpaper's. [black]: on true black a dark soft fill is mixed from it. */
    fun softFor(accent: Accent, primary: Int, dark: Boolean, black: Boolean = false): Int =
        (if (dark) accent.softDark else accent.soft) ?: containerFor(primary, dark, black)

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
    /**
     * The redesign's roles ("calm and clear", rule 1 of `docs/REDESIGN.md`, values from `Main.dc.html`): a warm grey ground with WHITE cards (the container roles are what cards are drawn with), the
     * boards' hairline (#E8E8E3) and divider (#EDEDE8), ink #17191B and muted #5C6166; dark: ground #0F1112, card #181B1D, hairline #262A2E, ink #F2F3F1, muted #A3A9AE.
     */
    val LIGHT = SurfaceRoles(0xF5F5F2, 0xF5F5F2, 0x17191B, 0xEDEDE8, 0x5C6166, 0x80868B, 0xE8E8E3, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xF0F0EC, 0xE8E8E3)
    val DARK = SurfaceRoles(0x0F1112, 0x0F1112, 0xF2F3F1, 0x2A2F33, 0xA3A9AE, 0x767D82, 0x262A2E, 0x0F1112, 0x181B1D, 0x181B1D, 0x202427, 0x2A2F33)

    /** True black (OLED): the background and every surface black or nearly; raised surfaces step up in small greys so cards and sheets still separate from the screen. */
    val BLACK = SurfaceRoles(0x000000, 0x000000, 0xF2F3F1, 0x1E2123, 0xA3A9AE, 0x767D82, 0x2A2F33, 0x000000, 0x0B0C0D, 0x111314, 0x1A1D1F, 0x25292B)

    /** The roles for a theme. [highContrast] (the phone's high-contrast text or contrast level, the iPhone's Increase Contrast) makes secondary text as strong as body text and every outline a visible line (3:1 or better). */
    fun surfaces(dark: Boolean, black: Boolean, highContrast: Boolean): SurfaceRoles {
        val base = if (dark && black) BLACK else if (dark) DARK else LIGHT
        if (!highContrast) return base
        return if (dark) base.copy(onSurfaceVariant = 0xF2F3F1, outline = 0xC4C9CC, outlineVariant = 0xC4C9CC)
        else base.copy(onSurfaceVariant = 0x2C3034, outline = 0x4A5055, outlineVariant = 0x4A5055)
    }
}

/**
 * The signal colours as named roles: "watch" (amber: a little ahead, a bill due, something quiet) and "over" (red: over a limit only), each with its soft fill, light and dark, as in the boards
 * (#A34A06 / #F2A65A, #B42318 / #F28B82; fills #FBEBD9 / #3A2A17 and #FCE8E6 / #3D1A17). They do not follow the accent, as the money colours never did.
 */
object Signals {
    fun watch(dark: Boolean) = if (dark) 0xF2A65A else 0xA34A06
    fun watchSoft(dark: Boolean) = if (dark) 0x3A2A17 else 0xFBEBD9
    fun over(dark: Boolean) = if (dark) 0xF28B82 else 0xB42318
    fun overSoft(dark: Boolean) = if (dark) 0x3D1A17 else 0xFCE8E6
}
