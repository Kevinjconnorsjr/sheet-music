package com.kevinconnors.scorestand.ui.playlist

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.playlist.PlaylistPlayer
import com.kevinconnors.scorestand.playlist.formatCountdown
import kotlinx.coroutines.delay

/** "Sleep timer" card on Now playing: set, see the countdown, add time, or turn off (as in FoldPlayer). */
@OptIn(UnstableApi::class)
@Composable
fun SleepTimerCard(player: PlaylistPlayer) {
    val sleep = player.sleep
    var choosing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep?.endsAt != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { choosing = true }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bedtime, null, tint = if (sleep != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        sleep == null -> "Off"
                        sleep.endOfTrack -> "Pausing after this track"
                        else -> "Pausing in ${formatCountdown((sleep.endsAt ?: now) - now)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sleep != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sleep?.endsAt != null) TextButton(onClick = { player.extendSleepTimer(5) }) { Text("+5 min") }
            if (sleep != null) TextButton(onClick = player::cancelSleepTimer) { Text("Turn off") }
            else TextButton(onClick = { choosing = true }) { Text("Set") }
        }
    }
    if (choosing) {
        SleepTimerDialog(
            onDismiss = { choosing = false },
            onMinutes = { player.startSleepTimer(it); choosing = false },
            onEndOfTrack = { player.sleepAtEndOfTrack(); choosing = false },
        )
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onMinutes: (Int) -> Unit, onEndOfTrack: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    val customMinutes = custom.toIntOrNull()?.takeIf { it in 1..600 }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Bedtime, null) },
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text("Pause playback after:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(Modifier.padding(vertical = 8.dp)) {
                    listOf(5, 10, 15, 30, 45, 60, 90, 120).forEach { m ->
                        OutlinedButton(onClick = { onMinutes(m) }, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)) {
                            Text(if (m < 60 || m % 60 != 0) "$m min" else "${m / 60} h")
                        }
                    }
                }
                OutlinedButton(onClick = onEndOfTrack, modifier = Modifier.fillMaxWidth()) { Text("End of this track") }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { customMinutes?.let(onMinutes) }, enabled = customMinutes != null) { Text("Start") }
                }
                Text(
                    "The music fades out over the last 20 seconds. Until then the playlist keeps looping.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
