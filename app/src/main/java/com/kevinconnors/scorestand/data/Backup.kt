package com.kevinconnors.scorestand.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Saves the whole library (database, scores, audio, thumbnails) to one .zip, and restores it.
 * Restoring replaces everything and restarts the app.
 */
class BackupManager(private val context: Context, private val database: AppDatabase) {
    private val dataDirs = listOf("scores", "audio", "thumbs")
    private val dbName = "scorestand.db"

    suspend fun backup(target: Uri) = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file so one file holds everything.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(target) ?: throw IOException("Can't write the backup file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED) // PDFs and audio are already compressed
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                JSONObject()
                    .put("app", "ScoreStand")
                    .put("format", 1)
                    .put("databaseVersion", database.openHelper.readableDatabase.version)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(2).toByteArray()
            )
            zip.closeEntry()
            addFile(zip, context.getDatabasePath(dbName), "database/$dbName")
            for (dir in dataDirs) {
                File(context.filesDir, dir).listFiles()?.filter { it.isFile }?.forEach { addFile(zip, it, "files/$dir/${it.name}") }
            }
        }
    }

    /** Checks and unpacks the backup, swaps it in, then restarts the app. Doesn't return on success. */
    suspend fun restore(source: Uri) {
        val staging = File(context.cacheDir, "restore")
        withContext(Dispatchers.IO) {
            staging.deleteRecursively(); staging.mkdirs()
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Can't read the backup file")
            ZipInputStream(input.buffered()).use { zip ->
                val root = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val dest = File(staging, entry.name)
                    if (!dest.canonicalPath.startsWith(root)) throw IOException("This isn't a ScoreStand backup")
                    if (entry.isDirectory) { dest.mkdirs(); continue }
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { zip.copyTo(it) }
                }
            }
            val manifest = File(staging, MANIFEST)
            val db = File(staging, "database/$dbName")
            val ok = manifest.isFile && db.isFile && runCatching { JSONObject(manifest.readText()).getString("app") == "ScoreStand" }.getOrDefault(false)
            if (!ok) {
                staging.deleteRecursively()
                throw IOException("This isn't a ScoreStand backup")
            }
            val backupVersion = JSONObject(manifest.readText()).optInt("databaseVersion", 0)
            if (backupVersion > database.openHelper.readableDatabase.version) {
                staging.deleteRecursively()
                throw IOException("This backup was made by a newer version of ScoreStand. Update the app first.")
            }

            database.close()
            for (dir in dataDirs) {
                val live = File(context.filesDir, dir)
                live.deleteRecursively()
                File(staging, "files/$dir").takeIf { it.isDirectory }?.copyRecursively(live, overwrite = true) ?: live.mkdirs()
            }
            val liveDb = context.getDatabasePath(dbName)
            File(liveDb.path + "-wal").delete()
            File(liveDb.path + "-shm").delete()
            db.copyTo(liveDb, overwrite = true)
            staging.deleteRecursively()
        }
        restart()
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }

    companion object {
        private const val MANIFEST = "manifest.json"
    }
}
