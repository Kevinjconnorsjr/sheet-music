package com.kevinconnors.scorestand.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.kevinconnors.scorestand.render.PageSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Owns the score files on disk and the database rows that describe them. */
class ScoreRepository(private val context: Context, db: AppDatabase) {
    val scoreDao = db.scoreDao()
    val setlistDao = db.setlistDao()

    private val scoresDir = File(context.filesDir, "scores").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun fileFor(score: Score) = File(scoresDir, score.fileName)
    fun thumbnailFor(score: Score) = File(thumbsDir, score.fileName.substringBeforeLast('.') + ".jpg")

    /**
     * Copies the document behind [uri] into private storage, reads its page count,
     * renders a thumbnail and adds it to the library.
     */
    suspend fun import(uri: Uri): Score = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Untitled"
        val mimeType = detectMimeType(uri, displayName)
            ?: throw IOException("\"$displayName\" isn't a PDF or image")

        val ext = if (mimeType == PDF) "pdf" else
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img"
        val target = File(scoresDir, "${UUID.randomUUID()}.$ext")
        try {
            (resolver.openInputStream(uri) ?: throw IOException("Can't read \"$displayName\"")).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            val pageCount = PageSources.open(target, mimeType).use { source ->
                if (source.pageCount == 0) throw IOException("\"$displayName\" has no pages")
                val thumb = source.render(0, THUMB_WIDTH)
                File(thumbsDir, target.nameWithoutExtension + ".jpg").outputStream().use {
                    thumb.compress(Bitmap.CompressFormat.JPEG, 85, it)
                }
                source.pageCount
            }
            val score = Score(
                title = prettifyTitle(displayName),
                fileName = target.name,
                mimeType = mimeType,
                pageCount = pageCount,
            )
            score.copy(id = scoreDao.insert(score))
        } catch (e: Exception) {
            target.delete()
            if (e is IOException) throw e
            throw IOException("Couldn't open \"$displayName\" (${e.message ?: e.javaClass.simpleName})", e)
        }
    }

    suspend fun update(score: Score) = scoreDao.update(score)

    suspend fun delete(score: Score) {
        scoreDao.delete(score)
        withContext(Dispatchers.IO) {
            fileFor(score).delete()
            thumbnailFor(score).delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun detectMimeType(uri: Uri, name: String): String? {
        val reported = context.contentResolver.getType(uri)
        if (reported == PDF || reported?.startsWith("image/") == true) return reported
        val byExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        if (byExt == PDF || byExt?.startsWith("image/") == true) return byExt
        // Fall back to sniffing the "%PDF" magic header.
        val header = ByteArray(4)
        val read = try {
            context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
        } catch (_: Exception) {
            0
        }
        return if (read == 4 && String(header, Charsets.US_ASCII) == "%PDF") PDF else null
    }

    private fun prettifyTitle(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "Untitled" }

    companion object {
        const val PDF = "application/pdf"
        private const val THUMB_WIDTH = 360
    }
}
