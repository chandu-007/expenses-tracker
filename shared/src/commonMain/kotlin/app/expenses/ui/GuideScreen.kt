package app.expenses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Guide
import app.expenses.core.domain.GuideMode
import app.expenses.core.domain.GuidePlan

/**
 * The interactive tour. The app opens the real screen for each stop (the caller does that when [index] changes), this layer dims it, cuts a window around
 * the control the stop is about when the screen has registered one with [tourSpot], and explains the part in a card that moves out of the way.
 *
 * Skip is always in reach and one tap ends the tour; the system Back button does the same. [onTryIt] ends the tour and leaves the person on the screen
 * being shown, to try it. [onFinish] gets whether they asked not to be shown the tour after updates.
 */
@Composable
fun GuideScreen(
    plan: GuidePlan, index: Int, onIndex: (Int) -> Unit, onShowAll: () -> Unit, onTryIt: () -> Unit, onFinish: (neverAgain: Boolean) -> Unit,
) {
    var never by rememberSaveable { mutableStateOf(false) }
    val steps = plan.steps
    val i = index.coerceIn(0, steps.lastIndex)
    val step = steps[i]
    val last = i == steps.lastIndex
    val manual = plan.mode == GuideMode.MANUAL
    var origin by androidx.compose.runtime.remember { mutableStateOf(Offset.Zero) }
    val spot = step.spot?.let { TourSpots.bounds[it] }
    val density = LocalDensity.current
    val screenHeightPx = LocalWindowInfo.current.containerSize.height
    // the card goes to the side of the screen the highlighted control is not on
    val cardAtTop = spot != null && with(density) { spot.center.y - origin.y > (screenHeightPx / 2) }
    // the dialog does no dimming of its own; the tour does its own, with a bright window around the control it points at
    GuideDialog(onDismiss = { onFinish(never) }) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }.testTag("guide")) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.testTag("guide_scrim")) {
                drawRect(Color.Black.copy(alpha = if (spot != null) 0.62f else 0.3f))
                if (spot != null) {
                    val pad = 8.dp.toPx()
                    val topLeft = Offset(spot.left - origin.x - pad, spot.top - origin.y - pad)
                    val size = Size(spot.width + 2 * pad, spot.height + 2 * pad)
                    val corner = CornerRadius(18.dp.toPx())
                    drawRoundRect(Color.Black, topLeft, size, corner, blendMode = BlendMode.Clear)
                    drawRoundRect(Color.White, topLeft, size, corner, style = Stroke(width = 3.dp.toPx()))
                }
            }
            Surface(
                Modifier.align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).fillMaxWidth().testTag("guide_card"),
                shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (plan.mode == GuideMode.WHATS_NEW) Text("What is new", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("guide_whatsnew"))
                            Text("${i + 1} of ${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("guide_counter"))
                        }
                        TextButton(onClick = { onFinish(never) }, modifier = Modifier.testTag("guide_skip")) { Text(if (manual) "Close" else "Skip the tour") }
                    }
                    LinearProgressIndicator(progress = { (i + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
                    Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("guide_title"))
                    Text(step.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("guide_summary"))
                    // long notes scroll inside the card, so the buttons never leave the screen
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).testTag("guide_points"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        step.points.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.padding(top = 7.dp).size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Text(p, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Find it: ${step.where}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("guide_where"))
                    if (plan.mode == GuideMode.WHATS_NEW && steps.size < Guide.steps.size && i == 0) {
                        TextButton(onClick = onShowAll, modifier = Modifier.testTag("guide_show_all")) { Text("See the whole tour") }
                    }
                    if (!manual && (i == 0 || last)) Row(Modifier.fillMaxWidth().clickable { never = !never }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = never, onCheckedChange = { never = it }, modifier = Modifier.testTag("guide_never"))
                        Text("Do not show this tour after updates", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onIndex(i - 1) }, enabled = i > 0, modifier = Modifier.weight(1f).testTag("guide_back")) { Text("Back") }
                        if (step.target != null && !last) TextButton(onClick = onTryIt, modifier = Modifier.testTag("guide_try")) { Text("Try it") }
                        if (last) Button(onClick = { onFinish(never) }, modifier = Modifier.weight(1f).testTag("guide_done")) { Text(if (manual) "Close" else "Start using the app") }
                        else Button(onClick = { onIndex(i + 1) }, modifier = Modifier.weight(1f).testTag("guide_next")) { Text("Next") }
                    }
                }
            }
        }
    }
}
