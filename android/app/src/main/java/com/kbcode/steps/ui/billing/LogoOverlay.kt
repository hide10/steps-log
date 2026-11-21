package com.kbcode.steps.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kbcode.steps.R

/**
 * Trial logo for the KB Code billing template: the localized "app name +
 * trial version" label, 20sp bold, alpha 0.55, with an outline in the
 * color of the surface behind it so the label stays readable.
 * Decorative only: hidden from accessibility services and never clickable.
 *
 * Placement in this app: a strip of its own in the layout (see
 * [TrialLogoBar]), NOT a floating overlay. The steps screens are scrollable
 * lists with buttons and text reaching the bottom-left, and the first-run
 * guide has a full-width primary button at the bottom; a floating logo
 * overlapped both (confirmed on an emulator). Reserving layout space makes
 * overlap impossible on every screen. Locapin can float the logo because
 * its main surface is a map with no controls at the bottom-left.
 *
 * The outline uses the strip's container color: that is the surface the
 * logo actually sits on, so the outline blends into it.
 */
@Composable
fun TrialLogo(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.logo_trial_label)
    val baseStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
    Box(
        modifier = modifier
            .alpha(0.55f)
            .clearAndSetSemantics {
                @Suppress("DEPRECATION")
                invisibleToUser()
                testTag = "steps-logo"
            }
    ) {
        // Outline layer in the strip's container color, so the label
        // stays readable and the outline blends into the strip.
        Text(
            text = label,
            color = MaterialTheme.colorScheme.surfaceContainer,
            style = baseStyle.copy(drawStyle = Stroke(width = 8f, join = StrokeJoin.Round))
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            style = baseStyle
        )
    }
}

/**
 * The trial logo as a layout strip: bottom-left, directly above the bottom
 * navigation (or at the very bottom on the first-run guide). Painted with
 * the navigation bar's container color so it reads as part of that area.
 * Renders nothing once the purchase is owned.
 */
@Composable
fun TrialLogoBar(owned: Boolean, modifier: Modifier = Modifier) {
    if (owned) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        TrialLogo()
    }
}
