package com.kevinconnors.scorestand.ui.reader.markup

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kevinconnors.scorestand.data.Markup
import com.kevinconnors.scorestand.data.MarkupDao
import com.kevinconnors.scorestand.data.MarkupLayer
import com.kevinconnors.scorestand.data.PageLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

enum class Tool(val label: String) {
    PAN("Scroll / turn pages"),
    PEN("Pen"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser"),
    TEXT("Text"),
    STAMP("Symbols"),
    LINK("Jump links"),
}

object Palette {
    val pen = listOf(0xFF000000, 0xFFD32F2F, 0xFF1565C0, 0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A).map { it.toInt() }
    val highlighter = listOf(0xFFFFEB3B, 0xFF76FF03, 0xFFFF4081, 0xFF40C4FF, 0xFFFFAB40).map { it.toInt() }

    /** Three size choices per tool, in page-width units. */
    fun sizes(tool: Tool): List<Float> = when (tool) {
        Tool.PEN -> listOf(0.0018f, 0.0035f, 0.007f)
        Tool.HIGHLIGHTER -> listOf(0.012f, 0.022f, 0.036f)
        Tool.ERASER -> listOf(0.015f, 0.03f, 0.06f)
        Tool.TEXT -> listOf(0.022f, 0.032f, 0.048f)
        Tool.STAMP -> listOf(0.03f, 0.045f, 0.07f)
        Tool.PAN, Tool.LINK -> listOf(0f)
    }
}

/** The current tool and its options. Shared across pieces in a setlist. */
class ToolSettings {
    var tool by mutableStateOf(Tool.PEN)
    var penColor by mutableIntStateOf(Palette.pen[0])
    var highlighterColor by mutableIntStateOf(Palette.highlighter[0])
    var stamp by mutableStateOf(Stamp.FERMATA)
    private val sizeIndex = mutableStateMapOf<Tool, Int>()

    fun sizeIndex(tool: Tool) = sizeIndex[tool] ?: 1
    fun setSizeIndex(tool: Tool, index: Int) { sizeIndex[tool] = index }
    fun size(tool: Tool) = Palette.sizes(tool).let { it[sizeIndex(tool).coerceIn(0, it.lastIndex)] }

    /** Pen, text and symbols share the "ink" colour; the highlighter has its own. */
    fun color(tool: Tool) = if (tool == Tool.HIGHLIGHTER) highlighterColor else penColor
}

/**
 * In-memory markings for one score, written through to the database.
 * Edits show immediately; database writes run in order in the background.
 */
class MarkupSession(
    private val dao: MarkupDao,
    val scoreId: Long,
    private val scope: CoroutineScope,
) {
    val layers = mutableStateListOf<MarkupLayer>()
    val items = mutableStateListOf<Markup>()
    val links = mutableStateListOf<PageLink>()

    /** Where a jump link being created starts, until its destination is tapped. */
    var pendingLinkFrom by mutableStateOf<LinkStart?>(null)

    data class LinkStart(val page: Int, val x: Float, val y: Float)
    var activeLayerId by mutableLongStateOf(0L)
        private set

    private val visibleLayerIds by derivedStateOf { layers.filter { it.visible }.map { it.id }.toSet() }

    private sealed interface Edit {
        class Added(val items: List<Markup>) : Edit
        class Removed(val items: List<Markup>) : Edit
    }

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val writeLock = Mutex()
    private fun write(block: suspend () -> Unit) {
        scope.launch { writeLock.withLock { block() } }
    }

    suspend fun load() {
        var loadedLayers = dao.layers(scoreId)
        if (loadedLayers.isEmpty()) {
            dao.insertLayer(MarkupLayer(scoreId = scoreId, name = "My markings"))
            loadedLayers = dao.layers(scoreId)
        }
        layers.addAll(loadedLayers)
        activeLayerId = loadedLayers.first().id
        items.addAll(dao.markups(scoreId))
        links.addAll(dao.links(scoreId))
    }

    // ---- jump links ----

    fun addLink(from: LinkStart, toPage: Int, toY: Float, label: String) {
        pendingLinkFrom = null
        scope.launch {
            writeLock.withLock {
                val link = PageLink(
                    scoreId = scoreId, fromPage = from.page, fromX = from.x, fromY = from.y,
                    toPage = toPage, toY = toY, label = label,
                )
                links.add(link.copy(id = dao.insertLink(link)))
            }
        }
    }

    fun deleteLink(link: PageLink) {
        links.removeAll { it.id == link.id }
        write { dao.deleteLink(link.id) }
    }

    fun visibleOn(page: Int): List<Markup> {
        val visible = visibleLayerIds
        return items.filter { it.page == page && it.layerId in visible }
    }

    fun newId(): Long = idSource.incrementAndGet()

    // ---- editing ----

    fun add(markup: Markup) {
        items.add(markup)
        record(Edit.Added(listOf(markup)))
        write { dao.insert(listOf(markup)) }
    }

    /** Removes marks without recording an undo step; call [commitRemoval] at the end of a gesture. */
    fun removeLive(marks: List<Markup>) {
        if (marks.isEmpty()) return
        val ids = marks.map { it.id }.toSet()
        items.removeAll { it.id in ids }
        write { dao.delete(ids.toList()) }
    }

    fun commitRemoval(marks: List<Markup>) {
        if (marks.isNotEmpty()) record(Edit.Removed(marks.toList()))
    }

    /** Clears the active layer's marks on [page]. */
    fun clearPage(page: Int) {
        val marks = items.filter { it.page == page && it.layerId == activeLayerId }
        removeLive(marks)
        commitRemoval(marks)
    }

    private fun record(edit: Edit) {
        undoStack.addLast(edit)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        refreshFlags()
    }

    fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        apply(invert(edit))
        redoStack.addLast(edit)
        refreshFlags()
    }

    fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        apply(edit)
        undoStack.addLast(edit)
        refreshFlags()
    }

    private fun invert(edit: Edit): Edit = when (edit) {
        is Edit.Added -> Edit.Removed(edit.items)
        is Edit.Removed -> Edit.Added(edit.items)
    }

    private fun apply(edit: Edit) {
        // Skip marks whose layer has since been deleted.
        val layerIds = layers.map { it.id }.toSet()
        when (edit) {
            is Edit.Added -> {
                val restore = edit.items.filter { it.layerId in layerIds && items.none { m -> m.id == it.id } }
                items.addAll(restore)
                write { dao.insert(restore) }
            }
            is Edit.Removed -> {
                val ids = edit.items.map { it.id }.toSet()
                items.removeAll { it.id in ids }
                write { dao.delete(ids.toList()) }
            }
        }
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---- layers ----

    fun setActiveLayer(id: Long) {
        activeLayerId = id
        // Drawing on a hidden layer would be confusing, so show it.
        layers.firstOrNull { it.id == id && !it.visible }?.let { setVisible(it, true) }
    }

    fun setVisible(layer: MarkupLayer, visible: Boolean) {
        val updated = layer.copy(visible = visible)
        replaceLayer(updated)
        write { dao.updateLayer(updated) }
    }

    fun renameLayer(layer: MarkupLayer, name: String) {
        val updated = layer.copy(name = name.trim())
        replaceLayer(updated)
        write { dao.updateLayer(updated) }
    }

    fun addLayer(name: String) {
        val position = (layers.maxOfOrNull { it.position } ?: -1) + 1
        scope.launch {
            writeLock.withLock {
                val layer = MarkupLayer(scoreId = scoreId, name = name.trim(), position = position)
                val id = dao.insertLayer(layer)
                layers.add(layer.copy(id = id))
                activeLayerId = id
            }
        }
    }

    fun deleteLayer(layer: MarkupLayer) {
        if (layers.size <= 1) return
        layers.removeAll { it.id == layer.id }
        items.removeAll { it.layerId == layer.id }
        if (activeLayerId == layer.id) activeLayerId = layers.first().id
        write { dao.deleteLayer(layer.id) } // marks are removed by the foreign-key cascade
    }

    private fun replaceLayer(updated: MarkupLayer) {
        val i = layers.indexOfFirst { it.id == updated.id }
        if (i >= 0) layers[i] = updated
    }

    private companion object {
        /** Time-seeded so ids stay unique across app launches. */
        val idSource = AtomicLong(System.currentTimeMillis() * 1000)
    }
}
