package com.kevinconnors.scorestand.ui.playlist

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.ScoreStandApp
import com.kevinconnors.scorestand.data.ListeningPlaylist
import com.kevinconnors.scorestand.data.PlaylistTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Listening playlists: create, fill with learning tracks, reorder, and play. */
@OptIn(UnstableApi::class)
class ListeningViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ScoreStandApp).repository
    private val dao = repo.listeningDao
    val player = app.let { (it as ScoreStandApp).playlistPlayer }

    val playlists = dao.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTracks = dao.observeAllTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playlist(id: Long): Flow<ListeningPlaylist?> = dao.observe(id)
    fun tracks(id: Long): Flow<List<PlaylistTrack>> = dao.observeTracks(id)

    fun create(name: String, trackIds: List<Long> = emptyList(), then: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = dao.insert(ListeningPlaylist(name = name.trim()))
        if (trackIds.isNotEmpty()) dao.addTracks(id, trackIds)
        then(id)
    }

    fun rename(p: ListeningPlaylist, name: String) = viewModelScope.launch { dao.update(p.copy(name = name.trim())) }

    fun delete(p: ListeningPlaylist) = viewModelScope.launch {
        if (player.playlistId == p.id) player.stop()
        dao.delete(p.id)
    }

    fun addTracks(playlistId: Long, trackIds: List<Long>) = viewModelScope.launch {
        dao.addTracks(playlistId, trackIds)
        player.refreshIfPlaying(playlistId)
    }

    fun remove(playlistId: Long, items: List<PlaylistTrack>, item: PlaylistTrack) = viewModelScope.launch {
        dao.deleteEntry(item.entryId)
        dao.reorder(items.filter { it.entryId != item.entryId }.map { it.entryId })
        player.refreshIfPlaying(playlistId)
    }

    fun move(playlistId: Long, items: List<PlaylistTrack>, from: Int, to: Int) = viewModelScope.launch {
        if (from !in items.indices || to !in items.indices) return@launch
        val list = items.toMutableList().apply { add(to, removeAt(from)) }
        dao.reorder(list.map { it.entryId })
        player.refreshIfPlaying(playlistId)
    }
}
