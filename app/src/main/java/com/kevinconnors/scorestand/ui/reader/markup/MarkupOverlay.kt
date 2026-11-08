package com.kevinconnors.scorestand.ui.reader.markup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kevinconnors.scorestand.data.Markup
import com.kevinconnors.scorestand.data.MarkupType
import com.kevinconnors.scorestand.data.PageLink

/**
 * Draws the visible marks for [page] on top of the page image, and — while [editing] —
 * turns touches into marks with the current tool. Sized exactly to the page.
 */
@Composable
fun MarkupOverlay(
    session: MarkupSession,
    settings: ToolSettings,
    page: Int,
    editing: Boolean,
    onRequestText: (page: Int, x: Float, y: Float) -> Unit,
    onLinkSpot: (page: Int, x: Float, y: Float) -> Unit,
    onLinkTapped: (PageLink) -> Unit,
) {
    val marks by remember(session, page) { derivedStateOf { session.visibleOn(page) } }
    val measurer = rememberTextMeasurer()
    val paths = remember(session) { HashMap<Long, Path>() }
    val live = remember { mutableStateListOf<Offset>() }
    var eraser by remember { mutableStateOf<Offset?>(null) }
    val tool = settings.tool

    val input = if (!editing) Modifier else when (tool) {
        Tool.PEN, Tool.HIGHLIGHTER -> Modifier.pointerInput(session, page, tool) {
            trackStroke(
                onPoint = { p -> if (live.isEmpty() || (live.last() - p).getDistance() > 0.0007f) live.add(p) },
                onEnd = { completed ->
                    if (completed && live.isNotEmpty()) {
                        val pts = FloatArray(live.size * 2).also { arr -> live.forEachIndexed { i, o -> arr[i * 2] = o.x; arr[i * 2 + 1] = o.y } }
                        session.add(
                            Markup(
                                id = session.newId(), scoreId = session.scoreId, layerId = session.activeLayerId, page = page,
                                type = if (tool == Tool.HIGHLIGHTER) MarkupType.HIGHLIGHT else MarkupType.INK,
                                color = settings.color(tool), size = settings.size(tool), points = pts,
                            )
                        )
                    }
                    live.clear()
                },
            )
        }
        Tool.ERASER -> Modifier.pointerInput(session, page) {
            val erased = mutableListOf<Markup>()
            trackStroke(
                onPoint = { p ->
                    eraser = p
                    val r = settings.size(Tool.ERASER)
                    val hits = session.visibleOn(page).filter { it.layerId == session.activeLayerId && it.isHitBy(p.x, p.y, r) }
                    session.removeLive(hits)
                    erased += hits
                },
                onEnd = {
                    session.commitRemoval(erased)
                    erased.clear()
                    eraser = null
                },
            )
        }
        Tool.LINK -> Modifier.pointerInput(session, page) {
            detectTapGestures { pos ->
                val unit = size.width.toFloat()
                onLinkSpot(page, pos.x / unit, pos.y / unit)
            }
        }
        Tool.TEXT, Tool.STAMP -> Modifier.pointerInput(session, page, tool) {
            detectTapGestures { pos ->
                val unit = size.width.toFloat()
                val x = pos.x / unit
                val y = pos.y / unit
                if (tool == Tool.TEXT) {
                    onRequestText(page, x, y)
                } else {
                    session.add(
                        Markup(
                            id = session.newId(), scoreId = session.scoreId, layerId = session.activeLayerId, page = page,
                            type = MarkupType.STAMP, color = settings.color(tool), size = settings.size(tool),
                            x = x, y = y, text = settings.stamp.name,
                        )
                    )
                }
            }
        }
        Tool.PAN -> Modifier
    }

    val linkColor = Color(0xFF1E3A5F)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val unitPx = constraints.maxWidth.toFloat()
    Canvas(Modifier.fillMaxSize().then(input)) {
        val unit = size.width
        if (unit <= 0f) return@Canvas
        for (m in marks) {
            drawMarkup(m, unit, measurer) { mark -> paths.getOrPut(mark.id) { buildInkPath(mark.points ?: FloatArray(0)) } }
        }
        if (live.isNotEmpty()) {
            val pts = FloatArray(live.size * 2).also { arr -> live.forEachIndexed { i, o -> arr[i * 2] = o.x; arr[i * 2 + 1] = o.y } }
            val highlight = tool == Tool.HIGHLIGHTER
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(
                    buildInkPath(pts),
                    if (highlight) highlighterColor(settings.color(tool)) else Color(settings.color(tool)),
                    style = Stroke(settings.size(tool), cap = if (highlight) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        eraser?.let {
            drawCircle(Color(0x88000000), settings.size(Tool.ERASER) * unit, it * unit, style = Stroke(1.5.dp.toPx()))
        }
        if (editing) {
            // Where links land: a small flag in the left margin.
            for (l in session.links) if (l.toPage == page) {
                val y = l.toY * unit
                val s = 8.dp.toPx()
                drawPath(
                    Path().apply { moveTo(0f, y - s); lineTo(s * 1.6f, y); lineTo(0f, y + s); close() },
                    linkColor.copy(alpha = 0.8f),
                )
            }
            session.pendingLinkFrom?.takeIf { it.page == page }?.let {
                drawCircle(linkColor, 14.dp.toPx(), Offset(it.x * unit, it.y * unit), style = Stroke(3.dp.toPx()))
            }
        }
    }
    // Link badges: tap to jump while reading; tap with the link tool to remove.
    val badgesEnabled = !editing || tool == Tool.LINK
    for (l in session.links) if (l.fromPage == page) {
        val density = LocalDensity.current
        Surface(
            onClick = { onLinkTapped(l) },
            enabled = badgesEnabled,
            shape = RoundedCornerShape(12.dp),
            color = linkColor.copy(alpha = 0.9f),
            contentColor = Color.White,
            modifier = Modifier.offset {
                with(density) { IntOffset((l.fromX * unitPx - 14.dp.toPx()).roundToInt(), (l.fromY * unitPx - 12.dp.toPx()).roundToInt()) }
            },
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Shortcut, null, Modifier.size(14.dp))
                Text(" ${l.label} → p${l.toPage + 1}", fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    }
}

/**
 * Follows one finger (or stylus) from down to up, reporting positions in page-width units.
 * A second finger cancels the stroke so pinches and accidental palm touches don't leave marks.
 */
@OptIn(ExperimentalComposeUiApi::class)
private suspend fun PointerInputScope.trackStroke(onPoint: (Offset) -> Unit, onEnd: (completed: Boolean) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val unit = size.width.toFloat().coerceAtLeast(1f)
        onPoint(down.position / unit)
        var completed = true
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) { completed = false; break }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.historical.forEach { onPoint(it.position / unit) }
            onPoint(change.position / unit)
            change.consume()
            if (!change.pressed) break
        }
        onEnd(completed)
    }
}
