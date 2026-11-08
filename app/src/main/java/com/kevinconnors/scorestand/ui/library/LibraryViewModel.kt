package com.kevinconnors.scorestand.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinconnors.scorestand.ScoreStandApp
import com.kevinconnors.scorestand.data.Score
import com.kevinconnors.scorestand.data.Setlist
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    TITLE("Title"),
    COMPOSER("Composer"),
    RECENTLY_OPENED("Recently opened"),
    RECENTLY_ADDED("Recently added"),
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ScoreStandApp).repository

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.TITLE)
    val tagFilter = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)

    private val app = app as ScoreStandApp
    private val shareChannel = Channel<android.content.Intent>(Channel.BUFFERED)
    /** Share-sheet intents for exported PDFs. */
    val shares = shareChannel.receiveAsFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val allScores = repo.scoreDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scores = combine(allScores, query, sort, tagFilter) { list, q, order, tag ->
        val needle = q.trim()
        list.asSequence()
            .filter { tag == null || tag in it.tagList() }
            .filter {
                needle.isEmpty() || it.title.contains(needle, true) || it.composer.contains(needle, true) ||
                    it.tags.contains(needle, true)
            }
            .let { seq ->
                when (order) {
                    SortOrder.TITLE -> seq.sortedBy { it.title.lowercase() }
                    SortOrder.COMPOSER -> seq.sortedWith(compareBy({ it.composer.ifEmpty { "￿" }.lowercase() }, { it.title.lowercase() }))
                    SortOrder.RECENTLY_OPENED -> seq.sortedByDescending { it.lastOpenedAt }
                    SortOrder.RECENTLY_ADDED -> seq.sortedByDescending { it.createdAt }
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags = allScores.map { list -> list.flatMap { it.tagList() }.distinct().sortedBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val setlists = repo.setlistDao.observeSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun thumbnailFor(score: Score) = repo.thumbnailFor(score)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var ok = 0
            val errors = mutableListOf<String>()
            for (uri in uris) {
                try {
                    repo.import(uri); ok++
                } catch (e: Exception) {
                    errors += e.message ?: "Import failed"
                }
            }
            importing.value = false
            when {
                errors.isEmpty() -> messageChannel.send("Added $ok score${if (ok == 1) "" else "s"}")
                ok == 0 -> messageChannel.send(errors.first())
                else -> messageChannel.send("Added $ok, ${errors.size} failed: ${errors.first()}")
            }
        }
    }

    fun update(score: Score) = viewModelScope.launch { repo.update(score) }

    fun delete(score: Score) = viewModelScope.launch {
        repo.delete(score)
        messageChannel.send("Deleted \"${score.title}\"")
    }

    fun createSetlist(name: String, withScore: Score? = null) = viewModelScope.launch {
        val id = repo.setlistDao.insert(Setlist(name = name.trim()))
        if (withScore != null) {
            repo.setlistDao.addScores(id, listOf(withScore.id))
            messageChannel.send("Added to \"${name.trim()}\"")
        }
    }

    fun addToSetlist(setlist: Setlist, score: Score) = viewModelScope.launch {
        repo.setlistDao.addScores(setlist.id, listOf(score.id))
        messageChannel.send("Added to \"${setlist.name}\"")
    }

    fun exportPdf(score: Score) = viewModelScope.launch {
        importing.value = true
        try {
            shareChannel.send(app.exporter.shareIntent(app.exporter.exportPdf(score)))
        } catch (e: Exception) {
            messageChannel.send("Couldn't export: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            importing.value = false
        }
    }

    fun backup(target: Uri) = viewModelScope.launch {
        importing.value = true
        try {
            app.backups.backup(target)
            messageChannel.send("Backup saved")
        } catch (e: Exception) {
            messageChannel.send("Backup failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            importing.value = false
        }
    }

    /** Replaces the library with the backup and restarts the app. */
    fun restore(source: Uri) = viewModelScope.launch {
        importing.value = true
        try {
            app.backups.restore(source)
        } catch (e: Exception) {
            importing.value = false
            messageChannel.send("Restore failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun deleteSetlist(setlist: Setlist) = viewModelScope.launch { repo.setlistDao.delete(setlist.id) }
}
