package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** The hero card's background: the accent, fading to a slightly deeper (or, in dark mode where the accent is light, lighter) tone of itself, so the text on it keeps its contrast at both ends. */
@Composable
fun Modifier.heroBackground(): Modifier {
    val p = MaterialTheme.colorScheme.primary
    val second = lerp(p, if (p.luminance() > 0.5f) Color.White else Color.Black, 0.28f)
    val onP = MaterialTheme.colorScheme.onPrimary
    return this.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(p, second)))
        // a soft light in the top corner and a fine edge: the look of a glossy pane, drawn once, with no blur and no cost while scrolling
        .drawBehind { drawCircle(Brush.radialGradient(listOf(onP.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.88f, 0f), radius = size.width * 0.6f), radius = size.width * 0.6f, center = Offset(size.width * 0.88f, 0f)) }
        .border(1.dp, onP.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
}

/** "Today", "Yesterday", or "Mon, 5 Oct" for a local day given as an epoch-day number. */
fun dayLabel(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return when (epochDay) {
        today.toEpochDay() -> "Today"
        today.toEpochDay() - 1 -> "Yesterday"
        else -> java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy").format(d)
    }
}

/** A rounded tinted tile with the category's initial; the tint is the same for the same name every time (a small fixed palette, all pale enough for dark text). */
@Composable
fun CategoryAvatar(name: String, fallback: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val label = name.ifBlank { fallback }
    val tint = AVATAR_TINTS[Math.floorMod(label.lowercase().hashCode(), AVATAR_TINTS.size)]
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(tint), contentAlignment = Alignment.Center) {
        Text(label.trim().take(1).uppercase(), color = Color(0xFF1B1B1F), fontWeight = FontWeight.SemiBold)
    }
}

private val AVATAR_TINTS = listOf(Color(0xFFBFE3D0), Color(0xFFC9DAF8), Color(0xFFF4D9B5), Color(0xFFE5CFF0), Color(0xFFF6C9CF), Color(0xFFD7E6A8), Color(0xFFB9E2E8), Color(0xFFE8DDB5))

@Composable
fun DayHeader(epochDay: Long) {
    Text(dayLabel(epochDay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

/** A short rise-and-fade when a screen opens (not for the home list, whose [key] is negative). Respects the phone's remove-animations setting. */
@Composable
fun ScreenEnter(key: Int, content: @Composable () -> Unit) {
    if (key < 0) { content(); return }
    key(key) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = Motion.STANDARD)) }
        Box(Modifier.graphicsLayer { val p = progress.value; alpha = 0.35f + 0.65f * p; translationY = (1f - p) * 24.dp.toPx() }) { content() }
    }
}

/** Two softly blurred lights behind the hero's figures. Only called on Android 12 and later, where blur exists; before that the corner light from [heroBackground] is all there is. */
@Composable
fun BoxScope.HeroGlow(color: Color) {
    // matchParentSize: the lights take the card's size and never decide it (a fill-the-screen box here once stretched the card over the whole screen);
    // the unbounded edge lets each blur fade out past its own square instead of being cut off as a hard-edged block
    Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-30).dp).size(150.dp).blur(48.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded).background(color.copy(alpha = 0.34f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-30).dp, y = 36.dp).size(120.dp).blur(40.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded).background(color.copy(alpha = 0.20f), CircleShape))
    }
}

/** Where the glass indicator of the bottom bar last rested, so a bar that appears on another screen starts there and glides on, as one bar would. */
private object TabMemory { var index = 0f }

private val TABS = listOf("Home", "Search", "Reports", "More")

/**
 * The bar along the bottom: Home, Search, Reports and More, equal in width so they fit any screen, each label on one line (it shrinks rather than wrap).
 * [selected] names the tab that is current. A pane of glass (a lit, see-through pill) glides under the current tab on a spring and stretches a little while it
 * travels, then settles; the pane starts where it last rested, so moving between screens looks like one bar moving. [moreBadge] puts a small dot on More
 * (something there wants attention). The tags are the ones the actions had before.
 */
@Composable
fun HomeBar(selected: String, onHome: () -> Unit, onSearch: () -> Unit, onReports: () -> Unit, onMore: () -> Unit, moreBadge: Boolean = false) {
    val target = TABS.indexOf(selected).coerceAtLeast(0).toFloat()
    val reduce = LocalReduceMotion.current
    val pos = remember { Animatable(if (reduce) target else TabMemory.index) }
    LaunchedEffect(target, reduce) {
        if (reduce) pos.snapTo(target)
        else pos.animateTo(target, Motion.spring(dampingRatio = 0.72f, stiffness = 260f))
        TabMemory.index = target
    }
    val glassTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val glassBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val rim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // navigationBarsPadding: with the phone's three-button or gesture bar showing, the tabs must sit above it, not under it
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val gap = 4.dp.toPx()
                        val w = (size.width - gap * 3) / 4f
                        val stretch = 1f + 0.10f * kotlin.math.abs(target - pos.value).coerceAtMost(3f)
                        val pw = w * stretch
                        val cx = pos.value * (w + gap) + w / 2f
                        val r = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                        val tl = androidx.compose.ui.geometry.Offset(cx - pw / 2f, 0f)
                        val sz = androidx.compose.ui.geometry.Size(pw, size.height)
                        drawRoundRect(Brush.verticalGradient(listOf(glassTop, glassBottom)), tl, sz, r)
                        // the lit edge and the highlight along the top that make it read as glass
                        drawRoundRect(rim, tl, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        drawRoundRect(Color.White.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(tl.x + 10.dp.toPx(), 3.dp.toPx()), androidx.compose.ui.geometry.Size(pw - 20.dp.toPx(), 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarItem("Home", Icons.Outlined.Home, selected == "Home", onHome, Modifier.weight(1f).testTag("nav_home"))
                    BarItem("Search", Icons.Outlined.Search, selected == "Search", onSearch, Modifier.weight(1f).testTag("open_search"))
                    BarItem("Reports", Icons.Outlined.BarChart, selected == "Reports", onReports, Modifier.weight(1f).testTag("open_reports"))
                    BarItem("More", Icons.Outlined.MoreHoriz, selected == "More", onMore, Modifier.weight(1f).testTag("more_menu"), badge = moreBadge)
                }
            }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier, badge: Boolean = false) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(if (LocalReduceMotion.current) 0 else Motion.STANDARD), label = "tab tint",
    )
    Column(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(20.dp)).pressScale(onClick, androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick).semantics { role = Role.Tab; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error).testTag("more_badge"))
        }
        androidx.compose.material3.Text( // a fixed label, never an amount, so the amount-hiding Text is not needed (and has no softWrap)
            // the label shrinks to fit its quarter of the bar (a narrow screen or a big system font) instead of being cut or wrapped
            label, maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp), style = MaterialTheme.typography.labelMedium,
            color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** How many tiles go side by side on the More page: two on a phone, three where the screen is wide (a tablet or a phone on its side). */
val LocalMoreColumns = androidx.compose.runtime.compositionLocalOf { 2 }

/** The More page: a title and the groups of tiles, scrolling if the screen is short, with as many columns as the width allows. */
@Composable
fun MorePage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 600.dp) 3 else 2
        androidx.compose.runtime.CompositionLocalProvider(LocalMoreColumns provides cols) {
            FadeIn { Column(Modifier.fillMaxSize().androidxVerticalScroll().padding(horizontal = 16.dp, vertical = 12.dp).testTag("more_page")) {
                Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                content()
            } }
        }
    }
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

/** Whether money went out, came in, or only moved between your own accounts. */
enum class Flow3 { OUT, IN, MOVE }

fun flowOf(kind: String, inflow: Boolean): Flow3 = when {
    kind == "TRANSFER" -> Flow3.MOVE
    inflow -> Flow3.IN
    else -> Flow3.OUT
}

/**
 * The colour of an amount: red for money out, green for money in, the ordinary text colour for a transfer. The shades are chosen per theme so each reads at
 * 4.5:1 or better on that theme's background (the light ones are darker, the dark ones lighter than the plain red and green).
 */
@Composable
fun amountColor(flow: Flow3): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (flow) {
        Flow3.OUT -> if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
        Flow3.IN -> if (dark) Color(0xFF6FD3A0) else Color(0xFF17744A)
        Flow3.MOVE -> MaterialTheme.colorScheme.onSurface
    }
}

/** The amount with its sign: "-" for money out, "+" for money in, none for a transfer. [amount] is the plain formatted figure. */
fun signedAmount(amount: String, flow: Flow3): String = when (flow) {
    Flow3.OUT -> "-" + amount.removePrefix("-")
    Flow3.IN -> "+" + amount.removePrefix("+")
    Flow3.MOVE -> amount
}

@Composable
fun MenuHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp))
}

/** A group of tiles under a small heading, two to a row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MenuGroup(heading: String, tiles: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    MenuHeading(heading)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = LocalMoreColumns.current, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = tiles)
}

/** One place in the More menu: a tinted round badge with a symbol and the name (up to three lines), and optionally a count. Keeps the tag the plain item had. */
@Composable
fun androidx.compose.foundation.layout.FlowRowScope.MenuTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .pressScale(onClick).padding(horizontal = 8.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 3, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (badge != null) Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 1.dp)) { Text(badge, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
    }
}

/** Shrinks slightly while pressed and springs back: the touch is acknowledged before the action happens. Follows the phone's remove-animations setting. */
fun Modifier.pressScale(onClick: () -> Unit, haptic: androidx.compose.ui.hapticfeedback.HapticFeedbackType? = null): Modifier = composed {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.95f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    val feedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) { haptic?.let { feedback.performHapticFeedback(it) }; onClick() }
}

/** A short rise-and-fade for the More page. */
@Composable
fun FadeIn(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = Motion.STANDARD)) }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 12.dp.toPx() }) { content() }
}


/**
 * Whether the on-screen keyboard is showing. The window is resized for the keyboard rather than padded, so the insets API reports nothing on every phone
 * and emulator this app supports; the visible part of the window shrinking by more than a sixth of its height is the reliable sign.
 */
@Composable
fun rememberKeyboardOpen(): Boolean {
    val view = androidx.compose.ui.platform.LocalView.current
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(view) {
        val rect = android.graphics.Rect()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(rect)
            val full = view.rootView.height
            open = full > 0 && (full - rect.bottom) > full / 6
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}
