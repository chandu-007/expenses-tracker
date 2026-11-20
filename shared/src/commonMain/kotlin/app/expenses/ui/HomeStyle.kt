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
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.heading
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import app.expenses.formatDate
import app.expenses.todayLocal
import kotlinx.datetime.LocalDate

/** The hero card's background: the accent, fading to a slightly deeper (or, in dark mode where the accent is light, lighter) tone of itself, so the text on it keeps its contrast at both ends. */
@Composable
fun Modifier.heroBackground(): Modifier {
    val p = MaterialTheme.colorScheme.primary
    val second = lerp(p, if (p.luminance() > 0.5f) Color.White else Color.Black, 0.28f)
    val onP = MaterialTheme.colorScheme.onPrimary
    return this.clip(Radius.L).background(Brush.linearGradient(listOf(p, second)))
        // a soft light in the top corner and a fine edge: the look of a glossy pane, drawn once, with no blur and no cost while scrolling
        .drawBehind { drawCircle(Brush.radialGradient(listOf(onP.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.88f, 0f), radius = size.width * 0.6f), radius = size.width * 0.6f, center = Offset(size.width * 0.88f, 0f)) }
        .border(1.dp, onP.copy(alpha = 0.22f), Radius.L)
}

/** "Today", "Yesterday", or "Mon, 5 Oct" for a local day given as an epoch-day number. */
fun dayLabel(epochDay: Long, today: LocalDate = todayLocal()): String {
    val d = LocalDate.fromEpochDays(epochDay)
    return when (epochDay) {
        today.toEpochDays() -> "Today"
        today.toEpochDays() - 1 -> "Yesterday"
        else -> formatDate(d, if (d.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy")
    }
}

/**
 * The heading above each day's entries: the day, and at the right what was spent that day ([total], already signed, null when the day is not complete yet or had no spending). The pair reads as one heading
 * to a screen reader ("Today, -₹1,112.00"). The total is drawn with [Text], so Hide amounts masks it.
 */
@Composable
fun DayHeader(epochDay: Long, total: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() }.testTag("day_header_$epochDay"),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dayLabel(epochDay), style = Heading.Day, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (total != null) Text(total, style = Heading.Day, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 12.dp).testTag("day_total_$epochDay"))
    }
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

/** How tall the fade above the bottom bar is: the same as the bottom padding of the lists that scroll under it (UI S7). */
private val SCROLL_EDGE = 24.dp

/** Where the glass indicator of the bottom bar last rested, so a bar that appears on another screen starts there and glides on, as one bar would. */
private object TabMemory { var index = 0f }

/** The five places along the bar, left to right: the Add button is the middle one and is never the current place, so the glass never rests on it. */
private val TABS = listOf("Home", "Search", "Add", "Reports", "More")

/** The most the bar's labels grow with the phone's text size (130%): at 200% a label of 24 sp made "Search" and "Reports" touch and "Home" wider than its pill. */
private const val BAR_MAX_FONT_SCALE = 1.3f

/**
 * The bar along the bottom: Home, Search, a round Add button in the middle, Reports and More, equal in width so they fit any screen, each label on one line (it shrinks rather than wrap).
 * [selected] names the tab that is current. A pane of glass (a lit, see-through pill) glides under the current tab on a spring and stretches a little while it
 * travels, then settles; the pane starts where it last rested, so moving between screens looks like one bar moving. [moreBadge] puts a small dot on More
 * (something there wants attention). [onAdd] is the Add button (null leaves the middle empty); its test tag is [addTag] (Home's is `add`, the tag the button had when it floated).
 * The other tags are the ones the actions had before. The labels grow with the text size only up to [BAR_MAX_FONT_SCALE]. UI S8 (backlog N-20): a long press on Add lists [quickEntries] (the saved
 * quick entries with an amount); choosing one is [onQuickEntry] (recorded at once, with Undo, as on Home). With none saved, a long press does nothing more than a tap.
 */
@Composable
fun HomeBar(selected: String, onHome: () -> Unit, onSearch: () -> Unit, onReports: () -> Unit, onMore: () -> Unit, moreBadge: Boolean = false, onAdd: (() -> Unit)? = null, addTag: String = "bar_add", quickEntries: List<BarQuickEntry> = emptyList(), onQuickEntry: (String) -> Unit = {}) {
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
    // UI S7 (U-22): the content slides under a soft fade above the bar instead of being cut off hard at its edge. The fade is the screen's own colour, drawn once (no blur), as tall as the lists' bottom padding, so the last
    // row is clear of it at rest. A person who asked the phone for less transparency gets an opaque bar with a visible edge and no fade.
    val opaque = systemReduceTransparency()
    val screen = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().drawBehind {
        if (!opaque) {
            val h = SCROLL_EDGE.toPx()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, screen.copy(alpha = 0.92f)), startY = -h, endY = 0f), topLeft = androidx.compose.ui.geometry.Offset(0f, -h), size = androidx.compose.ui.geometry.Size(size.width, h))
        }
    }.testTag(if (opaque) "bar_opaque" else "bar_glass")) {
    androidx.compose.material3.Surface(color = if (opaque) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            androidx.compose.material3.HorizontalDivider(color = if (opaque) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // navigationBarsPadding: with the phone's three-button or gesture bar showing, the tabs must sit above it, not under it
            CappedFontScale(BAR_MAX_FONT_SCALE) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val gap = 4.dp.toPx()
                        val w = (size.width - gap * (TABS.size - 1)) / TABS.size
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
                    if (onAdd != null) AddItem(onAdd, Modifier.weight(1f), addTag, quickEntries, onQuickEntry) else Spacer(Modifier.weight(1f))
                    BarItem("Reports", Icons.Outlined.BarChart, selected == "Reports", onReports, Modifier.weight(1f).testTag("open_reports"))
                    BarItem("More", Icons.Outlined.MoreHoriz, selected == "More", onMore, Modifier.weight(1f).testTag("more_menu"), badge = moreBadge)
                }
            }
            }
        }
    }
    }
}

@Composable
private fun BarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier, badge: Boolean = false) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(if (LocalReduceMotion.current) Motion.REDUCED_MS else Motion.TAB), label = "tab tint",
    )
    Column(
        modifier.heightIn(min = 56.dp).clip(Radius.M).pressScale(onClick, Feel.PRESS).semantics { role = Role.Tab; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error).testTag("more_badge"))
        }
        androidx.compose.material3.Text( // a fixed label, never an amount, so the amount-hiding Text is not needed (and has no softWrap)
            // the label shrinks to fit its fifth of the bar (a narrow screen or a big system font) instead of being cut or wrapped
            label, maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp), style = MaterialTheme.typography.labelMedium,
            color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** One saved quick entry offered by a long press on the bar's Add (UI S8): its template [id], [name] and formatted [amountText] (hidden by [Text] while Hide amounts is on). */
data class BarQuickEntry(val id: String, val name: String, val amountText: String)

/**
 * The Add button in the middle of the bar: a filled pill with a plus, the word "Add" under it. 56 dp high like the other places, so the touch area is well over 48 dp. Its test tag is [tag]. A long press
 * (UI S8) opens a short menu of [quick] entries (`bar_quick_menu`, one item `bar_quick_<id>` each); a screen reader offers it as the long-press action "Quick entries". The menu is composed only while open.
 */
@Composable
private fun AddItem(onClick: () -> Unit, modifier: Modifier, tag: String, quick: List<BarQuickEntry>, onQuick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val feel = rememberFeel()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.M)
                .pressScale(onClick, Feel.PRESS, onLongClick = if (quick.isEmpty()) null else ({ feel(Feel.LONG_PRESS); open = true }), onLongClickLabel = "Quick entries")
                .semantics { role = Role.Button }.testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(width = 56.dp, height = 32.dp).clip(Radius.S).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            androidx.compose.material3.Text(
                "Add", maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
            )
        }
        if (open) androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = { open = false }, modifier = Modifier.testTag("bar_quick_menu")) {
            quick.forEach { q ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${q.name}  ${q.amountText}", maxLines = 1) },
                    onClick = { open = false; onQuick(q.id) },
                    modifier = Modifier.testTag("bar_quick_${q.id}"),
                )
            }
        }
    }
}

/** Whether money went out, came in, or only moved between your own accounts. */
enum class Flow3 { OUT, IN, MOVE }

fun flowOf(kind: String, inflow: Boolean): Flow3 = when {
    // Home narrowed to one account: a transfer is money in or out for that account
    kind == "TRANSFER_IN" -> Flow3.IN
    kind == "TRANSFER_OUT" -> Flow3.OUT
    kind == "TRANSFER" -> Flow3.MOVE
    inflow -> Flow3.IN
    else -> Flow3.OUT
}

/** Whether spending is drawn in red (the Appearance switch "Show spending in red"; off by default: the ordinary text colour with a minus sign). */
val LocalSpendingRed = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The colour of an amount (UI S3): money in is green, with a plus sign; money out is the ordinary text colour with a minus sign, because the sign already says which way it went and a list that is all red makes red
 * mean nothing; a transfer is the ordinary text colour. Red is kept for what is wrong (over a limit, overdue, failed), drawn by those screens, and the person can switch spending back to red under Appearance
 * ([LocalSpendingRed]). The greens and reds are chosen per theme so each reads at 4.5:1 or better on that theme's background.
 */
@Composable
fun amountColor(flow: Flow3): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (flow) {
        Flow3.OUT -> if (!LocalSpendingRed.current) MaterialTheme.colorScheme.onSurface else if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
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

/**
 * Shrinks slightly while pressed and springs back: the touch is acknowledged before the action happens. Follows the phone's remove-animations setting. With [haptic] (UI S7: [Feel.PRESS] for the tabs and
 * tiles) it gives that feel at the moment of touch-down, not when the finger lifts; a touch that turns into a scroll gives none, because the press is only reported once the touch is not a scroll.
 */
fun Modifier.pressScale(onClick: () -> Unit, haptic: Feel? = null, onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null): Modifier = composed {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.95f else 1f, Motion.press(), label = "press")
    val feel by androidx.compose.runtime.rememberUpdatedState(rememberFeel())
    if (haptic != null) LaunchedEffect(source, haptic) {
        source.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Press) feel(haptic) }
    }
    val scaled = this.graphicsLayer { scaleX = scale; scaleY = scale }
    // UI S8: with [onLongClick] (the bar's Add) a long press is a second action, which a screen reader offers under [onLongClickLabel]
    if (onLongClick == null) scaled.clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) { onClick() }
    else scaled.combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onLongClickLabel = onLongClickLabel, onLongClick = onLongClick) { onClick() }
}

/** A short rise-and-fade for the More page. */
@Composable
fun FadeIn(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = Motion.STANDARD)) }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 12.dp.toPx() }) { content() }
}

/** The widest the app is drawn: wider screens (tablets, unfolded foldables) get margins either side, so lines of text and rows stay a readable length. */
val MAX_CONTENT_WIDTH = 720.dp

/** The change against last month as a small chip on the month card ("▲ +158%"): drawn on the card's own colour, so it reads in light and dark. The words are the sentence under the figure; the chip is the short form. */
@Composable
fun ComparePill(text: String, onHero: Color, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text, style = MaterialTheme.typography.labelMedium, color = onHero, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(onHero.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("month_compare_pill"),
    )
}
