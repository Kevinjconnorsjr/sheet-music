package com.kevinconnors.scorestand.playlist

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kevinconnors.scorestand.audio.PanAudioProcessor
import com.kevinconnors.scorestand.audio.buildPracticeExoPlayer
import com.kevinconnors.scorestand.data.AudioTrack
import com.kevinconnors.scorestand.data.PlaylistTrack
import com.kevinconnors.scorestand.data.ScoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Plays listening playlists for the whole app (it keeps going in the background through
 * [PlaybackService]). Loops the whole playlist by default. Each track brings its own practice
 * settings — speed, pitch, pan, volume and A–B loop — shared with the score's audio panel, and
 * changes made here are saved back to the track. Main thread only.
 */
@UnstableApi
class PlaylistPlayer(app: Application, private val repo: ScoreRepository) {
    private val pan = PanAudioProcessor()
    val player: ExoPlayer = buildPracticeExoPlayer(app, pan).apply { repeatMode = Player.REPEAT_MODE_ALL }
    private val scope = MainScope()
    private val persist = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var playlistId by mutableLongStateOf(0L)
        private set
    var playlistName by mutableStateOf<String?>(null)
        private set
    /** The tracks queued, in playlist order (shuffle changes only the play order). */
    var queue by mutableStateOf<List<PlaylistTrack>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    /** The track playing, including setting changes not yet saved. */
    var current by mutableStateOf<AudioTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_ALL)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var sleep by mutableStateOf<SleepTimer?>(null)
        private set

    val hasQueue get() = queue.isNotEmpty()

    private var sleepJob: Job? = null
    private var fade = 1f
    private var saveJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncCurrent()
            override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED && sleep?.endOfTrack == true) clearEndOfTrackTimer()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "End of this track" sleep timer: the player paused itself at the end of the track.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleep?.endOfTrack == true) clearEndOfTrackTimer()
            }
        })
        scope.launch { poll() }
    }

    // ---- queue ----

    /** Plays a playlist from [startIndex]; [shuffled] turns shuffle on first. */
    fun playPlaylist(id: Long, name: String, startIndex: Int = 0, shuffled: Boolean = false) {
        scope.launch {
            val tracks = repo.listeningDao.tracks(id)
            if (tracks.isEmpty()) return@launch
            flushSave()
            playlistId = id
            playlistName = name
            queue = tracks
            player.shuffleModeEnabled = shuffled
            player.setMediaItems(tracks.map { it.toMediaItem(name) }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            player.prepare()
            player.play()
            syncCurrent()
        }
    }

    /** Refreshes the queue after the playlist was edited, keeping the current track playing. */
    fun refreshIfPlaying(id: Long) {
        if (id != playlistId || !hasQueue) return
        scope.launch {
            val tracks = repo.listeningDao.tracks(id)
            val playingEntry = queue.getOrNull(player.currentMediaItemIndex)?.entryId
            val newIndex = tracks.indexOfFirst { it.entryId == playingEntry }
            if (newIndex < 0) return@launch // the playing track was removed; leave the queue as is
            val pos = player.currentPosition
            val wasPlaying = player.playWhenReady
            queue = tracks
            player.setMediaItems(tracks.map { it.toMediaItem(playlistName.orEmpty()) }, newIndex, pos)
            player.prepare()
            player.playWhenReady = wasPlaying
            syncCurrent()
        }
    }

    fun playAt(i: Int) {
        if (i !in queue.indices) return
        player.seekTo(i, 0L)
        player.play()
    }

    fun togglePlay() = if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L); player.play() }
    fun next() = player.seekToNextMediaItem()
    /** Back to the start of the track, or to the previous one when near the start. */
    fun previous() = player.seekToPrevious()
    fun seekTo(ms: Long) { player.seekTo(ms.coerceAtLeast(0)); positionMs = ms }
    fun skip(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    /** Loop all → loop one → no loop. */
    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
    }

    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }

    fun stop() {
        flushSave()
        cancelSleepTimer()
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        index = -1
        current = null
        playlistId = 0
    }

    private fun syncCurrent() {
        flushSave()
        val i = player.currentMediaItemIndex
        index = if (queue.isEmpty()) -1 else i
        current = queue.getOrNull(i)?.track
        current?.let(::apply)
        durationMs = player.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0L
    }

    // ---- practice settings (saved to the track) ----

    fun setSpeed(speed: Float) = update { it.copy(speed = (speed * 100).toInt() / 100f) }
    fun setPitchCents(cents: Int) = update { it.copy(pitchCents = cents.coerceIn(-1200, 1200)) }
    fun setPan(p: Float) = update { it.copy(pan = p.coerceIn(-1f, 1f)) }
    fun setVolume(v: Float) = update { it.copy(volume = v.coerceIn(0f, 1f)) }

    fun setLoopStart() {
        val pos = player.currentPosition
        update { it.copy(loopStartMs = pos, loopEndMs = if (it.loopEndMs > pos) it.loopEndMs else -1, loopEnabled = it.loopEnabled && it.loopEndMs > pos) }
    }

    fun setLoopEnd() {
        val pos = player.currentPosition
        val t = current ?: return
        val start = t.loopStartMs.coerceAtLeast(0)
        if (pos <= start + 200) return
        update { it.copy(loopStartMs = start, loopEndMs = pos, loopEnabled = true) }
        player.seekTo(start)
    }

    fun toggleLoop() { current?.takeIf(::hasLoop)?.let { update { t -> t.copy(loopEnabled = !t.loopEnabled) } } }
    fun clearLoop() = update { it.copy(loopStartMs = -1, loopEndMs = -1, loopEnabled = false) }
    fun hasLoop(t: AudioTrack) = t.loopStartMs >= 0 && t.loopEndMs > t.loopStartMs

    private fun update(transform: (AudioTrack) -> AudioTrack) {
        val t = current ?: return
        val updated = transform(t)
        current = updated
        // Keep the queue's copy in step so coming back to this track keeps the change.
        queue = queue.map { if (it.track.id == updated.id) it.copy(track = updated) else it }
        apply(updated)
        saveJob?.cancel()
        saveJob = scope.launch { delay(500); flushSave() }
    }

    private fun flushSave() {
        saveJob?.cancel()
        saveJob = null
        val t = current ?: return
        val stored = t
        persist.launch { repo.audioDao.updateTrack(stored) }
    }

    private fun apply(t: AudioTrack) {
        player.playbackParameters = PlaybackParameters(t.speed.coerceIn(0.25f, 2f), 2f.pow(t.pitchCents / 1200f))
        pan.pan = t.pan
        player.volume = t.volume * fade
    }

    // ---- sleep timer (as in FoldPlayer) ----

    /** Pause in [minutes] minutes, fading out over the last 20 seconds. */
    fun startSleepTimer(minutes: Int) = sleepAt(System.currentTimeMillis() + minutes * 60_000L)

    fun extendSleepTimer(minutes: Int) {
        val endsAt = sleep?.endsAt
        if (endsAt == null) startSleepTimer(minutes) else sleepAt(maxOf(endsAt, System.currentTimeMillis()) + minutes * 60_000L)
    }

    fun sleepAtEndOfTrack() {
        cancelSleepTimer()
        player.pauseAtEndOfMediaItems = true
        sleep = SleepTimer(endOfTrack = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        setFade(1f)
        if (sleep?.endOfTrack == true) player.pauseAtEndOfMediaItems = false
        sleep = null
    }

    private fun clearEndOfTrackTimer() {
        player.pauseAtEndOfMediaItems = false
        sleep = null
    }

    private fun sleepAt(endsAt: Long) {
        cancelSleepTimer()
        sleep = SleepTimer(endsAt = endsAt)
        sleepJob = scope.launch {
            while (true) {
                val left = endsAt - System.currentTimeMillis()
                if (left <= 0) break
                setFade(sleepFadeVolume(left))
                delay(minOf(left, if (left < SLEEP_FADE_MS) 100L else 1000L))
            }
            player.pause()
            setFade(1f)
            sleepJob = null
            sleep = null
        }
    }

    private fun setFade(f: Float) {
        fade = f
        player.volume = (current?.volume ?: 1f) * f
    }

    // ---- position and A–B loop ----

    private suspend fun poll() {
        while (true) {
            val playing = player.isPlaying
            val pos = player.currentPosition
            val t = current
            if (t != null && playing && t.loopEnabled && hasLoop(t) && pos >= t.loopEndMs) {
                player.seekTo(t.loopStartMs)
                positionMs = t.loopStartMs
            } else {
                positionMs = pos
            }
            if (durationMs <= 0 && player.duration > 0) durationMs = player.duration
            delay(if (playing) 50 else 250)
        }
    }

    private fun PlaylistTrack.toMediaItem(playlist: String) = MediaItem.Builder()
        .setMediaId(entryId.toString())
        .setUri(Uri.fromFile(repo.fileFor(track)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(scoreTitle)
                .setAlbumTitle(playlist)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}
