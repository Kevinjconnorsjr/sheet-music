package com.kevinconnors.scorestand.ui.setlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinconnors.scorestand.ScoreStandApp
import com.kevinconnors.scorestand.data.Score
import com.kevinconnors.scorestand.data.SetlistItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistViewModel(app: Application, private val setlistId: Long) : AndroidViewModel(app) {
    private val repo = (app as ScoreStandApp).repository

    val setlist = repo.setlistDao.observe(setlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items = repo.setlistDao.observeItems(setlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allScores = repo.scoreDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun thumbnailFor(score: Score) = repo.thumbnailFor(score)

    fun rename(name: String) = viewModelScope.launch {
        setlist.value?.let { repo.setlistDao.update(it.copy(name = name.trim())) }
    }

    fun delete() = viewModelScope.launch { repo.setlistDao.delete(setlistId) }

    fun addScores(ids: List<Long>) = viewModelScope.launch { repo.setlistDao.addScores(setlistId, ids) }

    fun remove(item: SetlistItem) = viewModelScope.launch {
        repo.setlistDao.deleteEntry(item.entryId)
        repo.setlistDao.reorder(items.value.filter { it.entryId != item.entryId }.map { it.entryId })
    }

    fun move(from: Int, to: Int) = viewModelScope.launch {
        val list = items.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return@launch
        list.add(to, list.removeAt(from))
        repo.setlistDao.reorder(list.map { it.entryId })
    }
}
