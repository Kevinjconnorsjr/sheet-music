package com.kevinconnors.scorestand.ui.playlist

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOn
import androidx.compose.material.icons.outlined.RepeatOneOn
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.ShuffleOn
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.ui.TitleText
import com.kevinconnors.scorestand.ui.reader.audio.formatTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The full player for listening playlists: track, seek bar with the A–B loop, transport, loop
 * mode and shuffle, A–B repeat, speed / pitch / pan / volume, the sleep timer and what's next.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun NowPlayingScreen(onBack: () -> Unit, vm: ListeningViewModel = viewModel()) {
    val p = vm.player
    val track = p.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { TitleText(p.playlistName ?: "Now playing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (track == null) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Nothing is playing. Open a playlist and tap Play.", textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            TitleText(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            p.queue.getOrNull(p.index)?.let { TitleText(it.scoreTitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Track ${p.index + 1} of ${p.queue.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SeekBar(p.positionMs, p.durationMs, track.loopStartMs, track.loopEndMs, track.loopEnabled, onSeek = p::seekTo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(p.positionMs), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                Text(formatTime(p.durationMs), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            }
            // Transport
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = p::toggleShuffle) {
                    Icon(if (p.shuffle) Icons.Outlined.ShuffleOn else Icons.Outlined.Shuffle, if (p.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (p.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = p::previous) { Icon(Icons.Outlined.SkipPrevious, "Previous", Modifier.size(32.dp)) }
                IconButton(onClick = { p.skip(-5_000) }) { Icon(Icons.Outlined.Replay5, "Back 5 seconds") }
                FilledIconButton(onClick = p::togglePlay, modifier = Modifier.size(64.dp)) {
                    Icon(if (p.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (p.isPlaying) "Pause" else "Play", Modifier.size(36.dp))
                }
                IconButton(onClick = { p.skip(5_000) }) { Icon(Icons.Outlined.Forward5, "Forward 5 seconds") }
                IconButton(onClick = p::next) { Icon(Icons.Outlined.SkipNext, "Next", Modifier.size(32.dp)) }
                IconButton(onClick = p::cycleRepeat) {
                    val (icon, label) = when (p.repeatMode) {
                        Player.REPEAT_MODE_ALL -> Icons.Outlined.RepeatOn to "Looping the playlist"
                        Player.REPEAT_MODE_ONE -> Icons.Outlined.RepeatOneOn to "Looping this track"
                        else -> Icons.Outlined.Repeat to "Not looping"
                    }
                    Icon(icon, label, tint = if (p.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                when (p.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "Looping the whole playlist"
                    Player.REPEAT_MODE_ONE -> "Looping this track"
                    else -> "Stopping at the end of the playlist"
                } + if (p.shuffle) " · shuffled" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            // A–B repeat
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = p::setLoopStart) { Text(if (track.loopStartMs >= 0) "A ${formatTime(track.loopStartMs)}" else "Set A") }
                OutlinedButton(onClick = p::setLoopEnd) { Text(if (track.loopEndMs >= 0) "B ${formatTime(track.loopEndMs)}" else "Set B") }
                FilledTonalIconToggleButton(checked = track.loopEnabled, enabled = p.hasLoop(track), onCheckedChange = { p.toggleLoop() }) {
                    Icon(Icons.Outlined.Repeat, if (track.loopEnabled) "Stop repeating A–B" else "Repeat A–B")
                }
                if (track.loopStartMs >= 0) IconButton(onClick = p::clearLoop) { Icon(Icons.Outlined.Close, "Clear A–B") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Practice settings, saved with the track (the same ones as in the score's audio panel).
            Setting("Speed", "${(track.speed * 100).roundToInt()}%", if (track.speed != 1f) ({ p.setSpeed(1f) }) else null) {
                IconButton(onClick = { p.setSpeed((track.speed - 0.05f).coerceAtLeast(0.25f)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Slider(track.speed, { p.setSpeed(it) }, valueRange = 0.25f..2f, modifier = Modifier.weight(1f))
                IconButton(onClick = { p.setSpeed((track.speed + 0.05f).coerceAtMost(2f)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            val semis = Math.floorDiv(track.pitchCents + 50, 100)
            val cents = track.pitchCents - semis * 100
            Setting(
                "Pitch",
                (if (semis > 0) "+$semis" else "$semis") + " st" + if (cents != 0) (if (cents > 0) " +$cents¢" else " $cents¢") else "",
                if (track.pitchCents != 0) ({ p.setPitchCents(0) }) else null,
            ) {
                IconButton(onClick = { p.setPitchCents(track.pitchCents - 100) }) { Text("♭", style = MaterialTheme.typography.titleLarge) }
                Slider(cents.toFloat(), { p.setPitchCents(semis * 100 + it.roundToInt()) }, valueRange = -50f..50f, modifier = Modifier.weight(1f))
                IconButton(onClick = { p.setPitchCents(track.pitchCents + 100) }) { Text("♯", style = MaterialTheme.typography.titleLarge) }
            }
            Setting(
                "Pan",
                when {
                    abs(track.pan) < 0.02f -> "Centre"
                    track.pan < 0 -> "L ${(-track.pan * 100).roundToInt()}%"
                    else -> "R ${(track.pan * 100).roundToInt()}%"
                },
                if (track.pan != 0f) ({ p.setPan(0f) }) else null,
            ) {
                Text("L", Modifier.padding(horizontal = 12.dp))
                Slider(track.pan, { p.setPan(if (abs(it) < 0.04f) 0f else it) }, valueRange = -1f..1f, modifier = Modifier.weight(1f))
                Text("R", Modifier.padding(horizontal = 12.dp))
            }
            Setting("Volume", "${(track.volume * 100).roundToInt()}%", if (track.volume != 1f) ({ p.setVolume(1f) }) else null) {
                Spacer(Modifier.width(36.dp))
                Slider(track.volume, { p.setVolume(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(36.dp))
            }
            Spacer(Modifier.height(8.dp))
            SleepTimerCard(p)
            Spacer(Modifier.height(12.dp))
            Text("Playlist", style = MaterialTheme.typography.titleMedium)
            p.queue.forEachIndexed { i, item ->
                val playing = i == p.index
                Row(
                    Modifier.fillMaxWidth().clickable { p.playAt(i) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", Modifier.width(28.dp), color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        TitleText(item.track.title, fontWeight = if (playing) FontWeight.Bold else null, color = if (playing) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                        TitleText(item.scoreTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatTime(item.track.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, loopA: Long, loopB: Long, loopOn: Boolean, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val d = duration.coerceAtLeast(1)
    val loopColor = MaterialTheme.colorScheme.tertiary
    Box(Modifier.fillMaxWidth()) {
        // The A–B loop drawn behind the slider.
        if (loopA >= 0 && loopB > loopA) {
            Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                val a = loopA.toFloat() / d * size.width
                val b = loopB.toFloat() / d * size.width
                drawRect(loopColor.copy(alpha = if (loopOn) 0.25f else 0.1f), Offset(a, size.height * 0.25f), Size(b - a, size.height * 0.5f))
            }
        }
        Slider(
            value = dragging ?: (position.toFloat() / d).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onSeek((it * d).toLong()) }; dragging = null },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Setting(label: String, value: String, onReset: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(84.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        Box(Modifier.width(60.dp)) { if (onReset != null) TextButton(onClick = onReset) { Text("Reset") } }
    }
}
