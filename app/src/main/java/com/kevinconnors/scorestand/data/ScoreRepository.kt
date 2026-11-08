package com.kevinconnors.scorestand.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.kevinconnors.scorestand.audio.Waveform
import com.kevinconnors.scorestand.notation.MusicXml
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
    val markupDao = db.markupDao()
    val audioDao = db.audioDao()

    private val scoresDir = File(context.filesDir, "scores").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val audioDir = File(context.filesDir, "audio").apply { mkdirs() }

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
            ?: throw IOException("\"$displayName\" isn't a PDF, image or MusicXML file")
        if (mimeType == MusicXml.MIME) return@withContext importMusicXml(uri, displayName)

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

    /** Stores MusicXML (unpacking .mxl) as plain text and reads its title, composer and bars. */
    private suspend fun importMusicXml(uri: Uri, displayName: String): Score {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Can't read \"$displayName\"")
        val parsed = try {
            val text = MusicXml.readText(bytes)
            text to MusicXml.parse(text)
        } catch (e: Exception) {
            throw IOException("\"$displayName\" isn't a readable MusicXML score (${e.message ?: e.javaClass.simpleName})", e)
        }
        val target = File(scoresDir, "${UUID.randomUUID()}.musicxml")
        target.writeText(parsed.first)
        val info = parsed.second
        val score = Score(
            title = info.title ?: prettifyTitle(displayName),
            composer = info.composer.orEmpty(),
            fileName = target.name,
            mimeType = MusicXml.MIME,
            pageCount = info.measureStarts.size,
        )
        return score.copy(id = scoreDao.insert(score))
    }

    fun readNotation(score: Score): String = fileFor(score).readText()

    suspend fun update(score: Score) = scoreDao.update(score)

    suspend fun delete(score: Score) {
        val tracks = audioDao.tracksFor(score.id)
        scoreDao.delete(score)
        withContext(Dispatchers.IO) {
            fileFor(score).delete()
            thumbnailFor(score).delete()
            tracks.forEach { deleteAudioFiles(it) }
        }
    }

    // ---- audio ----

    fun fileFor(track: AudioTrack) = File(audioDir, track.fileName)
    private fun peaksFileFor(track: AudioTrack) = File(audioDir, track.fileName.substringBeforeLast('.') + ".peaks")

    /** Copies an audio file into private storage and attaches it to [scoreId]. */
    suspend fun importAudio(scoreId: Long, uri: Uri): AudioTrack = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Track"
        val ext = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "audio"
        val target = File(audioDir, "${UUID.randomUUID()}.$ext")
        try {
            (resolver.openInputStream(uri) ?: throw IOException("Can't read \"$displayName\"")).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            val retriever = MediaMetadataRetriever()
            val (duration, metaTitle) = try {
                retriever.setDataSource(target.path)
                val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                d to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            } catch (_: Exception) {
                null to null
            } finally {
                retriever.release()
            }
            if (duration == null || duration <= 0) throw IOException("\"$displayName\" isn't a playable audio file")
            val track = AudioTrack(
                scoreId = scoreId,
                title = metaTitle?.takeIf { it.isNotBlank() } ?: prettifyTitle(displayName),
                fileName = target.name,
                durationMs = duration,
                position = audioDao.maxPosition(scoreId) + 1,
            )
            track.copy(id = audioDao.insertTrack(track))
        } catch (e: Exception) {
            target.delete()
            if (e is IOException) throw e
            throw IOException("Couldn't add \"$displayName\" (${e.message ?: e.javaClass.simpleName})", e)
        }
    }

    suspend fun deleteTrack(track: AudioTrack) {
        audioDao.deleteTrack(track.id)
        withContext(Dispatchers.IO) { deleteAudioFiles(track) }
    }

    private fun deleteAudioFiles(track: AudioTrack) {
        fileFor(track).delete()
        peaksFileFor(track).delete()
    }

    /** Waveform outline for [track], decoded on first use and cached on disk. */
    suspend fun waveformFor(track: AudioTrack): FloatArray? = withContext(Dispatchers.IO) {
        val cache = peaksFileFor(track)
        Waveform.read(cache) ?: Waveform.compute(fileFor(track))?.also { Waveform.write(cache, it) }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun detectMimeType(uri: Uri, name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in setOf("musicxml", "mxl")) return MusicXml.MIME
        val reported = context.contentResolver.getType(uri)
        if (reported != null && "musicxml" in reported) return MusicXml.MIME
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
        if (read == 4 && String(header, Charsets.US_ASCII) == "%PDF") return PDF
        // Plain .xml files: check whether it's a MusicXML score.
        val start = try {
            context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(4096).let { b -> String(b, 0, s.read(b).coerceAtLeast(0)) } }.orEmpty()
        } catch (_: Exception) {
            ""
        }
        return if (MusicXml.looksLikeMusicXml(start)) MusicXml.MIME else null
    }

    private fun prettifyTitle(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "Untitled" }

    companion object {
        const val PDF = "application/pdf"
        private const val THUMB_WIDTH = 360
    }
}
