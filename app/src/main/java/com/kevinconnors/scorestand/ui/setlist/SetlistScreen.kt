package com.kevinconnors.scorestand.ui.setlist

import com.kevinconnors.scorestand.ui.TitleText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.kevinconnors.scorestand.data.Score
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.TextInputDialog
import com.kevinconnors.scorestand.ui.library.ScoreThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(setlistId: Long, onBack: () -> Unit, onPlay: (scoreId: Long, index: Int) -> Unit) {
    val vm: SetlistViewModel = viewModel(
        key = "setlist-$setlistId",
        factory = viewModelFactory { initializer { SetlistViewModel(this[APPLICATION_KEY]!!, setlistId) } },
    )
    val setlist by vm.setlist.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val allScores by vm.allScores.collectAsStateWithLifecycle()

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TitleText(setlist?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(enabled = items.isNotEmpty(), onClick = { onPlay(items.first().score.id, 0) }) {
                        Icon(Icons.Outlined.PlayArrow, "Perform setlist")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete setlist") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add scores") })
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("This setlist is empty.\nTap Add scores to fill it.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(items, key = { _, it -> it.entryId }) { index, item ->
                    ListItem(
                        modifier = Modifier.animateItem().clickable { onPlay(item.score.id, index) },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium)
                                ScoreThumbnail(vm.thumbnailFor(item.score), Modifier.size(40.dp, 52.dp).clip(RoundedCornerShape(4.dp)))
                            }
                        },
                        headlineContent = { TitleText(item.score.title) },
                        supportingContent = { if (item.score.composer.isNotBlank()) Text(item.score.composer) },
                        trailingContent = {
                            Row {
                                IconButton(enabled = index > 0, onClick = { vm.move(index, index - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                                IconButton(enabled = index < items.lastIndex, onClick = { vm.move(index, index + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { vm.remove(item) }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove from setlist") }
                            }
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog("Rename setlist", "Name", setlist?.name ?: "", onDismiss = { renaming = false }) {
            vm.rename(it); renaming = false
        }
    }
    if (deleting) {
        ConfirmDialog(
            "Delete setlist?", "The scores stay in your library.", "Delete",
            onDismiss = { deleting = false },
            onConfirm = { vm.delete(); deleting = false; onBack() },
        )
    }
    if (adding) {
        AddScoresDialog(allScores, onDismiss = { adding = false }) { ids -> vm.addScores(ids); adding = false }
    }
}

@Composable
private fun AddScoresDialog(scores: List<Score>, onDismiss: () -> Unit, onAdd: (List<Long>) -> Unit) {
    val selected = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add scores") },
        text = {
            if (scores.isEmpty()) {
                Text("Your library is empty. Import scores first.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(scores, key = { it.id }) { s ->
                        val checked = s.id in selected
                        ListItem(
                            headlineContent = { TitleText(s.title) },
                            supportingContent = { if (s.composer.isNotBlank()) Text(s.composer) },
                            leadingContent = { Checkbox(checked, null) },
                            modifier = Modifier.clickable { if (checked) selected.remove(s.id) else selected.add(s.id) },
                        )
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
