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

/** Wraps a [PageSource] with an in-memory bitmap cache and off-main-thread rendering. */
class PageRenderer(private val source: PageSource) : Closeable {
    val pageCount get() = source.pageCount
    fun aspectRatio(index: Int) = source.aspectRatio(index)

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 5).toInt().coerceAtMost(256 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Rounds widths up so small layout changes reuse the same bitmap. */
    private fun bucket(width: Int) = (((width + 127) / 128) * 128).coerceIn(128, 2560)

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
