package com.kevinconnors.scorestand.ui.playlist

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.data.ListeningPlaylist
import com.kevinconnors.scorestand.data.ListeningPlaylistWithCount
import com.kevinconnors.scorestand.data.TrackWithScore
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.LocalAskForNotifications
import com.kevinconnors.scorestand.ui.TextInputDialog
import com.kevinconnors.scorestand.ui.TitleText
import com.kevinconnors.scorestand.ui.reader.audio.formatTime

/** The library's "Playlists" tab: listening playlists of learning tracks. */
@Composable
fun ListeningPlaylistsTab(playlists: List<ListeningPlaylistWithCount>, onOpen: (Long) -> Unit) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No playlists yet.\nTap New playlist to gather learning tracks from your scores and listen to them in a loop.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(playlists, key = { it.playlist.id }) { item ->
            ListItem(
                headlineContent = { TitleText(item.playlist.name) },
                supportingContent = { Text("${item.count} track${if (item.count == 1) "" else "s"}") },
                leadingContent = { Icon(Icons.Outlined.GraphicEq, null) },
                modifier = Modifier.clickable { onOpen(item.playlist.id) },
            )
        }
    }
}

/** One listening playlist: play or shuffle it, add tracks, reorder or remove them. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun ListeningPlaylistScreen(playlistId: Long, onBack: () -> Unit, onNowPlaying: () -> Unit, vm: ListeningViewModel = viewModel()) {
    val playlist by remember(playlistId) { vm.playlist(playlistId) }.collectAsStateWithLifecycle(null)
    val items by remember(playlistId) { vm.tracks(playlistId) }.collectAsStateWithLifecycle(emptyList())
    val allTracks by vm.allTracks.collectAsStateWithLifecycle()
    val askForNotifications = LocalAskForNotifications.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val name = playlist?.name.orEmpty()

    fun play(index: Int, shuffled: Boolean = false) {
        askForNotifications()
        vm.player.playPlaylist(playlistId, name, index, shuffled)
        onNowPlaying()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TitleText(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add tracks") })
        },
        bottomBar = { MiniPlayer(onOpen = onNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = items.isNotEmpty(), onClick = { play(0) }) {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                OutlinedButton(enabled = items.size > 1, onClick = { play((items.indices).random(), shuffled = true) }) {
                    Icon(Icons.Outlined.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                }
            }
            Text(
                "Plays in a loop until you stop it (or set a sleep timer). Each track keeps its own speed, pitch, pan and A–B loop.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("This playlist is empty.\nTap Add tracks to choose learning tracks from your scores.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(items, key = { _, it -> it.entryId }) { index, item ->
                        val playingThis = vm.player.playlistId == playlistId && vm.player.queue.getOrNull(vm.player.index)?.entryId == item.entryId
                        ListItem(
                            modifier = Modifier.animateItem().clickable { play(index) },
                            leadingContent = {
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium,
                                    color = if (playingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            },
                            headlineContent = { TitleText(item.track.title, color = if (playingThis) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified) },
                            supportingContent = { TitleText("${item.scoreTitle} · ${formatTime(item.track.durationMs)}") },
                            trailingContent = {
                                Row {
                                    IconButton(enabled = index > 0, onClick = { vm.move(playlistId, items, index, index - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                                    IconButton(enabled = index < items.lastIndex, onClick = { vm.move(playlistId, items, index, index + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                                    IconButton(onClick = { vm.remove(playlistId, items, item) }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove from playlist") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) TextInputDialog("Rename playlist", "Name", name, onDismiss = { renaming = false }) { n -> playlist?.let { vm.rename(it, n) }; renaming = false }
    if (deleting) {
        ConfirmDialog("Delete \"$name\"?", "The tracks stay with their scores.", "Delete",
            onDismiss = { deleting = false }, onConfirm = { playlist?.let(vm::delete); deleting = false; onBack() })
    }
    if (adding) {
        AddTracksDialog(allTracks, already = items.map { it.track.id }.toSet(), onDismiss = { adding = false }) { ids ->
            vm.addTracks(playlistId, ids); adding = false
        }
    }
}

/** Choose learning tracks from every score, grouped by score; "all" ticks a score's tracks at once. */
@Composable
fun AddTracksDialog(tracks: List<TrackWithScore>, already: Set<Long>, onDismiss: () -> Unit, onAdd: (List<Long>) -> Unit) {
    val selected = remember { mutableStateListOf<Long>() }
    val byScore = tracks.groupBy { it.track.scoreId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tracks") },
        text = {
            if (tracks.isEmpty()) {
                Text("No audio tracks yet. Add recordings to a score first (Import, or 🎧 in a score).")
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    byScore.values.forEach { group ->
                        item(key = "score-${group.first().track.scoreId}") {
                            val ids = group.map { it.track.id }
                            val allOn = ids.all { it in selected }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TitleText(group.first().scoreTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { if (allOn) selected.removeAll(ids) else ids.filter { it !in selected }.forEach(selected::add) }) {
                                    Text(if (allOn) "None" else "All")
                                }
                            }
                        }
                        items(group, key = { it.track.id }) { t ->
                            val checked = t.track.id in selected
                            Row(
                                Modifier.fillMaxWidth().clickable { if (checked) selected.remove(t.track.id) else selected.add(t.track.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, null)
                                Column(Modifier.weight(1f)) {
                                    TitleText(t.track.title)
                                    if (t.track.id in already) Text("Already in this playlist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatTime(t.track.durationMs), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onAdd(selected.toList()) }) {
                Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick a listening playlist (or make a new one) for some tracks. */
@Composable
fun PickListeningPlaylistDialog(playlists: List<ListeningPlaylist>, onDismiss: () -> Unit, onPick: (ListeningPlaylist) -> Unit, onNew: (String) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TextInputDialog("New playlist", "Name", "", confirmLabel = "Create", onDismiss = { naming = false; onDismiss() }) { onNew(it) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("New playlist…") },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                        modifier = Modifier.clickable { naming = true },
                    )
                }
                items(playlists, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { TitleText(p.name) },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
