package com.kevinconnors.scorestand.ui.reader.markup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kevinconnors.scorestand.data.Markup
import com.kevinconnors.scorestand.data.MarkupLayer
import com.kevinconnors.scorestand.data.MarkupType
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.TextInputDialog

/** Material's icon set has no eraser, so this is a small custom one. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M15.1,3.5c0.8,-0.8 2,-0.8 2.8,0l2.6,2.6c0.8,0.8 0.8,2 0,2.8L11.4,18H20v2H8.2c-0.5,0 -1,-0.2 -1.4,-0.6" +
                "l-3.3,-3.3c-0.8,-0.8 -0.8,-2 0,-2.8ZM5,14.6l3.2,3.2c0.1,0.1 0.2,0.2 0.4,0.2h0.1l2.3,-2.3L7.3,12.1Z"
        ),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black),
    ).build()
}

private fun Tool.icon(): ImageVector = when (this) {
    Tool.PAN -> Icons.Outlined.PanTool
    Tool.PEN -> Icons.Outlined.Draw
    Tool.HIGHLIGHTER -> Icons.Outlined.Highlight
    Tool.ERASER -> EraserIcon
    Tool.TEXT -> Icons.Outlined.TextFields
    Tool.STAMP -> Icons.Outlined.Approval
    Tool.LINK -> Icons.AutoMirrored.Outlined.Shortcut
}

@Composable
fun MarkupToolbar(
    session: MarkupSession,
    settings: ToolSettings,
    currentPage: Int,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onDone: () -> Unit,
) {
    var showLayers by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val tool = settings.tool
    val activeLayer = session.layers.firstOrNull { it.id == session.activeLayerId }

    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Tool.entries.forEach { t ->
                    FilledIconToggleButton(checked = tool == t, onCheckedChange = { settings.tool = t }) {
                        Icon(t.icon(), t.label)
                    }
                }
                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 6.dp))
                // Swipes draw while a pen is selected, so page turns need buttons (pedals work too).
                IconButton(onClick = onPreviousPage) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page") }
                Text("${currentPage + 1}/$pageCount", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onNextPage) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page") }
                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 6.dp))
                IconButton(enabled = session.canUndo, onClick = session::undo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
                IconButton(enabled = session.canRedo, onClick = session::redo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear this page") }
                AssistChip(
                    onClick = { showLayers = true },
                    label = { Text(activeLayer?.name ?: "Layers", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onDone) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done")
                }
            }
            Spacer(Modifier.height(4.dp))
            ToolOptions(settings, session)
        }
    }

    if (showLayers) LayersDialog(session, onDismiss = { showLayers = false })
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear page ${currentPage + 1}?",
            message = "Removes the marks on this page in \"${activeLayer?.name}\". You can undo this.",
            confirmLabel = "Clear",
            onDismiss = { confirmClear = false },
            onConfirm = { session.clearPage(currentPage); confirmClear = false },
        )
    }
}

@Composable
private fun ToolOptions(settings: ToolSettings, session: MarkupSession) {
    val tool = settings.tool
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (tool) {
            Tool.PAN -> Text(
                "Swipe or scroll as usual. Pick a tool to mark up the music.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Tool.LINK -> Text(
                if (session.pendingLinkFrom == null) "Tap where the jump starts (e.g. a repeat sign, D.S. or “to Coda”). Tap a badge to remove it."
                else "Now go to where it jumps to and tap that spot.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Tool.ERASER -> {
                SizePicker(settings, tool)
                Text("Erases marks on the current layer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val colors = if (tool == Tool.HIGHLIGHTER) Palette.highlighter else Palette.pen
                colors.forEach { c ->
                    val selected = settings.color(tool) == c
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clickable { if (tool == Tool.HIGHLIGHTER) settings.highlighterColor = c else settings.penColor = c },
                    )
                }
                VerticalDivider(Modifier.height(28.dp))
                SizePicker(settings, tool)
            }
        }
    }
    if (tool == Tool.STAMP) {
        Spacer(Modifier.height(4.dp))
        StampPicker(settings)
    }
}

@Composable
private fun SizePicker(settings: ToolSettings, tool: Tool) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(8.dp, 14.dp, 20.dp).forEachIndexed { i, dot ->
            val selected = settings.sizeIndex(tool) == i
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { settings.setSizeIndex(tool, i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(dot).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

@Composable
private fun StampPicker(settings: ToolSettings) {
    val measurer = rememberTextMeasurer()
    val ink = Color(settings.penColor)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(Stamp.entries) { stamp ->
            val selected = settings.stamp == stamp
            Box(
                Modifier.size(width = if (stamp.halfWidth > 1f) 72.dp else 48.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.LightGray, RoundedCornerShape(8.dp))
                    .clickable { settings.stamp = stamp },
            ) {
                Canvas(Modifier.size(width = if (stamp.halfWidth > 1f) 72.dp else 48.dp, height = 48.dp)) {
                    val s = size.height * if (stamp.halfWidth > 1f) 0.36f else 0.55f
                    drawStamp(stamp, center, s, ink, measurer)
                }
            }
        }
    }
}

@Composable
private fun LayersDialog(session: MarkupSession, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<MarkupLayer?>(null) }
    var deleting by remember { mutableStateOf<MarkupLayer?>(null) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layers") },
        text = {
            Column {
                Text(
                    "New marks go on the selected layer. Hide a layer to see the music without it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(session.layers, key = { it.id }) { layer ->
                        val count = session.items.count { it.layerId == layer.id }
                        ListItem(
                            modifier = Modifier.clickable { session.setActiveLayer(layer.id) },
                            leadingContent = { RadioButton(selected = layer.id == session.activeLayerId, onClick = { session.setActiveLayer(layer.id) }) },
                            headlineContent = { Text(layer.name) },
                            supportingContent = { Text("$count mark${if (count == 1) "" else "s"}") },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { session.setVisible(layer, !layer.visible) }) {
                                        Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (layer.visible) "Hide layer" else "Show layer")
                                    }
                                    IconButton(onClick = { renaming = layer }) { Icon(Icons.Outlined.Edit, "Rename layer") }
                                    if (session.layers.size > 1) {
                                        IconButton(onClick = { deleting = layer }) { Icon(Icons.Outlined.Delete, "Delete layer") }
                                    }
                                }
                            },
                        )
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("New layer")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    renaming?.let { layer ->
        TextInputDialog("Rename layer", "Name", layer.name, onDismiss = { renaming = null }) {
            session.renameLayer(layer, it); renaming = null
        }
    }
    deleting?.let { layer ->
        ConfirmDialog(
            "Delete \"${layer.name}\"?", "All marks on this layer will be deleted. This can't be undone.", "Delete",
            onDismiss = { deleting = null },
            onConfirm = { session.deleteLayer(layer); deleting = null },
        )
    }
    if (adding) {
        TextInputDialog("New layer", "Name", "", confirmLabel = "Create", onDismiss = { adding = false }) {
            session.addLayer(it); adding = false
        }
    }
}

/** Asks for the words of a text note, then places it where the user tapped. */
@Composable
fun TextNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = { OutlinedTextField(text, { text = it }, label = { Text("Note") }, minLines = 1, maxLines = 4) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trimEnd()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun textMarkup(session: MarkupSession, settings: ToolSettings, page: Int, x: Float, y: Float, text: String) = Markup(
    id = session.newId(), scoreId = session.scoreId, layerId = session.activeLayerId, page = page,
    type = MarkupType.TEXT, color = settings.penColor, size = settings.size(Tool.TEXT), x = x, y = y, text = text,
)

private val linkPresets = listOf("Repeat", "D.S.", "D.C.", "To Coda", "Coda", "Fine", "1st ending", "2nd ending", "Jump")

/** Names a new jump link; the label is shown on its badge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkLabelDialog(toPage: Int, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var label by remember { mutableStateOf("Repeat") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to page ${toPage + 1}") },
        text = {
            Column {
                Text("What kind of jump is it?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    linkPresets.forEach { p ->
                        androidx.compose.material3.FilterChip(selected = label == p, onClick = { label = p }, label = { Text(p) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(label, { label = it.take(20) }, label = { Text("Label") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onCreate(label.trim()) }) { Text("Create link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
