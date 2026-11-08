package com.kevinconnors.scorestand.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevinconnors.scorestand.practice.ClickGenerator
import com.kevinconnors.scorestand.practice.MetronomeEngine
import com.kevinconnors.scorestand.practice.TapTempo
import com.kevinconnors.scorestand.practice.tempoName

/** Material's icon set has no metronome, so this is a small custom one. */
val MetronomeIcon: ImageVector by lazy {
    ImageVector.Builder("Metronome", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes("M9.4,2h5.2l5,20H4.4ZM8.3,18.5h7.4l-0.7,-2.8H9Z M11.2,14.9L17.4,4.9l1.3,0.8L12.5,15.7Z"),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black),
    ).build()
}

/** Metronome state for the reader; tempo is remembered per score via [onSettingsChanged]. */
class MetronomeController(private val onSettingsChanged: (bpm: Int, beatsPerBar: Int) -> Unit) {
    private val engine = MetronomeEngine()
    private val tapTempo = TapTempo()

    var bpm by mutableIntStateOf(100)
        private set
    var beatsPerBar by mutableIntStateOf(4)
        private set
    var accent by mutableStateOf(true)
        private set
    var running by mutableStateOf(false)
        private set

    fun load(bpm: Int, beatsPerBar: Int) {
        this.bpm = bpm.coerceIn(ClickGenerator.MIN_BPM, ClickGenerator.MAX_BPM); engine.bpm = this.bpm
        this.beatsPerBar = beatsPerBar.coerceIn(1, 16); engine.beatsPerBar = this.beatsPerBar
    }

    fun changeBpm(value: Int) {
        bpm = value.coerceIn(ClickGenerator.MIN_BPM, ClickGenerator.MAX_BPM)
        engine.bpm = bpm
        onSettingsChanged(bpm, beatsPerBar)
    }

    fun changeBeats(value: Int) {
        beatsPerBar = value.coerceIn(1, 16)
        engine.beatsPerBar = beatsPerBar
        onSettingsChanged(bpm, beatsPerBar)
    }

    fun toggleAccent() { accent = !accent; engine.accentFirstBeat = accent }
    fun tap() { tapTempo.tap()?.let(::changeBpm) }
    fun toggle() = if (running) stop() else start()
    fun start() { engine.start(); running = true }
    fun stop() { engine.stop(); running = false }
    fun currentBeat() = engine.currentBeat()
}

@Composable
fun MetronomePanel(metronome: MetronomeController, onClose: () -> Unit) {
    var beat by remember { mutableIntStateOf(-1) }
    LaunchedEffect(metronome.running) {
        if (!metronome.running) { beat = -1; return@LaunchedEffect }
        while (true) withFrameNanos { beat = metronome.currentBeat() }
    }
    var beatsMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MetronomeIcon, null, Modifier.size(20.dp))
                Text("  Metronome", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                // Beat lights: the first beat is larger when accented.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(metronome.beatsPerBar) { i ->
                        val on = i == beat
                        val first = i == 0 && metronome.accent && metronome.beatsPerBar > 1
                        Box(
                            Modifier.size(if (first) 16.dp else 12.dp).clip(CircleShape).background(
                                when {
                                    on && first -> MaterialTheme.colorScheme.tertiary
                                    on -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        )
                    }
                }
                IconButton(onClick = { metronome.stop(); onClose() }) { Icon(Icons.Outlined.Close, "Close metronome") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = metronome::toggle, modifier = Modifier.size(52.dp)) {
                    Icon(if (metronome.running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (metronome.running) "Stop" else "Start")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.width(96.dp)) {
                    Text("${metronome.bpm} BPM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(tempoName(metronome.bpm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { metronome.changeBpm(metronome.bpm - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Slider(
                    metronome.bpm.toFloat(), { metronome.changeBpm(it.toInt()) },
                    valueRange = ClickGenerator.MIN_BPM.toFloat()..ClickGenerator.MAX_BPM.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { metronome.changeBpm(metronome.bpm + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = metronome::tap) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tap tempo")
                }
                Box {
                    TextButton(onClick = { beatsMenu = true }) {
                        Text("${metronome.beatsPerBar} beat${if (metronome.beatsPerBar == 1) "" else "s"} per bar"); Icon(Icons.Outlined.ArrowDropDown, null)
                    }
                    DropdownMenu(beatsMenu, { beatsMenu = false }) {
                        (1..12).forEach { n ->
                            DropdownMenuItem(text = { Text("$n") }, onClick = { beatsMenu = false; metronome.changeBeats(n) })
                        }
                    }
                }
                FilterChip(selected = metronome.accent, onClick = metronome::toggleAccent, label = { Text("Accent beat 1") })
                listOf(60, 80, 100, 120).forEach { preset ->
                    TextButton(onClick = { metronome.changeBpm(preset) }) { Text("$preset") }
                }
            }
        }
    }
}
