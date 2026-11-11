package com.kevinconnors.scorestand.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.kevinconnors.scorestand.data.TitleDisplay

/** The user's choice for long names, provided at the top of the app. */
val LocalTitleDisplay = compositionLocalOf { TitleDisplay.WRAP }

/**
 * Shortens [text] by replacing its middle with "…", keeping as much of the start and the end as
 * fits. [fits] says whether a candidate string fits the available space.
 */
fun middleEllipsize(text: String, fits: (String) -> Boolean): String {
    if (fits(text)) return text
    // Binary search for the most characters that can be kept around the "…".
    var lo = 0
    var hi = text.length - 1
    var best = "…"
    while (lo <= hi) {
        val keep = (lo + hi) / 2
        val start = (keep + 1) / 2
        val candidate = text.take(start).trimEnd() + "…" + text.takeLast(keep - start).trimStart()
        if (fits(candidate)) {
            best = candidate
            lo = keep + 1
        } else {
            hi = keep - 1
        }
    }
    return best
}

/**
 * A name (score, track, setlist, marker) shown the way the user prefers: wrapped in full, or on
 * one line with the middle shortened so both the start and the end stay visible.
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    if (LocalTitleDisplay.current == TitleDisplay.WRAP) {
        Text(text, modifier, style = style, fontWeight = fontWeight, color = color)
        return
    }
    val measurer = rememberTextMeasurer()
    val merged = style.merge(TextStyle(fontWeight = fontWeight))
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val shown = remember(text, maxWidth, merged) {
            if (maxWidth == Constraints.Infinity) text
            else middleEllipsize(text) { candidate ->
                measurer.measure(candidate, merged, maxLines = 1, softWrap = false).size.width <= maxWidth
            }
        }
        Text(shown, style = style, fontWeight = fontWeight, color = color, maxLines = 1, softWrap = false)
    }
}
