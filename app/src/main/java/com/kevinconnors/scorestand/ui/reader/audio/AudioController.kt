package com.kevinconnors.scorestand.ui.reader.audio

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kevinconnors.scorestand.audio.PracticePlayer
import com.kevinconnors.scorestand.data.AudioMarker
import com.kevinconnors.scorestand.data.AudioTrack
import com.kevinconnors.scorestand.data.ScoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Practice audio for the score on screen: which tracks it has, the one playing, its speed /
 * pitch / pan / volume / A–B loop (saved per track), markers, and page turns that follow playback.
 */
class AudioController(app: Application, private val repo: ScoreRepository, private val scope: CoroutineScope) {
    private val player = PracticePlayer(app)
    /** Writes outlive the screen so the last settings change is never lost. */
    private val persist = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var scoreId = 0L
        private set
    var tracks by mutableStateOf<List<AudioTrack>>(emptyList())
        private set
    /** The selected track, including unsaved setting changes. */
    var current by mutableStateOf<AudioTrack?>(null)
        private set
    var markers by mutableStateOf<List<AudioMarker>>(emptyList())
        private set
    var waveform by mutableStateOf<FloatArray?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var followPages by mutableStateOf(true)

    /**
     * While on, every page turn made during playback is saved as a marker at that moment, so
     * later playbacks turn the pages by themselves. Existing markers don't turn pages meanwhile.
     */
    var recordTurns by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set

    val isPlaying get() = player.isPlaying
    val durationMs get() = player.durationMs.takeIf { it > 0 } ?: current?.durationMs ?: 0L

    /** Pages the reader should turn to (from markers reached during playback, or seeks). */
    val pageRequests = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private var tracksJob: Job? = null
    private var markersJob: Job? = null
    private var waveformJob: Job? = null
    private var saveJob: Job? = null
    private var dirty: AudioTrack? = null

    init {
        scope.launch { pollPlayback() }
    }

    fun openScore(id: Long) {
        if (id == scoreId) return
        flushSave()
        scoreId = id
        select(null)
        tracksJob?.cancel()
        tracksJob = scope.launch {
            repo.audioDao.observeTracks(id).collect { list ->
                tracks = list
                val cur = current
                if (cur == null || list.none { it.id == cur.id }) select(list.firstOrNull())
            }
        }
    }

    fun select(track: AudioTrack?) {
        if (track != null && track.id == current?.id) return
        flushSave()
        player.pause()
        current = track
        markers = emptyList()
        waveform = null
        positionMs = 0
        markersJob?.cancel()
        waveformJob?.cancel()
        if (track == null) {
            player.unload()
            return
        }
        player.load(repo.fileFor(track))
        applySettings(track)
        val loopStart = track.loopStartMs
        if (track.loopEnabled && loopStart > 0) player.seekTo(loopStart)
        markersJob = scope.launch { repo.audioDao.observeMarkers(track.id).collect { markers = it } }
        waveformJob = scope.launch { waveform = repo.waveformFor(track) }
    }

    // ---- transport ----

    fun togglePlay() = if (player.isPlaying) player.pause() else play()

    fun play() {
        val t = current ?: return
        val pos = player.positionMs
        if (hasLoop(t) && t.loopEnabled && (pos < t.loopStartMs || pos >= t.loopEndMs)) player.seekTo(t.loopStartMs)
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        player.seekTo(target)
        positionMs = target
        lastPosition = target
        if (followPages && !recordTurns) markers.lastOrNull { it.page >= 0 && it.positionMs <= target }?.let { pageRequests.tryEmit(it.page) }
    }

    fun skip(deltaMs: Long) = seekTo(player.positionMs + deltaMs)

    // ---- practice settings ----

    fun setSpeed(speed: Float) = update { it.copy(speed = (speed * 100).toInt() / 100f) }
    fun setPitchCents(cents: Int) = update { it.copy(pitchCents = cents.coerceIn(-1200, 1200)) }
    fun setPan(pan: Float) = update { it.copy(pan = pan.coerceIn(-1f, 1f)) }
    fun setVolume(volume: Float) = update { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setLoopStart() {
        val pos = player.positionMs
        update { it.copy(loopStartMs = pos, loopEndMs = if (it.loopEndMs > pos) it.loopEndMs else -1, loopEnabled = it.loopEnabled && it.loopEndMs > pos) }
    }

    fun setLoopEnd() {
        val pos = player.positionMs
        val t = current ?: return
        val start = t.loopStartMs.coerceAtLeast(0)
        if (pos <= start + 200) {
            messages.tryEmit("Set B after A")
            return
        }
        update { it.copy(loopStartMs = start, loopEndMs = pos, loopEnabled = true) }
    }

    fun toggleLoop() {
        val t = current ?: return
        if (hasLoop(t)) update { it.copy(loopEnabled = !it.loopEnabled) }
    }

    fun clearLoop() = update { it.copy(loopStartMs = -1, loopEndMs = -1, loopEnabled = false) }

    fun hasLoop(t: AudioTrack) = t.loopStartMs >= 0 && t.loopEndMs > t.loopStartMs

    private fun update(transform: (AudioTrack) -> AudioTrack) {
        val t = current ?: return
        val updated = transform(t)
        current = updated
        applySettings(updated)
        dirty = updated
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            flushSave()
        }
    }

    private fun flushSave() {
        val pending = dirty ?: return
        dirty = null
        persist.launch { repo.audioDao.updateTrack(pending) }
    }

    private fun applySettings(t: AudioTrack) {
        player.setSpeedAndPitch(t.speed, t.pitchCents)
        player.setPan(t.pan)
        player.setVolume(t.volume)
    }

    // ---- markers ----

    fun addMarker(page: Int) {
        val t = current ?: return
        val pos = player.positionMs
        val label = "Marker ${markers.size + 1}"
        persist.launch { repo.audioDao.insertMarker(AudioMarker(trackId = t.id, positionMs = pos, label = label, page = page)) }
    }

    /** Saves "turn to [page]" at the current playback moment. */
    fun addPageTurn(page: Int) {
        val t = current ?: return
        val pos = player.positionMs
        persist.launch { repo.audioDao.insertMarker(AudioMarker(trackId = t.id, positionMs = pos, label = "Page ${page + 1}", page = page)) }
    }

    fun renameMarker(marker: AudioMarker, label: String) = persist.launch { repo.audioDao.updateMarker(marker.copy(label = label.trim())) }
    fun linkMarker(marker: AudioMarker, page: Int) = persist.launch { repo.audioDao.updateMarker(marker.copy(page = page)) }
    fun deleteMarker(marker: AudioMarker) = persist.launch { repo.audioDao.deleteMarker(marker.id) }

    // ---- editing recorded page turns ----

    val pageTurns: List<AudioMarker> get() = markers.filter { it.page >= 0 }.sortedBy { it.positionMs }

    fun nudgeTurn(marker: AudioMarker, deltaMs: Long) = saveMarker(PageTurns.moved(marker, deltaMs, durationMs))
    fun setTurnTime(marker: AudioMarker, positionMs: Long) = saveMarker(PageTurns.at(marker, positionMs, durationMs))
    fun setTurnPage(marker: AudioMarker, page: Int, pageCount: Int) = saveMarker(PageTurns.toPage(marker, page, pageCount))

    fun shiftAllTurns(deltaMs: Long) {
        val shifted = PageTurns.shiftedAll(markers, deltaMs, durationMs)
        applyLocally(shifted)
        persist.launch { shifted.forEach { repo.audioDao.updateMarker(it) } }
    }

    fun clearAllTurns() {
        val turns = pageTurns
        markers = markers.filter { it.page < 0 }
        persist.launch { turns.forEach { repo.audioDao.deleteMarker(it.id) } }
    }

    /** Plays from a few seconds before a turn, to check its timing. */
    fun previewTurn(marker: AudioMarker) {
        seekTo((marker.positionMs - 3_000).coerceAtLeast(0))
        play()
    }

    private fun saveMarker(updated: AudioMarker) {
        applyLocally(listOf(updated)) // show the change at once; the database follows
        persist.launch { repo.audioDao.updateMarker(updated) }
    }

    private fun applyLocally(updated: List<AudioMarker>) {
        val byId = updated.associateBy { it.id }
        markers = markers.map { byId[it.id] ?: it }.sortedBy { it.positionMs }
    }

    // ---- tracks ----

    fun importTrack(uri: Uri) {
        val id = scoreId.takeIf { it > 0 } ?: return
        scope.launch {
            busy = true
            try {
                select(repo.importAudio(id, uri))
            } catch (e: Exception) {
                messages.tryEmit(e.message ?: "Couldn't add that file")
            } finally {
                busy = false
            }
        }
    }

    fun renameTrack(track: AudioTrack, title: String) {
        val renamed = (if (track.id == current?.id) current!! else track).copy(title = title.trim())
        if (track.id == current?.id) current = renamed
        persist.launch { repo.audioDao.updateTrack(renamed) }
    }

    fun deleteTrack(track: AudioTrack) {
        if (track.id == current?.id) {
            dirty = null
            select(null)
        }
        scope.launch { repo.deleteTrack(track) } // the track list flow then picks the next track
    }

    // ---- playback polling: position, A–B loop, page following ----

    private var lastPosition = 0L

    private suspend fun pollPlayback() {
        while (scope.isActive) {
            val playing = player.isPlaying
            val pos = player.positionMs
            val t = current
            if (t != null && playing) {
                if (t.loopEnabled && hasLoop(t) && pos >= t.loopEndMs) {
                    player.seekTo(t.loopStartMs)
                    positionMs = t.loopStartMs
                    lastPosition = t.loopStartMs
                    delay(25)
                    continue
                }
                if (followPages && !recordTurns && pos > lastPosition) {
                    markers.lastOrNull { it.page >= 0 && it.positionMs > lastPosition && it.positionMs <= pos }
                        ?.let { pageRequests.tryEmit(it.page) }
                }
            }
            lastPosition = pos
            positionMs = pos
            delay(if (playing) 25 else 200)
        }
    }

    fun release() {
        flushSave()
        player.release()
    }
}
