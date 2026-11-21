package com.kbcode.steps.ui.billing

import androidx.compose.foundation.layout.*
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

@Composable
fun LogoOverlay(owned: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        content()
        if (!owned) {
            val label = stringResource(R.string.logo_trial_label)
            val baseStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Box(
                modifier = Modifier.align(Alignment.BottomStart)
                    .safeDrawingPadding().padding(start = 8.dp, bottom = 4.dp)
                    .alpha(0.55f)
                    .clearAndSetSemantics {
                        @Suppress("DEPRECATION")
                        invisibleToUser()
                        testTag = "steps-logo"
                    }
            ) {
                // Outline layer in the background color, so the label stays
                // readable on any map tile or photo behind it.
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.background,
                    style = baseStyle.copy(drawStyle = Stroke(width = 8f, join = StrokeJoin.Round))
                )
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = baseStyle
                )
            }
        }
    }
}
