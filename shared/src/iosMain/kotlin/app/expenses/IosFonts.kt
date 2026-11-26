package app.expenses

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL

/**
 * The design's fonts from the app bundle (`iosApp/Resources/Fonts`, seven TTF files), read at once into [androidx.compose.ui.text.platform.Font]s. [problem] says what is wrong when it is not null
 * (which file is missing or unreadable), so a fallback to the system font is never silent: the unit test `FontsTests` fails on it, and the app logs it.
 */
object IosFonts {
    private val files = listOf("Inter-400" to FontWeight.Normal, "Inter-500" to FontWeight.Medium, "Inter-600" to FontWeight.SemiBold, "Inter-700" to FontWeight.Bold, "SpaceGrotesk-500" to FontWeight.Medium, "SpaceGrotesk-600" to FontWeight.SemiBold, "SpaceGrotesk-700" to FontWeight.Bold)

    /** Null when every file is there and read; else the first problem. */
    var problem: String? = null
        private set

    val fonts: AppFonts? by lazy { load() }

    private fun load(): AppFonts? {
        val loaded = HashMap<String, androidx.compose.ui.text.font.Font>()
        for ((name, weight) in files) {
            val path = NSBundle.mainBundle.pathForResource(name, "ttf") ?: run { problem = "font file $name.ttf is not in the app bundle"; return null }
            val bytes = NSData.dataWithContentsOfURL(NSURL.fileURLWithPath(path))?.toByteArray() ?: run { problem = "font file $name.ttf could not be read"; return null }
            if (bytes.size < 10_000) { problem = "font file $name.ttf is only ${bytes.size} bytes"; return null }
            loaded[name] = androidx.compose.ui.text.platform.Font(identity = name, data = bytes, weight = weight)
        }
        return runCatching {
            AppFonts(
                text = FontFamily(listOf("Inter-400", "Inter-500", "Inter-600", "Inter-700").map { loaded.getValue(it) }),
                display = FontFamily(listOf("SpaceGrotesk-500", "SpaceGrotesk-600", "SpaceGrotesk-700").map { loaded.getValue(it) }),
            )
        }.getOrElse { problem = "the font families could not be built: ${it.message}"; null }
    }

    /** Loads the fonts and returns the problem, or null: for the unit test and the app's log. */
    fun check(): String? { fonts; return problem }
}
