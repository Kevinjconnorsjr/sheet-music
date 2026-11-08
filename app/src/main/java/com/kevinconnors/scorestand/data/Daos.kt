package com.kevinconnors.scorestand.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Score>>

    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun get(id: Long): Score?

    @Insert
    suspend fun insert(score: Score): Long

    @Update
    suspend fun update(score: Score)

    @Delete
    suspend fun delete(score: Score)

    @Query("UPDATE scores SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: Long, time: Long)

    @Query("UPDATE scores SET lastPage = :page WHERE id = :id")
    suspend fun updateLastPage(id: Long, page: Int)

    @Query("UPDATE scores SET viewMode = :mode WHERE id = :id")
    suspend fun updateViewMode(id: Long, mode: ViewMode)

    @Query("UPDATE scores SET transposeSemitones = :semitones WHERE id = :id")
    suspend fun updateTranspose(id: Long, semitones: Int)

    @Query("UPDATE scores SET trimMargins = :trim WHERE id = :id")
    suspend fun updateTrimMargins(id: Long, trim: Boolean)

    @Query("UPDATE scores SET metronomeBpm = :bpm, beatsPerBar = :beatsPerBar WHERE id = :id")
    suspend fun updateMetronome(id: Long, bpm: Int, beatsPerBar: Int)
}

@Dao
interface SetlistDao {
    @Query(
        "SELECT s.*, (SELECT COUNT(*) FROM setlist_entries e WHERE e.setlistId = s.id) AS count " +
            "FROM setlists s ORDER BY s.name COLLATE NOCASE"
    )
    fun observeSetlists(): Flow<List<SetlistWithCount>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    fun observe(id: Long): Flow<Setlist?>

    @Query(
        "SELECT e.id AS entryId, e.position AS position, sc.* FROM setlist_entries e " +
            "JOIN scores sc ON sc.id = e.scoreId WHERE e.setlistId = :setlistId ORDER BY e.position"
    )
    fun observeItems(setlistId: Long): Flow<List<SetlistItem>>

    @Query(
        "SELECT e.id AS entryId, e.position AS position, sc.* FROM setlist_entries e " +
            "JOIN scores sc ON sc.id = e.scoreId WHERE e.setlistId = :setlistId ORDER BY e.position"
    )
    suspend fun getItems(setlistId: Long): List<SetlistItem>

    @Insert
    suspend fun insert(setlist: Setlist): Long

    @Update
    suspend fun update(setlist: Setlist)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEntry(entry: SetlistEntry): Long

    @Query("DELETE FROM setlist_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM setlist_entries WHERE setlistId = :setlistId")
    suspend fun maxPosition(setlistId: Long): Int

    @Query("UPDATE setlist_entries SET position = :position WHERE id = :entryId")
    suspend fun updatePosition(entryId: Long, position: Int)

    @Transaction
    suspend fun addScores(setlistId: Long, scoreIds: List<Long>) {
        var pos = maxPosition(setlistId)
        for (id in scoreIds) insertEntry(SetlistEntry(setlistId = setlistId, scoreId = id, position = ++pos))
    }

    /** Rewrites positions so entries appear in the given order. */
    @Transaction
    suspend fun reorder(entryIdsInOrder: List<Long>) {
        entryIdsInOrder.forEachIndexed { index, entryId -> updatePosition(entryId, index) }
    }
}
