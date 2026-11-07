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
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

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
                    onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
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
                )
            } else {
                SetlistsTab(setlists, onOpenSetlist)
            }
        }
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
                if (filtering) "No scores match." else "Your library is empty.\nTap Import to add PDFs or photos of sheet music.",
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
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Card(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            ScoreThumbnail(vm.thumbnailFor(score), Modifier.fillMaxWidth().aspectRatio(0.75f))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(score.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = listOf(score.composer, "${score.pageCount} page${if (score.pageCount == 1) "" else "s"}")
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Edit details") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Add to setlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }, onClick = { menu = false; onAddToSetlist() })
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
