package app.expenses.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import app.expenses.R
import app.expenses.core.domain.Accent
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.DeltaDirection
import app.expenses.core.domain.WidgetCategoryBar
import app.expenses.core.domain.WidgetDelta
import app.expenses.core.domain.WidgetHave
import app.expenses.core.domain.WidgetRecent
import app.expenses.core.domain.WidgetSummary
import app.expenses.core.domain.WidgetTier

// ---- the widgets' look ----
//
// One calm card: the surface colour, a tonal container for chips and the comparison, ONE ACCENT for "+ Add" and the bars, muted text for captions. The colours are the app's own palette (violet by
// default) in light and dark, and the accent is the one chosen in the app (More, Appearance): it is read with the figures ([WidgetModel.accent]) and mapped here to a drawable and a text colour
// pair taken from the same functions the app's theme uses ([Appearances.primaryFor], [Appearances.onColor]), so the widget and the app agree. The widget does NOT use the phone's Material You
// colours: the app has no "dynamic colour" setting, so its look is its own and the widget keeps to it. Money text comes in already masked when it has to be (see [WidgetModel]): nothing here decides
// what may be shown, it only draws.
//
// ---- touch targets ----
// Every control that can be tapped is at least [TARGET] dp (48) in both directions at every size, and the widgets declare minimum heights of 56 dp so that one always fits (see res/xml). The
// controls are: the eye, "+ Add", a quick-entry chip, the Undo word, and (2 x 1 only) the figure that reveals the amount. The visible shapes are smaller than their targets (the eye icon is 20 dp,
// the round Add 40 dp), so the card looks as before. Anything else on the widget (captions, figures, bars, empty space) opens the app: the whole card is one tap target (the root), and the controls
// above sit on top of it. The target sizes are measured on a device with the real views (WidgetTargetsTest).

internal const val TARGET = WidgetSummary.Room.TARGET

internal object WidgetColors {
    val text = ColorProvider(R.color.widget_text)
    val muted = ColorProvider(R.color.widget_muted)
    val positive = ColorProvider(R.color.widget_positive)
    val negative = ColorProvider(R.color.widget_negative)
}

/** The accent as the widget draws it: a pill/bar drawable of that accent's colour and the colour of the text on it, both light and dark. Unknown keys fall back to the app's default (violet). */
internal object WidgetAccents {
    fun fill(a: Accent): Int = when (a.key) {
        "blue" -> R.drawable.widget_pill_blue
        "teal" -> R.drawable.widget_pill_teal
        "green" -> R.drawable.widget_pill_green
        "orange" -> R.drawable.widget_pill_orange
        "red" -> R.drawable.widget_pill_red
        else -> R.drawable.widget_pill_violet
    }

    private fun color(rgb: Int) = Color(0xFF000000.toInt() or rgb)

    /** Text on the accent: black or white, whichever reads better on the accent in that mode (the same rule as the app's `onPrimary`). */
    fun onAccent(a: Accent): androidx.glance.unit.ColorProvider = ColorProvider(
        day = color(Appearances.onColor(Appearances.primaryFor(a, dark = false))),
        night = color(Appearances.onColor(Appearances.primaryFor(a, dark = true))),
    )
}

/**
 * The small caption above the figure. When the app itself keeps the figure masked it says why ("App lock is on", "Amounts hidden"), and when the data could not be read at all it is just the app's
 * name, so that a masked or empty widget never looks broken; tapping the figure or the caption opens the app.
 */
internal fun caption(model: WidgetModel, long: Boolean): String {
    val base = if (model.monthSpend == null) "Expenses" else if (long) "Spent this month" else "This month"
    val why = model.hiddenBy ?: return base
    return if (long) "$base · ${why.caption}" else why.caption
}

/** The figure line: this month's total as the widget may show it, and "Open the app" (which a tap does) when the database could not be read. */
internal fun figure(model: WidgetModel): String = model.monthSpend ?: if (model.needsApp) "Open the app" else "Quick add"

/** A Glance figure's size so that [text] fits in [availableDp] on one line: as big as [maxSp], never under [minSp]. Bold digits and the rupee sign are about 0.6 em wide, and a little more is kept back. */
internal fun amountSp(text: String, availableDp: Float, maxSp: Float, minSp: Float = 14f): Float = (availableDp / (text.length.coerceAtLeast(1) * 0.66f)).coerceIn(minSp, maxSp)

private val EDGE = 14.dp

/** Narrower than this a 2 x 2 has no room for the eye beside the caption, so the eye sits beside Add on the bottom row (two 48 dp targets side by side). */
private const val SQUARE_NARROW = 150

/** The widget: its card, drawn for the size it was given. What goes in it is [WidgetSummary.plan]'s decision. */
@Composable
fun WidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    val w = size.width.value.toInt(); val h = size.height.value.toInt()
    val ins = model.insights
    val plan = WidgetSummary.plan(w, h, WidgetHave(model.buttons.size, ins.categories.size, ins.recent.size, ins.delta != null, ins.budget != null, model.undo != null, model.revealed))
    // The padding is the card's inset; a tier that needs the width or the height for 48 dp controls takes the inset from the controls' own transparent margin instead (see the tiers).
    val (horizontal, vertical) = when (plan.tier) {
        WidgetTier.TINY -> 0 to 4
        WidgetTier.SQUARE -> 0 to 2
        WidgetTier.WIDE -> 12 to 4
        WidgetTier.CARD -> 14 to WidgetSummary.Room.PAD
    }
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg)).clickable(actionStartActivity(openIntent()))
            .padding(horizontal = horizontal.dp, vertical = vertical.dp),
    ) {
        when (plan.tier) {
            WidgetTier.TINY -> Tiny(model, w)
            WidgetTier.SQUARE -> Square(model, plan.showDelta, w, h)
            WidgetTier.WIDE -> Wide(model, plan.chips, plan.wideAdd, w)
            WidgetTier.CARD -> Card(model, plan, w, h)
        }
    }
}

// ---- the sizes ----

/**
 * About 2 x 1: this month's figure (no ".00": a five-digit amount still fits) and the round Add button. There is no room for a 48 dp eye next to Add and a figure at the real launcher minimum
 * (110 dp wide: 48 + 48 + a figure of about 40 does not fit before 176 dp), so the FIGURE is the eye here: a tap on the masked figure first asks "Show?" (the caption turns to "Confirm" for
 * [ARM_WINDOW_MILLIS]) and a second tap inside that time shows it; a tap on a shown figure hides it at once. Two taps because the figure is the part of a widget people tap to open the app, and
 * one stray tap must not put the month's spending on a home screen in public. When the app forces the figure masked there is nothing to reveal and the figure just opens the app, like everything
 * else that is not a control. The target is the whole text column (caption and figure) and its left margin: about 54 x 48 dp at 110 dp wide.
 */
@Composable
private fun Tiny(model: WidgetModel, widthDp: Int) {
    val reveal = model.monthSpend != null && model.canToggle
    val asking = reveal && !model.revealed && model.armed
    Row(GlanceModifier.fillMaxSize().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val column = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 12.dp).let {
            if (!reveal) it else it.clickable(if (model.revealed || model.armed) actionRunCallback<ToggleTotalAction>() else actionRunCallback<ArmRevealAction>())
                .semantics { contentDescription = if (model.revealed) "Hide the amount" else if (asking) "Tap again to show the amount" else "Show the amount" }
        }
        Column(column, verticalAlignment = Alignment.CenterVertically) {
            Text(if (asking) "Confirm" else caption(model, long = false), style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
            val text = if (asking) "Show?" else figure(model).removeSuffix(".00")
            Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, widthDp - 12f - 48f - 8f, 18f, 12f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        AddButton(wide = false, accent = model.accent)
    }
}

/**
 * About 2 x 2: caption and eye on top, the figure and the round Add button underneath, the comparison between them when there is height for it. Both rows are 48 dp, so the card needs 100 dp and
 * the widgets declare 56 dp as the least height of one cell. Under [SQUARE_NARROW] dp wide the eye moves down beside Add (two 48 dp targets take 96 of the 110 dp) so the caption and the figure
 * get the whole width.
 */
@Composable
private fun Square(model: WidgetModel, showDelta: Boolean, widthDp: Int, heightDp: Int) {
    val eye = model.monthSpend != null && model.canToggle
    val caption = caption(model, long = false)
    val text = figure(model).let { if (it.length > 9) it.removeSuffix(".00") else it }
    if (widthDp < SQUARE_NARROW) {
        Column(GlanceModifier.fillMaxSize()) {
            Column(GlanceModifier.padding(horizontal = 12.dp)) {
                Text(caption, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
                Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, widthDp - 24f, 20f, 13f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            // full width, so that two 48 dp targets fit at 110 dp: the margin is what is left, up to 8 dp
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = ((widthDp - 2 * TARGET) / 2).coerceIn(0, 8).dp), verticalAlignment = Alignment.CenterVertically) {
                if (eye) Eye(model.revealed)
                Spacer(GlanceModifier.defaultWeight())
                AddButton(wide = false, accent = model.accent)
            }
        }
        return
    }
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(GlanceModifier.fillMaxWidth().height(TARGET.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (eye) Eye(model.revealed)
        }
        if (showDelta && heightDp >= 130) model.insights.delta?.let { Delta(it, withWords = false) }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth().height(TARGET.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, widthDp - 24f - 48f, 22f, 14f).sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            AddButton(wide = false, accent = model.accent)
        }
    }
}

/** About 4 x 1: one row: caption and figure, the eye, a chip or two when the width allows, Add. */
@Composable
private fun Wide(model: WidgetModel, chips: Int, wideAdd: Boolean, widthDp: Int) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(caption(model, long = true), style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
            val text = figure(model)
            Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, widthDp - 24f - 48f - (if (wideAdd) 90f else 48f) - chips * 90f, 22f, 15f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        if (model.monthSpend != null && model.canToggle) Eye(model.revealed)
        model.buttons.take(chips).forEach { b -> Spacer(GlanceModifier.width(6.dp)); Chip(b, GlanceModifier, showDetail = false) }
        Spacer(GlanceModifier.width(4.dp))
        AddButton(wide = wideAdd, accent = model.accent)
    }
}

/** Everything wider than two cells and taller than one: a header row, then as many of the sections as the height holds, in [WidgetSummary.plan]'s order. */
@Composable
private fun Card(model: WidgetModel, plan: app.expenses.core.domain.WidgetPlan, widthDp: Int, heightDp: Int) {
    val ins = model.insights
    val inner = widthDp - 2 * 14f
    Column(GlanceModifier.fillMaxSize()) {
        // header: caption (and the comparison), the figure, the eye, Add
        Row(GlanceModifier.fillMaxWidth().height(WidgetSummary.Room.HEADER.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(caption(model, long = true), style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
                    if (plan.showDelta) ins.delta?.let { Spacer(GlanceModifier.width(8.dp)); Delta(it, withWords = widthDp >= 400) }
                }
                val text = figure(model)
                val avail = widthDp - 2 * 14f - (if (model.monthSpend != null && model.canToggle) 48f else 0f) - (if (plan.wideAdd) 96f else 52f)
                Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, avail, 24f, 15f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            if (model.monthSpend != null && model.canToggle) Eye(model.revealed)
            AddButton(wide = plan.wideAdd, accent = model.accent)
        }
        when {
            model.needsApp -> Text("Open the app to unlock your data", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 6.dp))
            else -> {
                if (plan.showUndo) model.undo?.let { Undo(it) }
                if (plan.chips > 0) Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                    val shown = model.buttons.take(plan.chips)
                    shown.forEachIndexed { i, b ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Chip(b, GlanceModifier.defaultWeight(), showDetail = (inner - 8 * (shown.size - 1)) / shown.size >= 96f)
                    }
                }
                if (plan.showHint) Text("Save a quick entry in the app to see it here", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 8.dp))
                if (plan.showBudget) ins.budget?.let { b ->
                    Column(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(b.text, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
                        Bar(inner, b.permille, over = b.over, height = 6.dp, accent = model.accent)
                    }
                }
                if (plan.categories > 0) {
                    Section("Where it went")
                    ins.categories.take(plan.categories).forEach { CategoryRow(it, inner, model.accent) }
                }
                if (plan.recent > 0) {
                    Section("Latest")
                    ins.recent.take(plan.recent).forEach { RecentRow(it) }
                }
                if (model.revealed && model.nothingYet && plan.categories == 0 && heightDp >= 146) Text("Nothing spent yet this month", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 8.dp))
                if (plan.hiddenNote) Text(model.hiddenBy?.note ?: "Tap the eye to see this month in detail", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 8.dp))
            }
        }
    }
}

// ---- the parts ----

/** The eye: shows or hides the figures. Its touch area is [target] square (48 dp), the icon 20 dp in the middle. */
@Composable
internal fun Eye(revealed: Boolean, target: Dp = TARGET.dp) {
    Box(GlanceModifier.size(target).clickable(actionRunCallback<ToggleTotalAction>()), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(if (revealed) R.drawable.ic_widget_eye_off else R.drawable.ic_widget_eye),
            contentDescription = if (revealed) "Hide the amount" else "Show the amount",
            colorFilter = ColorFilter.tint(WidgetColors.muted), modifier = GlanceModifier.size(20.dp),
        )
    }
}

/** The accent button: "+ Add" where there is width, a round "+" where there is not. Either way the touch area is 48 dp high (and 48 wide when round). */
@Composable
internal fun AddButton(wide: Boolean, accent: Accent, modifier: GlanceModifier = GlanceModifier, target: Dp = TARGET.dp) {
    Box(modifier.height(target).then(if (wide) GlanceModifier else GlanceModifier.width(target)).clickable(actionStartActivity(addIntent())), contentAlignment = Alignment.Center) {
        if (wide) Box(GlanceModifier.background(ImageProvider(WidgetAccents.fill(accent))).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text("+ Add", style = TextStyle(color = WidgetAccents.onAccent(accent), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        } else Box(GlanceModifier.size(target - 8.dp).background(ImageProvider(WidgetAccents.fill(accent))), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_add), contentDescription = "Add an expense", colorFilter = ColorFilter.tint(WidgetAccents.onAccent(accent)), modifier = GlanceModifier.size(22.dp))
        }
    }
}

/** The room a chip's label needs at the very least, to keep a one-letter name from making a chip narrower than [TARGET]: [label]'s characters are never narrower than about 3.5 dp each. */
internal fun chipSidePad(label: String): Float = ((TARGET - label.length * 3.5f) / 2f).coerceAtLeast(12f)

/** One quick entry: its name and, when the chip is wide enough, its amount. 48 dp high, and at least 48 dp wide (see [chipSidePad]). */
@Composable
internal fun Chip(b: WidgetButton, modifier: GlanceModifier, showDetail: Boolean = true) {
    Row(
        modifier.height(TARGET.dp).background(ImageProvider(R.drawable.widget_chip_bg)).padding(horizontal = chipSidePad(b.label).dp)
            .clickable(actionRunCallback<QuickAddAction>(actionParametersOf(TEMPLATE_ID to b.templateId))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(b.label, style = TextStyle(color = WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (showDetail && b.detail.isNotEmpty()) Text(b.detail, style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.padding(start = 6.dp))
    }
}

/** "↓ 12% vs last month" in a tonal pill: the arrow is green when spending is lower than last month's at this point, red when higher. */
@Composable
private fun Delta(d: WidgetDelta, withWords: Boolean) {
    val tone = when (d.direction) { DeltaDirection.LESS -> WidgetColors.positive; DeltaDirection.MORE -> WidgetColors.negative; DeltaDirection.SAME -> WidgetColors.muted }
    Row(GlanceModifier.background(ImageProvider(R.drawable.widget_delta_bg)).padding(horizontal = 8.dp, vertical = 1.dp).semantics { contentDescription = d.spoken }, verticalAlignment = Alignment.CenterVertically) {
        Text(d.arrow, style = TextStyle(color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Text(" " + d.percent + if (withWords) " vs last month" else "", style = TextStyle(color = WidgetColors.text, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp))
}

/** A slim bar [widthDp] wide with [permille] of it filled; the fill is red when [over]. Drawn from two boxes, so its length is true to the width the widget really has. */
@Composable
private fun Bar(widthDp: Float, permille: Int, over: Boolean, height: Dp, accent: Accent) {
    Box(GlanceModifier.padding(top = 3.dp)) {
        Box(GlanceModifier.width(widthDp.dp).height(height).background(ImageProvider(R.drawable.widget_track_bg))) {
            if (permille > 0) Box(GlanceModifier.width(maxOf(height.value, widthDp * permille / 1000f).dp).height(height).background(ImageProvider(if (over) R.drawable.widget_fill_over_bg else WidgetAccents.fill(accent)))) {}
        }
    }
}

/** A category name, a bar for its share of the month, and its amount. */
@Composable
private fun CategoryRow(c: WidgetCategoryBar, innerDp: Float, accent: Accent) {
    val nameW = 72f; val amountW = 54f; val gap = 8f
    Row(GlanceModifier.fillMaxWidth().height(WidgetSummary.Room.CATEGORY_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(c.name, style = TextStyle(color = WidgetColors.text, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.width(nameW.dp))
        Spacer(GlanceModifier.width(gap.dp))
        Box(GlanceModifier.semantics { contentDescription = "${c.name}: ${c.permille / 10} percent of this month" }) { Bar(innerDp - nameW - amountW - 2 * gap, c.permille, over = false, height = 6.dp, accent = accent) }
        Spacer(GlanceModifier.width(gap.dp))
        Text(c.amountText, style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp, textAlign = TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(amountW.dp))
    }
}

/** One of the newest entries: shop or note, when, signed amount (green for income). */
@Composable
private fun RecentRow(r: WidgetRecent) {
    Row(GlanceModifier.fillMaxWidth().height(WidgetSummary.Room.RECENT_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.title, style = TextStyle(color = WidgetColors.text, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(r.whenText, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.padding(horizontal = 8.dp))
        Text(r.amountText, style = TextStyle(color = if (r.income) WidgetColors.positive else WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End), maxLines = 1)
    }
}

/** The "✓ Chai ₹20 added  Undo" line shown for [UNDO_WINDOW_MILLIS] after a tap, one calm tonal pill 48 dp high: the words on the left, the Undo word on the right with a 48 dp target of its own (the whole of the right end, about 60 dp wide). */
@Composable
private fun Undo(u: WidgetUndo) {
    Box(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(GlanceModifier.fillMaxWidth().height(TARGET.dp).background(ImageProvider(R.drawable.widget_undo_bg)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(u.text, style = TextStyle(color = WidgetColors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxHeight().padding(horizontal = 14.dp).clickable(actionRunCallback<UndoAction>(actionParametersOf(UNDO_ENTRY_ID to u.entryId))), contentAlignment = Alignment.Center) {
                Text("Undo", style = TextStyle(color = WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
