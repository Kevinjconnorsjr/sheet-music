package com.kevinconnors.scorestand.ui.reader.markup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.kevinconnors.scorestand.data.Markup
import com.kevinconnors.scorestand.data.MarkupType
import kotlin.math.hypot

enum class GlyphStyle { DYNAMIC, WORD, PLAIN }

/**
 * Symbols musicians commonly pencil in. Text glyphs are drawn with system fonts; the rest are
 * drawn as vector shapes so they look the same on every device.
 * [halfWidth] is the horizontal extent relative to the symbol size (for hit-testing).
 */
enum class Stamp(val label: String, val glyph: String? = null, val style: GlyphStyle = GlyphStyle.DYNAMIC, val halfWidth: Float = 0.6f) {
    PP("pianissimo", "pp"), P("piano", "p"), MP("mezzo-piano", "mp"), MF("mezzo-forte", "mf"),
    F("forte", "f"), FF("fortissimo", "ff"), SFZ("sforzando", "sfz"),
    CRESC("crescendo", halfWidth = 1.2f), DIM("diminuendo", halfWidth = 1.2f),
    CRESC_WORD("cresc.", "cresc.", GlyphStyle.WORD, 1.6f), DIM_WORD("dim.", "dim.", GlyphStyle.WORD, 1.1f),
    RIT("rit.", "rit.", GlyphStyle.WORD, 1f), A_TEMPO("a tempo", "a tempo", GlyphStyle.WORD, 2f),
    FERMATA("fermata"), ACCENT("accent"), BREATH("breath mark"), CIRCLE("circle"),
    UP_BOW("up-bow"), DOWN_BOW("down-bow"), GLASSES("watch out!", halfWidth = 0.9f),
    SHARP("sharp", "♯", GlyphStyle.PLAIN), FLAT("flat", "♭", GlyphStyle.PLAIN), NATURAL("natural", "♮", GlyphStyle.PLAIN),
    FINGER_1("finger 1", "1", GlyphStyle.PLAIN), FINGER_2("finger 2", "2", GlyphStyle.PLAIN),
    FINGER_3("finger 3", "3", GlyphStyle.PLAIN), FINGER_4("finger 4", "4", GlyphStyle.PLAIN),
    FINGER_5("finger 5", "5", GlyphStyle.PLAIN);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: FERMATA
    }
}

private fun glyphStyle(style: GlyphStyle, color: Color, fontPx: Float, scope: DrawScope) = with(scope) {
    when (style) {
        GlyphStyle.DYNAMIC -> TextStyle(color = color, fontSize = fontPx.toSp(), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
        GlyphStyle.WORD -> TextStyle(color = color, fontSize = (fontPx * 0.75f).toSp(), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        GlyphStyle.PLAIN -> TextStyle(color = color, fontSize = fontPx.toSp(), fontWeight = FontWeight.SemiBold)
    }
}

/** Draws [stamp] centred on [c] with a nominal size of [s] pixels. */
fun DrawScope.drawStamp(stamp: Stamp, c: Offset, s: Float, color: Color, measurer: TextMeasurer) {
    stamp.glyph?.let { glyph ->
        val layout = measurer.measure(glyph, glyphStyle(stamp.style, color, s, this))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
        return
    }
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(pts: List<Offset>) {
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y) }
        drawPath(path, color, style = stroke)
    }
    when (stamp) {
        Stamp.CRESC -> line(listOf(Offset(c.x + s * 1.2f, c.y - s * 0.25f), Offset(c.x - s * 1.2f, c.y), Offset(c.x + s * 1.2f, c.y + s * 0.25f)))
        Stamp.DIM -> line(listOf(Offset(c.x - s * 1.2f, c.y - s * 0.25f), Offset(c.x + s * 1.2f, c.y), Offset(c.x - s * 1.2f, c.y + s * 0.25f)))
        Stamp.FERMATA -> {
            drawArc(color, 180f, 180f, false, Offset(c.x - s / 2, c.y - s * 0.35f), Size(s, s * 0.7f), style = stroke)
            drawCircle(color, s * 0.08f, Offset(c.x, c.y - s * 0.05f))
        }
        Stamp.ACCENT -> line(listOf(Offset(c.x - s / 2, c.y - s * 0.25f), Offset(c.x + s / 2, c.y), Offset(c.x - s / 2, c.y + s * 0.25f)))
        Stamp.BREATH -> line(listOf(Offset(c.x - s * 0.25f, c.y - s * 0.1f), Offset(c.x, c.y + s * 0.3f), Offset(c.x + s * 0.3f, c.y - s * 0.4f)))
        Stamp.CIRCLE -> drawCircle(color, s / 2, c, style = stroke)
        Stamp.UP_BOW -> line(listOf(Offset(c.x - s * 0.25f, c.y - s * 0.35f), Offset(c.x, c.y + s * 0.35f), Offset(c.x + s * 0.25f, c.y - s * 0.35f)))
        Stamp.DOWN_BOW -> {
            line(listOf(Offset(c.x - s * 0.3f, c.y + s * 0.3f), Offset(c.x - s * 0.3f, c.y - s * 0.25f), Offset(c.x + s * 0.3f, c.y - s * 0.25f), Offset(c.x + s * 0.3f, c.y + s * 0.3f)))
            drawRect(color, Offset(c.x - s * 0.3f, c.y - s * 0.32f), Size(s * 0.6f, s * 0.14f))
        }
        Stamp.GLASSES -> {
            drawCircle(color, s * 0.3f, Offset(c.x - s * 0.42f, c.y + s * 0.05f), style = stroke)
            drawCircle(color, s * 0.3f, Offset(c.x + s * 0.42f, c.y + s * 0.05f), style = stroke)
            drawArc(color, 200f, 140f, false, Offset(c.x - s * 0.14f, c.y - s * 0.12f), Size(s * 0.28f, s * 0.2f), style = stroke)
        }
        else -> Unit
    }
}

/** Smooths the points with quadratic curves through segment midpoints. Units are page widths. */
fun buildInkPath(points: FloatArray): Path {
    val path = Path()
    val n = points.size / 2
    if (n == 0) return path
    path.moveTo(points[0], points[1])
    if (n == 1) {
        path.lineTo(points[0] + 0.0001f, points[1])
        return path
    }
    for (i in 1 until n - 1) {
        val x = points[i * 2]; val y = points[i * 2 + 1]
        val nx = points[i * 2 + 2]; val ny = points[i * 2 + 3]
        path.quadraticTo(x, y, (x + nx) / 2, (y + ny) / 2)
    }
    path.lineTo(points[(n - 1) * 2], points[(n - 1) * 2 + 1])
    return path
}

fun highlighterColor(argb: Int) = Color(argb).copy(alpha = 0.38f)

/** Draws one mark. [unit] is the page width in pixels. */
fun DrawScope.drawMarkup(m: Markup, unit: Float, measurer: TextMeasurer, pathFor: (Markup) -> Path) {
    when (m.type) {
        MarkupType.INK, MarkupType.HIGHLIGHT -> {
            val highlight = m.type == MarkupType.HIGHLIGHT
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(
                    pathFor(m),
                    if (highlight) highlighterColor(m.color) else Color(m.color),
                    style = Stroke(width = m.size, cap = if (highlight) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        MarkupType.TEXT -> {
            val layout = measurer.measure(m.text.orEmpty(), TextStyle(color = Color(m.color), fontSize = (m.size * unit).toSp()))
            drawText(layout, topLeft = Offset(m.x * unit, m.y * unit))
        }
        MarkupType.STAMP -> drawStamp(Stamp.from(m.text), Offset(m.x * unit, m.y * unit), m.size * unit, Color(m.color), measurer)
    }
}

/** True when a circle at ([px], [py]) with radius [r] (page-width units) touches the mark. */
fun Markup.isHitBy(px: Float, py: Float, r: Float): Boolean = when (type) {
    MarkupType.INK, MarkupType.HIGHLIGHT -> {
        val pts = points ?: FloatArray(0)
        val reach = r + size / 2
        val n = pts.size / 2
        if (n == 1) hypot(pts[0] - px, pts[1] - py) <= reach
        else (0 until n - 1).any { i ->
            segmentDistance(px, py, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]) <= reach
        }
    }
    MarkupType.TEXT -> {
        val lines = text.orEmpty().lines()
        val w = (lines.maxOfOrNull { it.length } ?: 1) * size * 0.6f
        val h = lines.size * size * 1.3f
        px >= x - r && px <= x + w + r && py >= y - r && py <= y + h + r
    }
    MarkupType.STAMP -> {
        val stamp = Stamp.from(text)
        kotlin.math.abs(px - x) <= size * stamp.halfWidth + r && kotlin.math.abs(py - y) <= size * 0.5f + r
    }
}

private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
