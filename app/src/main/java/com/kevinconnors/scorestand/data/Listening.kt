package com.kevinconnors.scorestand.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A listening playlist of learning tracks (recordings attached to scores). */
@Entity(tableName = "listening_playlists")
data class ListeningPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "listening_entries",
    foreignKeys = [
        ForeignKey(entity = ListeningPlaylist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AudioTrack::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class ListeningEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

data class ListeningPlaylistWithCount(@Embedded val playlist: ListeningPlaylist, val count: Int)

/** A track in a playlist, with the title of the score it belongs to. */
data class PlaylistTrack(
    val entryId: Long,
    val entryPosition: Int,
    val scoreTitle: String,
    @Embedded val track: AudioTrack,
)

/** Any track in the library, with its score's title (for adding to playlists). */
data class TrackWithScore(val scoreTitle: String, @Embedded val track: AudioTrack)

@Dao
interface ListeningDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM listening_entries e WHERE e.playlistId = p.id) AS count " +
            "FROM listening_playlists p ORDER BY p.name COLLATE NOCASE"
    )
    fun observePlaylists(): Flow<List<ListeningPlaylistWithCount>>

    @Query("SELECT * FROM listening_playlists ORDER BY name COLLATE NOCASE")
    suspend fun playlists(): List<ListeningPlaylist>

    @Query("SELECT * FROM listening_playlists WHERE id = :id")
    fun observe(id: Long): Flow<ListeningPlaylist?>

    @Query(
        "SELECT e.id AS entryId, e.position AS entryPosition, s.title AS scoreTitle, t.* FROM listening_entries e " +
            "JOIN audio_tracks t ON t.id = e.trackId JOIN scores s ON s.id = t.scoreId " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position"
    )
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrack>>

    @Query(
        "SELECT e.id AS entryId, e.position AS entryPosition, s.title AS scoreTitle, t.* FROM listening_entries e " +
            "JOIN audio_tracks t ON t.id = e.trackId JOIN scores s ON s.id = t.scoreId " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position"
    )
    suspend fun tracks(playlistId: Long): List<PlaylistTrack>

    @Query("SELECT s.title AS scoreTitle, t.* FROM audio_tracks t JOIN scores s ON s.id = t.scoreId ORDER BY s.title COLLATE NOCASE, t.position, t.id")
    fun observeAllTracks(): Flow<List<TrackWithScore>>

    @Insert
    suspend fun insert(playlist: ListeningPlaylist): Long

    @Update
    suspend fun update(playlist: ListeningPlaylist)

    @Query("DELETE FROM listening_playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEntry(entry: ListeningEntry): Long

    @Query("DELETE FROM listening_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM listening_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE listening_entries SET position = :position WHERE id = :entryId")
    suspend fun updatePosition(entryId: Long, position: Int)

    @Transaction
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        var pos = maxPosition(playlistId)
        for (id in trackIds) insertEntry(ListeningEntry(playlistId = playlistId, trackId = id, position = ++pos))
    }

    @Transaction
    suspend fun reorder(entryIdsInOrder: List<Long>) {
        entryIdsInOrder.forEachIndexed { i, id -> updatePosition(id, i) }
    }
}
