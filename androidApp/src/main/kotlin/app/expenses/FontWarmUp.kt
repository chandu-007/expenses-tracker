package app.expenses

import android.content.Context
import android.graphics.Typeface

/** Builds the typefaces of [AppFonts] once, so Compose's first text layout finds them in the platform's own cache. */
internal fun warmFonts(context: Context, @Suppress("UNUSED_PARAMETER") fonts: AppFonts) {
    for (file in listOf("Inter-400", "Inter-500", "Inter-600", "SpaceGrotesk-500", "SpaceGrotesk-600")) Typeface.createFromAsset(context.assets, "fonts/$file.ttf")
}
