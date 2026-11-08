package com.kevinconnors.scorestand.data

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.kevinconnors.scorestand.notation.MidiWriter
import com.kevinconnors.scorestand.notation.MusicXml
import com.kevinconnors.scorestand.notation.PlaybackOptions
import com.kevinconnors.scorestand.render.PageSources
import com.kevinconnors.scorestand.ui.reader.markup.buildInkPath
import com.kevinconnors.scorestand.ui.reader.markup.drawMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Writes a score to a new PDF with its visible markings drawn in, for sharing or printing. */
class ScoreExporter(private val context: Context, private val repo: ScoreRepository) {

    suspend fun exportPdf(score: Score, includeMarkings: Boolean = true): File = withContext(Dispatchers.IO) {
        val visibleLayers = repo.markupDao.layers(score.id).filter { it.visible }.map { it.id }.toSet()
        val marks = if (includeMarkings) repo.markupDao.markups(score.id).filter { it.layerId in visibleLayers } else emptyList()
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, safeName(score.title) + ".pdf")
        val doc = PdfDocument()
        try {
            PageSources.open(repo.fileFor(score), score.mimeType).use { source ->
                for (i in 0 until source.pageCount) {
                    val w = source.widthPoints(i)
                    val h = (w / source.aspectRatio(i)).roundToInt()
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                    // ~200 dpi keeps print quality without huge files.
                    val bitmap = source.render(i, (w * 200 / 72).coerceAtMost(2400))
                    page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                    val onPage = marks.filter { it.page == i }
                    if (onPage.isNotEmpty()) {
                        // Reuse the on-screen drawing code; 1 unit = 1 point here.
                        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(page.canvas), Size(w.toFloat(), h.toFloat())) {
                            onPage.forEach { m -> drawMarkup(m, w.toFloat(), measurer) { buildInkPath(it.points ?: FloatArray(0)) } }
                        }
                    }
                    doc.finishPage(page)
                }
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        out
    }

    /** Writes a MusicXML score's playback (with its saved transposition) as a .mid file. */
    suspend fun exportMidi(score: Score): File = withContext(Dispatchers.IO) {
        val parsed = MusicXml.parse(repo.readNotation(score))
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        File(dir, safeName(score.title) + ".mid").apply {
            writeBytes(MidiWriter.write(parsed, PlaybackOptions(transpose = score.transposeSemitones)))
        }
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "mid") "audio/midi" else "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${file.nameWithoutExtension}")
    }

    private fun safeName(title: String) = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(80).ifEmpty { "Score" }
}
