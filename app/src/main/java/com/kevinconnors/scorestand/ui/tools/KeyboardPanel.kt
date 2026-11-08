package com.kevinconnors.scorestand.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinconnors.scorestand.practice.Keys
import com.kevinconnors.scorestand.practice.PianoSynth

/** White keys from [firstWhite] across [whiteCount] keys, with black keys between. */
private class KeyLayout(val firstMidi: Int, val whiteCount: Int, val width: Float, val height: Float) {
    val whites: List<Int> = generateSequence(firstMidi) { it + 1 }.filter { !Keys.isBlack(it) }.take(whiteCount).toList()
    val whiteWidth = width / whiteCount
    val blackWidth = whiteWidth * 0.6f
    val blackHeight = height * 0.62f

    fun whiteLeft(i: Int) = i * whiteWidth
    /** Black keys sit on the boundary after each white key that has a sharp. */
    val blacks: List<Pair<Int, Float>> = whites.mapIndexedNotNull { i, w ->
        val sharp = w + 1
        if (Keys.isBlack(sharp) && i < whites.lastIndex) sharp to (whiteLeft(i + 1) - blackWidth / 2) else null
    }

    fun keyAt(p: Offset): Int? {
        if (p.y < 0 || p.y > height || p.x < 0 || p.x > width) return null
        if (p.y <= blackHeight) blacks.firstOrNull { (_, left) -> p.x in left..(left + blackWidth) }?.let { return it.first }
        return whites.getOrNull((p.x / whiteWidth).toInt())
    }
}

/**
 * On-screen piano. Several fingers can play at once; sliding along the keys plays a glissando.
 * Arrows shift by an octave; the number of keys fits the screen width.
 */
@Composable
fun KeyboardPanel(synth: PianoSynth, onClose: () -> Unit) {
    var startOctave by rememberSaveable { mutableIntStateOf(3) } // leftmost C = C3
    var showNames by rememberSaveable { mutableStateOf(false) }
    var sustain by rememberSaveable { mutableStateOf(false) }
    val pressed = remember { mutableStateMapOf<PointerId, Int>() }
    val measurer = rememberTextMeasurer()
    val highlight = MaterialTheme.colorScheme.primary

    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Piano, null, Modifier.size(20.dp).padding(start = 4.dp))
                Text("  Keyboard", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(enabled = startOctave > 1, onClick = { startOctave-- }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Octave down") }
                Text("C$startOctave", style = MaterialTheme.typography.labelLarge)
                IconButton(enabled = startOctave < 6, onClick = { startOctave++ }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Octave up") }
                FilterChip(selected = sustain, onClick = { sustain = !sustain; synth.sustain = sustain }, label = { Text("Sustain") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = showNames, onClick = { showNames = !showNames }, label = { Text("Names") })
                IconButton(onClick = { synth.allOff(); onClose() }) { Icon(Icons.Outlined.Close, "Close keyboard") }
            }
            val firstMidi = (startOctave + 1) * 12
            val firstMidiState by rememberUpdatedState(firstMidi)
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .pointerInput(Unit) {
                        // Track every finger separately so chords and slides both work.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val whiteTarget = 34.dp.toPx()
                                val layout = KeyLayout(firstMidiState, (size.width / whiteTarget).toInt().coerceIn(10, 36), size.width.toFloat(), size.height.toFloat())
                                for (change in event.changes) {
                                    val before = pressed[change.id]
                                    val now = if (change.pressed) layout.keyAt(change.position) else null
                                    if (before != now) {
                                        before?.let { synth.noteOff(it) }
                                        now?.let { synth.noteOn(it) }
                                        if (now == null) pressed.remove(change.id) else pressed[change.id] = now
                                    }
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                val layout = KeyLayout(firstMidi, (size.width / 34.dp.toPx()).toInt().coerceIn(10, 36), size.width, size.height)
                val down = pressed.values.toSet()
                layout.whites.forEachIndexed { i, midi ->
                    val left = layout.whiteLeft(i)
                    drawRoundRect(if (midi in down) highlight.copy(alpha = 0.45f) else Color.White, Offset(left, 0f), Size(layout.whiteWidth, size.height), CornerRadius(6f))
                    drawRoundRect(Color(0xFF9E9E9E), Offset(left, 0f), Size(layout.whiteWidth, size.height), CornerRadius(6f), style = Stroke(1.5f))
                    val isC = Keys.name(midi) == "C"
                    if (showNames || isC) {
                        val text = measurer.measure(if (isC) Keys.label(midi) else Keys.name(midi), TextStyle(fontSize = 11.sp, color = if (midi == 60) highlight else Color(0xFF616161)))
                        drawText(text, topLeft = Offset(left + (layout.whiteWidth - text.size.width) / 2, size.height - text.size.height - 6))
                    }
                }
                layout.blacks.forEach { (midi, left) ->
                    drawRoundRect(if (midi in down) highlight else Color(0xFF212121), Offset(left, 0f), Size(layout.blackWidth, layout.blackHeight), CornerRadius(4f))
                    if (showNames) {
                        val text = measurer.measure(Keys.name(midi), TextStyle(fontSize = 9.sp, color = Color.White))
                        drawText(text, topLeft = Offset(left + (layout.blackWidth - text.size.width) / 2, layout.blackHeight - text.size.height - 4))
                    }
                }
            }
        }
    }
}
