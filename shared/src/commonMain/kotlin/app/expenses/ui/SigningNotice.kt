package app.expenses.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.SigningExpiry
import app.expenses.nowMillis

/** The iPhone test build's "stops opening in N days" words, or null: nothing unless the platform knows an expiry and it is within two days (never on Android). */
@Composable
fun signingNoticeText(): String? {
    val platform = LocalPlatform.current
    return remember(platform) { platform.signingExpiresAtMillis?.let { SigningExpiry.notice(it, nowMillis()) } }
}

/** The iPhone test build's "stops opening in N days" line (see [signingNoticeText]). Nothing at all when there is nothing to say. */
@Composable
fun SigningNotice(modifier: Modifier = Modifier) {
    val text = signingNoticeText() ?: return
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp).testTag("signing_notice")) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
