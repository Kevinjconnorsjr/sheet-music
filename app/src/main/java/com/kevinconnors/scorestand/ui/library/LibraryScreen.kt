package com.kevinconnors.scorestand.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.ui.platform.LocalContext
import com.kevinconnors.scorestand.ui.tools.TunerDialog
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinconnors.scorestand.data.Score
import com.kevinconnors.scorestand.data.SetlistWithCount
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.EditScoreDialog
import com.kevinconnors.scorestand.ui.PickSetlistDialog
import com.kevinconnors.scorestand.ui.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenScore: (Long) -> Unit,
    onOpenSetlist: (Long) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val scores by vm.scores.collectAsStateWithLifecycle()
    val tags by vm.allTags.collectAsStateWithLifecycle()
    val setlists by vm.setlists.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val tagFilter by vm.tagFilter.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showNewSetlist by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // Recordings for a score: chosen from its menu or offered right after importing it.
    var tracksFor by remember { mutableStateOf<Score?>(null) }
    val trackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        tracksFor?.let { vm.attachTracks(it, uris) }
        tracksFor = null
    }
    val launchTrackPicker: (Score) -> Unit = { score -> tracksFor = score; trackPicker.launch(arrayOf("audio/*")) }
    LaunchedEffect(Unit) {
        vm.trackOffers.collect { score ->
            val result = snackbar.showSnackbar(
                "Added \"${score.title}\"", actionLabel = "Add audio tracks", withDismissAction = true,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) launchTrackPicker(score)
        }
    }
    val pendingTracks by vm.pendingTracks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.shares.collect { context.startActivity(it) } }
    var moreMenu by remember { mutableStateOf(false) }
    var showTuner by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::backup)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ScoreStand") },
                    actions = {
                        if (tab == 0) {
                            IconButton(onClick = {
                                searching = !searching
                                if (!searching) vm.query.value = ""
                            }) {
                                Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "Search")
                            }
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            leadingIcon = { if (order == sort) Icon(Icons.Outlined.Check, null) },
                                            onClick = { vm.sort.value = order; sortMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(moreMenu, { moreMenu = false }) {
                                DropdownMenuItem(text = { Text("Tuner") }, leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) }, onClick = { moreMenu = false; showTuner = true })
                                DropdownMenuItem(
                                    text = { Text("Back up library…") }, leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                                    onClick = { moreMenu = false; backupPicker.launch("ScoreStand-backup-${java.time.LocalDate.now()}.zip") },
                                )
                                DropdownMenuItem(
                                    text = { Text("Restore from backup…") }, leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                                    onClick = { moreMenu = false; restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                )
                            }
                        }
                    },
                )
                if (searching && tab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("Search title, composer or tag") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Scores") }, icon = { Icon(Icons.Outlined.LibraryMusic, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Setlists") }, icon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) })
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // MusicXML often arrives as plain XML or a generic file type, so allow those
                        // too; anything that isn't a score is rejected with a message.
                        picker.launch(arrayOf(
                            "application/pdf", "image/*",
                            "application/vnd.recordare.musicxml+xml", "application/vnd.recordare.musicxml",
                            "application/xml", "text/xml", "application/octet-stream", "application/zip",
                            // Recordings picked together with a score are saved with it.
                            "audio/*",
                        ))
                    },
                    icon = { Icon(Icons.Outlined.UploadFile, null) },
                    text = { Text("Import") },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showNewSetlist = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("New setlist") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) {
                ScoresTab(
                    vm = vm,
                    scores = scores,
                    tags = tags,
                    tagFilter = tagFilter,
                    filtering = query.isNotBlank() || tagFilter != null,
                    onOpen = onOpenScore,
                    onAddTracks = launchTrackPicker,
                )
            } else {
                SetlistsTab(setlists, onOpenSetlist)
            }
        }
    }

    if (showTuner) TunerDialog(onDismiss = { showTuner = false })
    pendingTracks?.let { (scores, uris) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.pendingTracks.value = null },
            title = { Text("Which score are the ${uris.size} recording${if (uris.size == 1) "" else "s"} for?") },
            text = {
                LazyColumn {
                    items(scores, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title) },
                            supportingContent = { if (s.composer.isNotBlank()) Text(s.composer) },
                            modifier = Modifier.clickable { vm.pendingTracks.value = null; vm.attachTracks(s, uris) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.pendingTracks.value = null }) { Text("Skip") } },
        )
    }
    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "Everything in your library now — scores, markings, audio and setlists — will be replaced by the backup. The app will restart.",
            confirmLabel = "Replace & restart",
            onDismiss = { restoreUri = null },
            onConfirm = { vm.restore(uri); restoreUri = null },
        )
    }

    if (showNewSetlist) {
        TextInputDialog(
            title = "New setlist", label = "Name", confirmLabel = "Create",
            onDismiss = { showNewSetlist = false },
            onConfirm = { vm.createSetlist(it); showNewSetlist = false },
        )
    }
}

@Composable
private fun ScoresTab(
    vm: LibraryViewModel,
    scores: List<Score>,
    tags: List<String>,
    tagFilter: String?,
    filtering: Boolean,
    onOpen: (Long) -> Unit,
    onAddTracks: (Score) -> Unit,
) {
    var editing by remember { mutableStateOf<Score?>(null) }
    var deleting by remember { mutableStateOf<Score?>(null) }
    var addingToSetlist by remember { mutableStateOf<Score?>(null) }
    var newSetlistFor by remember { mutableStateOf<Score?>(null) }
    val setlists by vm.setlists.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags) { tag ->
                    FilterChip(
                        selected = tag == tagFilter,
                        onClick = { vm.tagFilter.value = if (tag == tagFilter) null else tag },
                        label = { Text(tag) },
                    )
                }
            }
        }
        if (scores.isEmpty()) {
            EmptyState(
                if (filtering) "No scores match." else "Your library is empty.\nTap Import to add PDFs, photos of sheet music, or MusicXML files.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(scores, key = { it.id }) { score ->
                    ScoreCard(
                        score = score,
                        vm = vm,
                        onOpen = { onOpen(score.id) },
                        onEdit = { editing = score },
                        onDelete = { deleting = score },
                        onAddToSetlist = { addingToSetlist = score },
                        onShare = { vm.exportPdf(score) },
                        onAddTracks = { onAddTracks(score) },
                    )
                }
            }
        }
    }

    editing?.let { s ->
        EditScoreDialog(s, onDismiss = { editing = null }, onSave = { vm.update(it); editing = null })
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete score?",
            message = "\"${s.title}\" will be removed from your library and all setlists.",
            confirmLabel = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(s); deleting = null },
        )
    }
    addingToSetlist?.let { s ->
        PickSetlistDialog(
            setlists = setlists.map { it.setlist },
            onDismiss = { addingToSetlist = null },
            onPick = { vm.addToSetlist(it, s); addingToSetlist = null },
            onCreateNew = { newSetlistFor = s; addingToSetlist = null },
        )
    }
    newSetlistFor?.let { s ->
        TextInputDialog(
            title = "New setlist", label = "Name", confirmLabel = "Create",
            onDismiss = { newSetlistFor = null },
            onConfirm = { vm.createSetlist(it, s); newSetlistFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoreCard(
    score: Score,
    vm: LibraryViewModel,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToSetlist: () -> Unit,
    onShare: () -> Unit,
    onAddTracks: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Card(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            ScoreThumbnail(vm.thumbnailFor(score), Modifier.fillMaxWidth().aspectRatio(0.75f))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(score.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val size = if (score.isNotation()) "MusicXML · ${score.pageCount} bars" else "${score.pageCount} page${if (score.pageCount == 1) "" else "s"}"
                val subtitle = listOf(score.composer, size)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Edit details") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Add audio tracks…") }, leadingIcon = { Icon(Icons.Outlined.Headphones, null) }, onClick = { menu = false; onAddTracks() })
            DropdownMenuItem(text = { Text("Add to setlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }, onClick = { menu = false; onAddToSetlist() })
            DropdownMenuItem(
                text = { Text(if (score.isNotation()) "Share MIDI file" else "Share as PDF") },
                leadingIcon = { Icon(if (score.isNotation()) Icons.Outlined.LibraryMusic else Icons.Outlined.PictureAsPdf, null) },
                onClick = { menu = false; onShare() },
            )
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun SetlistsTab(setlists: List<SetlistWithCount>, onOpen: (Long) -> Unit) {
    if (setlists.isEmpty()) {
        EmptyState("No setlists yet.\nGroup scores for a gig, rehearsal or practice session.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(setlists, key = { it.setlist.id }) { item ->
            ListItem(
                headlineContent = { Text(item.setlist.name) },
                supportingContent = { Text("${item.count} score${if (item.count == 1) "" else "s"}") },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
                modifier = Modifier.clickable { onOpen(item.setlist.id) },
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
