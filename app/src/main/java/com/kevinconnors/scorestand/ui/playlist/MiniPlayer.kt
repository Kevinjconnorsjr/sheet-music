package com.kevinconnors.scorestand.ui.playlist

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.ScoreStandApp
import com.kevinconnors.scorestand.ui.TitleText

/** A bar with the playing track, shown at the bottom of the library and playlists; tap for Now playing. */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val p = (LocalContext.current.applicationContext as ScoreStandApp).playlistPlayer
    val track = p.current ?: return
    Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TitleText(track.title, style = MaterialTheme.typography.titleSmall)
                    p.queue.getOrNull(p.index)?.let { TitleText("${it.scoreTitle} · ${p.playlistName.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = p::togglePlay) { Icon(if (p.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (p.isPlaying) "Pause" else "Play") }
                IconButton(onClick = p::next) { Icon(Icons.Outlined.SkipNext, "Next") }
                IconButton(onClick = p::stop) { Icon(Icons.Outlined.Close, "Stop and close") }
            }
        }
    }
}
