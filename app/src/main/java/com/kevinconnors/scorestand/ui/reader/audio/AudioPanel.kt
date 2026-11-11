package com.kevinconnors.scorestand.ui.reader.audio

import com.kevinconnors.scorestand.ui.TitleText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevinconnors.scorestand.data.AudioMarker
import com.kevinconnors.scorestand.data.AudioTrack
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.TextInputDialog
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun AudioPanel(
    audio: AudioController,
    currentPage: Int,
    onAddTrack: () -> Unit,
    onClose: () -> Unit,
    /** Saves a page turn at this moment and turns the page; null where pages don't apply. */
    onTurnPageHere: (() -> Unit)? = null,
    pageCount: Int = 0,
) {
    var editingTurns by remember { mutableStateOf(false) }
    if (editingTurns) PageTurnEditor(audio, pageCount, onClose = { editingTurns = false })
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TrackHeader(audio, adjustOpen, onToggleAdjust = { adjustOpen = !adjustOpen }, onAddTrack = onAddTrack, onClose = onClose)
            if (audio.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val track = audio.current
            if (track == null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Attach a recording to play along, slow it down, change its key or loop a passage.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onAddTrack) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("Add track")
                    }
                }
                return@Column
            }
            WaveformBar(audio, track)
            Transport(audio, track, currentPage)
            if (onTurnPageHere != null) PageTurnRow(audio, onTurnPageHere, onEdit = { editingTurns = true })
            if (adjustOpen) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Adjustments(audio, track)
            }
            Markers(audio, currentPage)
        }
    }
}

@Composable
private fun TrackHeader(audio: AudioController, adjustOpen: Boolean, onToggleAdjust: () -> Unit, onAddTrack: () -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AudioTrack?>(null) }
    var deleting by remember { mutableStateOf<AudioTrack?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menu = true }) {
                TitleText(audio.current?.title ?: "Audio tracks", modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(menu, { menu = false }) {
                audio.tracks.forEach { t ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TitleText(t.title, modifier = Modifier.weight(1f, fill = false))
                                Text("  ·  ${formatTime(t.durationMs)}")
                            }
                        },
                        leadingIcon = { if (t.id == audio.current?.id) Icon(Icons.Outlined.Check, null) },
                        onClick = { menu = false; audio.select(t) },
                    )
                }
                if (audio.tracks.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(text = { Text("Add track…") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }, onClick = { menu = false; onAddTrack() })
                audio.current?.let { cur ->
                    DropdownMenuItem(text = { Text("Rename track") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = cur })
                    DropdownMenuItem(text = { Text("Remove track") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = cur })
                }
            }
        }
        if (audio.current != null) {
            FilterChip(
                selected = audio.followPages,
                onClick = { audio.followPages = !audio.followPages },
                label = { Text("Turn pages") },
                leadingIcon = { Icon(if (audio.followPages) Icons.Outlined.Link else Icons.Outlined.LinkOff, null, Modifier.size(18.dp)) },
            )
            FilledTonalIconToggleButton(checked = adjustOpen, onCheckedChange = { onToggleAdjust() }) { Icon(Icons.Outlined.Tune, "Speed, pitch, pan and volume") }
        }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Hide audio") }
    }
    renaming?.let { t ->
        TextInputDialog("Rename track", "Name", t.title, onDismiss = { renaming = null }) { audio.renameTrack(t, it); renaming = null }
    }
    deleting?.let { t ->
        ConfirmDialog("Remove \"${t.title}\"?", "The recording and its markers will be removed from this score.", "Remove",
            onDismiss = { deleting = null }, onConfirm = { audio.deleteTrack(t); deleting = null })
    }
}

@Composable
private fun WaveformBar(audio: AudioController, track: AudioTrack) {
    val duration = audio.durationMs.coerceAtLeast(1)
    val peaks = audio.waveform
    val played = MaterialTheme.colorScheme.primary
    val unplayed = MaterialTheme.colorScheme.outlineVariant
    val loopColor = MaterialTheme.colorScheme.tertiary
    val markerColor = MaterialTheme.colorScheme.secondary
    Canvas(
        Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)
            .pointerInput(duration) { detectTapGestures { audio.seekTo((it.x / size.width * duration).toLong()) } }
            .pointerInput(duration) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    audio.seekTo((change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * duration).toLong())
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val progressX = audio.positionMs.toFloat() / duration * w
        if (audio.current?.let { audio.hasLoop(it) } == true) {
            val a = track.loopStartMs.toFloat() / duration * w
            val b = track.loopEndMs.toFloat() / duration * w
            drawRoundRect(loopColor.copy(alpha = if (track.loopEnabled) 0.22f else 0.08f), Offset(a, 0f), Size(b - a, h), CornerRadius(6f))
            drawLine(loopColor, Offset(a, 0f), Offset(a, h), strokeWidth = 2.dp.toPx())
            drawLine(loopColor, Offset(b, 0f), Offset(b, h), strokeWidth = 2.dp.toPx())
        } else if (track.loopStartMs >= 0) {
            val a = track.loopStartMs.toFloat() / duration * w
            drawLine(loopColor, Offset(a, 0f), Offset(a, h), strokeWidth = 2.dp.toPx())
        }
        if (peaks != null && peaks.isNotEmpty()) {
            val barW = w / peaks.size
            peaks.forEachIndexed { i, p ->
                val x = i * barW
                val barH = (p.coerceIn(0.03f, 1f)) * h * 0.9f
                drawRect(if (x < progressX) played else unplayed, Offset(x, (h - barH) / 2), Size((barW * 0.7f).coerceAtLeast(1f), barH))
            }
        } else {
            drawRoundRect(unplayed, Offset(0f, h / 2 - 3), Size(w, 6f), CornerRadius(3f))
            drawRoundRect(played, Offset(0f, h / 2 - 3), Size(progressX, 6f), CornerRadius(3f))
        }
        for (m in audio.markers) {
            val x = m.positionMs.toFloat() / duration * w
            drawLine(markerColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5.dp.toPx())
            drawCircle(markerColor, 3.dp.toPx(), Offset(x, 3.dp.toPx()))
        }
        drawLine(Color.Black.copy(alpha = 0.7f), Offset(progressX, 0f), Offset(progressX, h), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun Transport(audio: AudioController, track: AudioTrack, currentPage: Int) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${formatTime(audio.positionMs)} / ${formatTime(audio.durationMs)}",
            style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp),
        )
        IconButton(onClick = { audio.skip(-5_000) }) { Icon(Icons.Outlined.Replay5, "Back 5 seconds") }
        FilledIconButton(onClick = audio::togglePlay, modifier = Modifier.size(48.dp)) {
            Icon(if (audio.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (audio.isPlaying) "Pause" else "Play")
        }
        IconButton(onClick = { audio.skip(5_000) }) { Icon(Icons.Outlined.Forward5, "Forward 5 seconds") }
        Spacer(Modifier.width(8.dp))
        // A–B repeat
        OutlinedButton(onClick = audio::setLoopStart, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            Text(if (track.loopStartMs >= 0) "A ${formatTime(track.loopStartMs)}" else "Set A")
        }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = audio::setLoopEnd, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            Text(if (track.loopEndMs >= 0) "B ${formatTime(track.loopEndMs)}" else "Set B")
        }
        FilledTonalIconToggleButton(
            checked = track.loopEnabled,
            enabled = audio.hasLoop(track),
            onCheckedChange = { audio.toggleLoop() },
        ) { Icon(Icons.Outlined.Repeat, if (track.loopEnabled) "Stop repeating A–B" else "Repeat A–B") }
        if (track.loopStartMs >= 0) {
            IconButton(onClick = audio::clearLoop) { Icon(Icons.Outlined.Close, "Clear A–B") }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { audio.addMarker(currentPage) }) { Icon(Icons.Outlined.BookmarkAdd, "Add marker here (linked to this page)") }
    }
}

@Composable
private fun Adjustments(audio: AudioController, track: AudioTrack) {
    Column {
        // Speed: tempo changes, pitch stays.
        SettingRow(
            label = "Speed",
            value = "${(track.speed * 100).roundToInt()}%",
            onReset = if (track.speed != 1f) ({ audio.setSpeed(1f) }) else null,
        ) {
            IconButton(onClick = { audio.setSpeed((track.speed - 0.05f).coerceAtLeast(0.25f)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Slider(track.speed, { audio.setSpeed(it) }, valueRange = 0.25f..2f, modifier = Modifier.weight(1f))
            IconButton(onClick = { audio.setSpeed((track.speed + 0.05f).coerceAtMost(2f)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        // Pitch: key changes, tempo stays. Semitone steps plus a ±50 cent fine tune.
        val semis = Math.floorDiv(track.pitchCents + 50, 100)
        val cents = track.pitchCents - semis * 100
        SettingRow(
            label = "Pitch",
            value = buildString {
                append(if (semis > 0) "+$semis" else "$semis"); append(" st")
                if (cents != 0) { append(if (cents > 0) " +$cents¢" else " $cents¢") }
            },
            onReset = if (track.pitchCents != 0) ({ audio.setPitchCents(0) }) else null,
        ) {
            IconButton(onClick = { audio.setPitchCents(track.pitchCents - 100) }) { Text("♭", style = MaterialTheme.typography.titleLarge) }
            Slider(cents.toFloat(), { audio.setPitchCents(semis * 100 + it.roundToInt()) }, valueRange = -50f..50f, modifier = Modifier.weight(1f))
            IconButton(onClick = { audio.setPitchCents(track.pitchCents + 100) }) { Text("♯", style = MaterialTheme.typography.titleLarge) }
        }
        SettingRow(
            label = "Pan",
            value = when {
                abs(track.pan) < 0.02f -> "Centre"
                track.pan < 0 -> "L ${(-track.pan * 100).roundToInt()}%"
                else -> "R ${(track.pan * 100).roundToInt()}%"
            },
            onReset = if (track.pan != 0f) ({ audio.setPan(0f) }) else null,
        ) {
            Text("L", Modifier.padding(horizontal = 12.dp))
            Slider(track.pan, { audio.setPan(if (abs(it) < 0.04f) 0f else it) }, valueRange = -1f..1f, modifier = Modifier.weight(1f))
            Text("R", Modifier.padding(horizontal = 12.dp))
        }
        SettingRow(
            label = "Volume",
            value = "${(track.volume * 100).roundToInt()}%",
            onReset = if (track.volume != 1f) ({ audio.setVolume(1f) }) else null,
        ) {
            Spacer(Modifier.width(36.dp))
            Slider(track.volume, { audio.setVolume(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onReset: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(92.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        Box(Modifier.width(64.dp)) {
            if (onReset != null) TextButton(onClick = onReset) { Text("Reset") }
        }
    }
}

/** Syncing page turns to the recording: one at a time, or recorded live while playing. */
@Composable
private fun PageTurnRow(audio: AudioController, onTurnPageHere: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onTurnPageHere, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardTab, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Turn page here")
        }
        val turns = audio.pageTurns.size
        if (turns > 0) {
            OutlinedButton(onClick = onEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit turns ($turns)")
            }
        }
        FilterChip(
            selected = audio.recordTurns,
            onClick = { audio.recordTurns = !audio.recordTurns },
            label = { Text(if (audio.recordTurns) "Recording page turns…" else "Record turns") },
            leadingIcon = { Icon(Icons.Outlined.FiberManualRecord, null, Modifier.size(16.dp), tint = if (audio.recordTurns) Color(0xFFD32F2F) else LocalContentColor.current) },
        )
        Text(
            if (audio.recordTurns) "Play, and turn pages as you normally would."
            else "Page turns are saved as markers; “Turn pages” replays them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Markers(audio: AudioController, currentPage: Int) {
    if (audio.markers.isEmpty()) return
    var menuFor by remember { mutableStateOf<AudioMarker?>(null) }
    var renaming by remember { mutableStateOf<AudioMarker?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        audio.markers.forEach { m ->
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.combinedClickable(onClick = { audio.seekTo(m.positionMs) }, onLongClick = { menuFor = m }),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bookmark, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        TitleText(m.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(max = 220.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatTime(m.positionMs) + if (m.page >= 0) " · p${m.page + 1}" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(menuFor == m, { menuFor = null }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuFor = null; renaming = m })
                    DropdownMenuItem(
                        text = { Text("Link to page ${currentPage + 1}") }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        onClick = { menuFor = null; audio.linkMarker(m, currentPage) },
                    )
                    if (m.page >= 0) {
                        DropdownMenuItem(text = { Text("Unlink page") }, leadingIcon = { Icon(Icons.Outlined.LinkOff, null) }, onClick = { menuFor = null; audio.linkMarker(m, -1) })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menuFor = null; audio.deleteMarker(m) })
                }
            }
        }
    }
    renaming?.let { m ->
        TextInputDialog("Rename marker", "Name", m.label, onDismiss = { renaming = null }) { audio.renameMarker(m, it); renaming = null }
    }
}
