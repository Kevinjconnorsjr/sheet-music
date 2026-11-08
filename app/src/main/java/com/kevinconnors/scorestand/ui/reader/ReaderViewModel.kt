package com.kevinconnors.scorestand.ui.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinconnors.scorestand.ScoreStandApp
import com.kevinconnors.scorestand.data.Score
import com.kevinconnors.scorestand.data.SetlistItem
import com.kevinconnors.scorestand.data.ViewMode
import com.kevinconnors.scorestand.render.PageRenderer
import com.kevinconnors.scorestand.render.PageSources
import com.kevinconnors.scorestand.ui.reader.audio.AudioController
import com.kevinconnors.scorestand.ui.reader.markup.MarkupSession
import com.kevinconnors.scorestand.ui.reader.markup.ToolSettings
import com.kevinconnors.scorestand.practice.PianoSynth
import com.kevinconnors.scorestand.ui.tools.MetronomeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    app: Application,
    private val scoreId: Long,
    private val setlistId: Long,
    startIndex: Int,
) : AndroidViewModel(app) {
    private val repo = (app as ScoreStandApp).repository

    data class Loaded(val score: Score, val renderer: PageRenderer, val initialPage: Int, val markup: MarkupSession)

    /** Markup tool choices survive moving between pieces. */
    val tools = ToolSettings()

    private var metronomeSave: kotlinx.coroutines.Job? = null

    /** Metronome tempo and beats are remembered per piece. */
    val metronome = MetronomeController { bpm, beats ->
        val id = loaded?.score?.id ?: return@MetronomeController
        metronomeSave?.cancel()
        metronomeSave = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            repo.scoreDao.updateMetronome(id, bpm, beats)
        }
    }

    var exporting by mutableStateOf(false)
        private set

    /** Builds a PDF of the current score with its visible markings and hands back a share intent. */
    fun exportPdf(onReady: (android.content.Intent) -> Unit, onError: (String) -> Unit) {
        val score = loaded?.score ?: return
        val app = getApplication<ScoreStandApp>()
        viewModelScope.launch {
            exporting = true
            try {
                onReady(app.exporter.shareIntent(app.exporter.exportPdf(score)))
            } catch (e: Exception) {
                onError("Couldn't export: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                exporting = false
            }
        }
    }

    /** One player for the whole reader session; it switches tracks as pieces change. */
    val audio = AudioController(app, repo, viewModelScope)

    var loaded by mutableStateOf<Loaded?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var setlistItems by mutableStateOf<List<SetlistItem>>(emptyList())
        private set
    var setlistIndex by mutableIntStateOf(startIndex)
        private set
    var setlistName by mutableStateOf<String?>(null)
        private set

    val inSetlist get() = setlistId > 0 && setlistItems.isNotEmpty()

    init {
        viewModelScope.launch {
            if (setlistId > 0) {
                setlistItems = repo.setlistDao.getItems(setlistId)
                setlistName = repo.setlistDao.observe(setlistId).first()?.name
                val target = setlistItems.getOrNull(startIndex)?.score?.id ?: scoreId
                open(target, fromStart = true)
            } else {
                open(scoreId, fromStart = false)
            }
        }
    }

    private suspend fun open(id: Long, fromStart: Boolean) {
        val score = repo.scoreDao.get(id)
        if (score == null) {
            error = "This score is no longer in your library."
            return
        }
        try {
            val renderer = withContext(Dispatchers.IO) {
                PageRenderer(PageSources.open(repo.fileFor(score), score.mimeType))
            }
            val markup = MarkupSession(repo.markupDao, score.id, viewModelScope).apply { load() }
            val previous = loaded
            val start = if (fromStart) 0 else score.lastPage.coerceIn(0, renderer.pageCount - 1)
            loaded = Loaded(score, renderer, start, markup)
            audio.openScore(score.id)
            metronome.load(score.metronomeBpm.takeIf { it > 0 } ?: 100, score.beatsPerBar)
            error = null
            previous?.renderer?.close()
            repo.scoreDao.markOpened(id, System.currentTimeMillis())
        } catch (e: Exception) {
            error = "Couldn't open \"${score.title}\": ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun hasNextPiece() = inSetlist && setlistIndex < setlistItems.lastIndex
    fun hasPreviousPiece() = inSetlist && setlistIndex > 0

    fun nextPiece() {
        if (!hasNextPiece()) return
        setlistIndex++
        viewModelScope.launch { open(setlistItems[setlistIndex].score.id, fromStart = true) }
    }

    fun previousPiece() {
        if (!hasPreviousPiece()) return
        setlistIndex--
        viewModelScope.launch { open(setlistItems[setlistIndex].score.id, fromStart = true) }
    }

    fun saveProgress(page: Int) {
        val l = loaded ?: return
        viewModelScope.launch { repo.scoreDao.updateLastPage(l.score.id, page) }
    }

    fun setTrimMargins(trim: Boolean, currentPage: Int) {
        val l = loaded ?: return
        loaded = l.copy(score = l.score.copy(trimMargins = trim), initialPage = currentPage)
        viewModelScope.launch { repo.scoreDao.updateTrimMargins(l.score.id, trim) }
    }

    /** On-screen keyboard sound; runs only while the keyboard is open. */
    val piano = PianoSynth()

    fun setViewMode(mode: ViewMode, currentPage: Int) {
        val l = loaded ?: return
        loaded = l.copy(score = l.score.copy(viewMode = mode), initialPage = currentPage)
        viewModelScope.launch { repo.scoreDao.updateViewMode(l.score.id, mode) }
    }

    override fun onCleared() {
        audio.release()
        metronome.stop()
        piano.stop()
        loaded?.renderer?.close()
    }
}
