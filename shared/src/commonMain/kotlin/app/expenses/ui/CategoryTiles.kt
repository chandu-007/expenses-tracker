package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import app.expenses.core.domain.Appearances
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expenses.CategoryUi
import app.expenses.core.domain.CategoryLook
import app.expenses.core.domain.DefaultRules

/**
 * Category identity (UI S3, backlog U-03): a glyph from Material icons on a tile of one of 12 stable colour slots ([CategoryLook]), the person's own colour winning when they chose one. The glyphs are
 * built the first time they are drawn (a lambda per key), so a screen that shows six of them never builds the other thirty-six.
 */
private val GLYPH_ICONS: Map<String, () -> ImageVector> = mapOf(
    "restaurant" to { Icons.Outlined.Restaurant }, "cafe" to { Icons.Outlined.LocalCafe }, "cart" to { Icons.Outlined.ShoppingCart }, "bag" to { Icons.Outlined.ShoppingBag },
    "bus" to { Icons.Outlined.DirectionsBus }, "car" to { Icons.Outlined.DirectionsCar }, "fuel" to { Icons.Outlined.LocalGasStation }, "train" to { Icons.Outlined.Train },
    "flight" to { Icons.Outlined.Flight }, "hotel" to { Icons.Outlined.Hotel }, "beach" to { Icons.Outlined.BeachAccess }, "home" to { Icons.Outlined.Home },
    "bolt" to { Icons.Outlined.Bolt }, "water" to { Icons.Outlined.WaterDrop }, "wifi" to { Icons.Outlined.Wifi }, "phone" to { Icons.Outlined.Smartphone },
    "receipt" to { Icons.AutoMirrored.Outlined.ReceiptLong }, "card" to { Icons.Outlined.CreditCard }, "payments" to { Icons.Outlined.Payments }, "savings" to { Icons.Outlined.Savings },
    "bank" to { Icons.Outlined.AccountBalance }, "trend" to { Icons.Outlined.TrendingUp }, "percent" to { Icons.Outlined.Percent }, "shield" to { Icons.Outlined.Shield },
    "medical" to { Icons.Outlined.MedicalServices }, "fitness" to { Icons.Outlined.FitnessCenter }, "spa" to { Icons.Outlined.Spa }, "pets" to { Icons.Outlined.Pets },
    "child" to { Icons.Outlined.ChildCare }, "school" to { Icons.Outlined.School }, "book" to { Icons.Outlined.MenuBook }, "movie" to { Icons.Outlined.Movie },
    "music" to { Icons.Outlined.MusicNote }, "games" to { Icons.Outlined.SportsEsports }, "gift" to { Icons.Outlined.CardGiftcard }, "heart" to { Icons.Outlined.Favorite },
    "work" to { Icons.Outlined.Work }, "build" to { Icons.Outlined.Build }, "subscriptions" to { Icons.Outlined.Subscriptions }, "swap" to { Icons.Outlined.SwapHoriz },
    "sell" to { Icons.Outlined.Sell }, "label" to { Icons.Outlined.Label }, "money" to { Icons.Outlined.AttachMoney },
)

/** The icon for a glyph key; the plain tag for a key this build does not know. */
fun categoryGlyph(key: String?): ImageVector = (GLYPH_ICONS[key] ?: GLYPH_ICONS.getValue("label"))()

/** What the app knows about how one category is drawn: its id (the colour slot comes from it), the colour the person chose, the glyph the person chose. */
class CategoryStyle(val id: String, val colorArgb: Long?, val glyph: String?, val name: String)

/** The categories by name (lower case), so a row that carries only a category's name can be drawn as that category. Provided once at the root. */
class CategoryLooks(private val byName: Map<String, CategoryStyle>) {
    operator fun get(name: String): CategoryStyle? = byName[name.trim().lowercase()]

    companion object {
        val Empty = CategoryLooks(emptyMap())
        fun of(categories: List<CategoryUi>): CategoryLooks = CategoryLooks(buildMap {
            for (c in categories) {
                val style = CategoryStyle(c.id, c.colorArgb, c.glyph, c.name)
                put(c.name.trim().lowercase(), style)
                put(displayName(categories, c).trim().lowercase(), style)
            }
        })
    }
}

val LocalCategoryLooks = compositionLocalOf { CategoryLooks.Empty }

@Composable
private fun isDarkTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Int.opaque() = Color(0xFF000000.toInt() or this)

/** The tile colours for a category style, a slot hue or a chosen colour. */
@Composable
private fun tileColors(slotKey: String, style: CategoryStyle?): CategoryLook.Tile {
    val dark = isDarkTheme()
    val own = style?.colorArgb
    return remember(slotKey, style?.id, own, dark) {
        when {
            own != null -> CategoryLook.tileOfHue((own and 0xFFFFFF).toInt(), dark)
            style != null -> CategoryLook.tileFor(CategoryLook.slot(style.id), dark)
            else -> CategoryLook.tileFor(CategoryLook.slotOf(slotKey), dark)
        }
    }
}

private fun tileShape(size: Dp) = if (size >= 36.dp) Radius.M else Radius.S

/**
 * A tile for something named [name] (a category, usually): when [name] is one of the person's categories the tile is its glyph on its colour; when it is not (a payee, a person, a bill) it is a monogram,
 * the first letter on a stable colour from the text. [fallback] is used for the monogram when [name] is blank.
 */
@Composable
fun CategoryAvatar(name: String, fallback: String, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color? = null, monogram: Boolean = false) {
    // [monogram]: the first letter, whatever the name is (an account, a tag or a shop that happens to share a category's name is not that category)
    val style = if (monogram) null else LocalCategoryLooks.current[name]
    val label = name.ifBlank { fallback }
    // a [tint] draws the tile in that colour (the slice of a chart, over the surface when it has some alpha) with the glyph colour that reads best on it
    val surface = MaterialTheme.colorScheme.surface
    val tinted = tint?.compositeOver(surface)?.toArgb()?.and(0xFFFFFF)
    val tile = if (tinted != null) CategoryLook.Tile(tinted, Appearances.onColor(tinted)) else tileColors(label.trim().lowercase(), style)
    Box(modifier.size(size).clip(tileShape(size)).background(tile.container.opaque()).testTag("tile_" + label.trim().lowercase()), contentAlignment = Alignment.Center) {
        if (style != null) Icon(categoryGlyph(style.glyph ?: CategoryLook.defaultGlyph(style.name)), contentDescription = null, tint = tile.glyph.opaque(), modifier = Modifier.size(size * 0.56f))
        else Text(label.trim().take(1).uppercase(), color = tile.glyph.opaque(), fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
    }
}

/** A swap tile in neutral colours: what a move between the person's own accounts looks like, never red or green. */
@Composable
fun TransferTile(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(size).clip(tileShape(size)).background(colors.surfaceVariant).testTag("tile_transfer"), contentAlignment = Alignment.Center) {
        Icon(categoryGlyph("swap"), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(size * 0.56f))
    }
}

/** True when [title] is a merchant the app knows by name (its starter table of well-known payees). Remembered per title. */
@Composable
fun rememberIsKnownMerchant(title: String): Boolean = remember(title) { title.isNotBlank() && DefaultRules.displayNameFor(title) != null }

/**
 * The tile of a list row: a transfer is the grey swap tile; a payee the app knows by name (Starbucks, Netflix) is its monogram on a colour from the payee, with a small badge of the category it is in;
 * anything else is the category's glyph tile. The tile is decoration: the row's text names the category.
 */
@Composable
fun EntryTile(title: String, category: String, kind: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (kind.startsWith("TRANSFER")) { TransferTile(modifier, size); return }
    val style = LocalCategoryLooks.current[category]
    if (style != null && rememberIsKnownMerchant(title)) {
        val tile = tileColors(title.trim().lowercase(), null)
        val catTile = tileColors(style.id, style)
        Box(modifier.size(size)) {
            Box(Modifier.size(size).clip(tileShape(size)).background(tile.container.opaque()).testTag("tile_" + title.trim().lowercase()), contentAlignment = Alignment.Center) {
                Text(title.trim().take(1).uppercase(), color = tile.glyph.opaque(), fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
            }
            // the badge needs room: on the 32 dp tile of large text it would sit on the letter
            if (size >= 36.dp) Badge(catTile, categoryGlyph(style.glyph ?: CategoryLook.defaultGlyph(style.name)), size)
        }
    } else CategoryAvatar(category, title, modifier, size)
}

@Composable
private fun BoxScope.Badge(tile: CategoryLook.Tile, glyph: ImageVector, size: Dp) {
    val badge = size * 0.4f
    Box(Modifier.align(Alignment.BottomEnd).size(badge).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(badge - 2.dp).background(tile.container.opaque(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(glyph, contentDescription = null, tint = tile.glyph.opaque(), modifier = Modifier.size(badge * 0.6f))
        }
    }
}
