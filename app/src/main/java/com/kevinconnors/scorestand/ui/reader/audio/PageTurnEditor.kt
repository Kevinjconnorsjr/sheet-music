package com.kevinconnors.scorestand.ui.reader.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kevinconnors.scorestand.data.AudioMarker
import com.kevinconnors.scorestand.ui.ConfirmDialog

/**
 * Lists the page turns recorded on the current track and lets each one be fine-tuned:
 * its moment (nudge, or "now"), the page it turns to, a quick preview, or removal.
 */
@Composable
fun PageTurnEditor(audio: AudioController, pageCount: Int, onClose: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Page turns", style = MaterialTheme.typography.titleLarge)
                        Text(audio.current?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Transport here too, so timings can be checked without leaving the editor.
                    Text(PageTurns.formatPrecise(audio.positionMs), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = audio::togglePlay) {
                        Icon(if (audio.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (audio.isPlaying) "Pause" else "Play")
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
                }
                Text(
                    "Each turn happens at the time shown. Nudge it earlier (−) or later (+), set it to the moment " +
                        "now playing, or change the page it turns to. ▶ plays from just before the turn.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val turns = audio.pageTurns
                if (turns.isEmpty()) {
                    Text("No page turns on this track yet.", Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(turns, key = { _, m -> m.id }) { i, m -> TurnRow(i, m, audio, pageCount) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shift all", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        NudgeButton("−1s") { audio.shiftAllTurns(-1_000) }
                        NudgeButton("−0.25") { audio.shiftAllTurns(-250) }
                        NudgeButton("+0.25") { audio.shiftAllTurns(250) }
                        NudgeButton("+1s") { audio.shiftAllTurns(1_000) }
                    }
                    TextButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove all page turns")
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            "Remove all page turns?", "Other markers on this track are kept.", "Remove",
            onDismiss = { confirmClear = false },
            onConfirm = { audio.clearAllTurns(); confirmClear = false },
        )
    }
}

@Composable
private fun TurnRow(index: Int, m: AudioMarker, audio: AudioController, pageCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                Text(PageTurns.formatPrecise(m.positionMs), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text("→ page", style = MaterialTheme.typography.bodyMedium)
                IconButton(enabled = m.page > 0, onClick = { audio.setTurnPage(m, m.page - 1, pageCount) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("${m.page + 1}", style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = m.page < pageCount - 1, onClick = { audio.setTurnPage(m, m.page + 1, pageCount) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { audio.previewTurn(m) }) { Icon(Icons.Outlined.PlayArrow, "Play from just before this turn") }
                IconButton(onClick = { audio.deleteMarker(m) }) { Icon(Icons.Outlined.Delete, "Remove this turn") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NudgeButton("−1s") { audio.nudgeTurn(m, -1_000) }
                NudgeButton("−0.1") { audio.nudgeTurn(m, -100) }
                NudgeButton("+0.1") { audio.nudgeTurn(m, 100) }
                NudgeButton("+1s") { audio.nudgeTurn(m, 1_000) }
                FilledTonalButton(onClick = { audio.setTurnTime(m, audio.positionMs) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Outlined.MyLocation, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Now")
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 36.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
