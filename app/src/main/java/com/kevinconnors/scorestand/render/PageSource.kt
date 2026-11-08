package com.kevinconnors.scorestand.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A document that can be drawn one page at a time. */
interface PageSource : Closeable {
    val pageCount: Int

    /** Width divided by height of the page. */
    fun aspectRatio(index: Int): Float

    /** Page width in PDF points (1/72 inch), used when exporting. */
    fun widthPoints(index: Int): Int = 612

    /** Renders [index] to a bitmap roughly [targetWidth] pixels wide. Blocking. */
    fun render(index: Int, targetWidth: Int): Bitmap
}

object PageSources {
    fun open(file: File, mimeType: String): PageSource =
        if (mimeType == "application/pdf") PdfPageSource(file) else ImagePageSource(file)
}

private const val MAX_PIXELS = 12_000_000

private fun clampSize(width: Int, height: Int): Pair<Int, Int> {
    val pixels = width.toLong() * height
    if (pixels <= MAX_PIXELS) return width to height
    val scale = sqrt(MAX_PIXELS.toDouble() / pixels)
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

class PdfPageSource(file: File) : PageSource {
    private val lock = Any()
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    private var closed = false

    override val pageCount: Int = renderer.pageCount

    private val widths = IntArray(pageCount)
    private val ratios: FloatArray = synchronized(lock) {
        FloatArray(pageCount) { i ->
            val page = renderer.openPage(i)
            try {
                widths[i] = page.width
                page.width.toFloat() / page.height.coerceAtLeast(1)
            } finally {
                page.close()
            }
        }
    }

    override fun aspectRatio(index: Int): Float = ratios.getOrElse(index) { 0.7071f }
    override fun widthPoints(index: Int): Int = widths.getOrElse(index) { 612 }.takeIf { it > 0 } ?: 612

    override fun render(index: Int, targetWidth: Int): Bitmap = synchronized(lock) {
        val ratio = aspectRatio(index)
        val (w, h) = clampSize(targetWidth.coerceAtLeast(1), (targetWidth / ratio).roundToInt().coerceAtLeast(1))
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        if (!closed) {
            val page = renderer.openPage(index)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } finally {
                page.close()
            }
        }
        bitmap
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
            fd.close()
        }
    }
}

class ImagePageSource(private val file: File) : PageSource {
    private val width: Int
    private val height: Int

    init {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "Not a readable image" }
        width = opts.outWidth
        height = opts.outHeight
    }

    override val pageCount = 1
    override fun aspectRatio(index: Int) = width.toFloat() / height

    override fun render(index: Int, targetWidth: Int): Bitmap {
        var sample = 1
        while (width / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    }

    override fun close() {}
}

/** A region of a page in fractions of its width/height (0..1). */
data class ContentBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val FULL = ContentBox(0f, 0f, 1f, 1f)

        /**
         * Finds the box around everything that isn't (near-)white, plus a small border.
         * Ignores specks: a row/column needs a few dark pixels to count. Never crops away more
         * than needed, and returns [FULL] for blank pages.
         */
        fun find(pixels: IntArray, width: Int, height: Int, padding: Float = 0.015f): ContentBox {
            if (width <= 0 || height <= 0) return FULL
            fun dark(p: Int): Boolean {
                val a = (p ushr 24) and 0xFF
                if (a < 128) return false
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                return (r * 299 + g * 587 + b * 114) / 1000 < 200
            }
            val colCounts = IntArray(width)
            val rowCounts = IntArray(height)
            for (y in 0 until height) for (x in 0 until width) if (dark(pixels[y * width + x])) { colCounts[x]++; rowCounts[y]++ }
            val minHits = 2
            val l = colCounts.indexOfFirst { it >= minHits }
            if (l < 0) return FULL
            val r = colCounts.indexOfLast { it >= minHits }
            val t = rowCounts.indexOfFirst { it >= minHits }
            val b = rowCounts.indexOfLast { it >= minHits }
            val box = ContentBox(
                (l.toFloat() / width - padding).coerceAtLeast(0f),
                (t.toFloat() / height - padding).coerceAtLeast(0f),
                ((r + 1).toFloat() / width + padding).coerceAtMost(1f),
                ((b + 1).toFloat() / height + padding).coerceAtMost(1f),
            )
            // Something tiny (a page number alone) isn't worth zooming into.
            return if (box.width < 0.2f || box.height < 0.1f) FULL else box
        }
    }
}

/** Wraps a [PageSource] with an in-memory bitmap cache and off-main-thread rendering. */
class PageRenderer(private val source: PageSource) : Closeable {
    val pageCount get() = source.pageCount
    fun aspectRatio(index: Int) = source.aspectRatio(index)

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 5).toInt().coerceAtMost(256 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Rounds widths up so small layout changes reuse the same bitmap. Zoomed pages go up to 4096 px. */
    private fun bucket(width: Int) = (((width + 127) / 128) * 128).coerceIn(128, 4096)

    private val contentBounds = java.util.concurrent.ConcurrentHashMap<Int, ContentBox>()

    fun cachedContentBounds(index: Int): ContentBox? = contentBounds[index]

    /** Where the printed content sits on [index], found once from a small render and cached. */
    suspend fun contentBounds(index: Int): ContentBox {
        contentBounds[index]?.let { return it }
        return withContext(Dispatchers.Default) {
            val small = withContext(Dispatchers.IO) { source.render(index, 400) }
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            ContentBox.find(pixels, small.width, small.height)
        }.also { contentBounds[index] = it }
    }

    fun cached(index: Int, width: Int): Bitmap? = cache.get("$index@${bucket(width)}")

    suspend fun render(index: Int, width: Int): Bitmap {
        val w = bucket(width)
        val key = "$index@$w"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) { source.render(index, w) }.also { cache.put(key, it) }
    }

    override fun close() {
        source.close()
        cache.evictAll()
    }
}
