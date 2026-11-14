package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What Reports shows while its first page is being worked out (it used to be the words "Loading…" in the middle of a white page): the page's own chrome, the four period choices and the four groupings as
 * chips that cannot be pressed yet, and plain blocks where the title, the figures, the chart and the list will be. It does not pulse or shimmer (a looping animation keeps the screen redrawing), it only
 * fades in once, and it is hidden from screen readers: there is nothing in it to read.
 */
@Composable
internal fun ReportsSkeleton(padding: PaddingValues) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (reduce) shown.snapTo(1f) else shown.animateTo(1f, Motion.tween(false, Motion.CHIP)) }
    val block = MaterialTheme.colorScheme.surfaceVariant
    fun Modifier.block(h: Dp, w: Dp? = null, corner: Dp = 8.dp) = (if (w == null) fillMaxWidth() else width(w)).height(h).clip(RoundedCornerShape(corner)).background(block)
    Column(Modifier.fillMaxSize().padding(padding).graphicsLayer { alpha = shown.value }.clearAndSetSemantics { testTag = "reports_skeleton" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("By period", "By year", "Financial year", "Any dates").forEachIndexed { i, l -> FilterChip(selected = i == 0, onClick = {}, enabled = false, label = { Text(l) }) }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Category", "Account", "Tag", "Merchant").forEachIndexed { i, l -> FilterChip(selected = i == 0, onClick = {}, enabled = false, label = { Text(l) }) }
        }
        Box(Modifier.padding(horizontal = 16.dp)) { Box(Modifier.block(24.dp, 180.dp)) }
        Box(Modifier.padding(horizontal = 16.dp)) { Box(Modifier.block(96.dp, corner = 24.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            listOf(0.4f, 0.65f, 0.5f, 0.8f, 0.55f, 1f).forEach { f -> Box(Modifier.weight(1f).height(72.dp * f).clip(RoundedCornerShape(6.dp)).background(block)) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(block))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.block(14.dp)) } }
        }
        repeat(4) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(block))
                Box(Modifier.weight(1f)) { Box(Modifier.block(14.dp)) }
                Box(Modifier.block(14.dp, 64.dp))
            }
        }
    }
}
