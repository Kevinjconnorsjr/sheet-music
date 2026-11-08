package com.kevinconnors.scorestand.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A recording attached to a score, with the practice settings last used for it. */
@Entity(
    tableName = "audio_tracks",
    foreignKeys = [ForeignKey(entity = Score::class, parentColumns = ["id"], childColumns = ["scoreId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scoreId")],
)
data class AudioTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreId: Long,
    val title: String,
    /** File name inside the app's private `audio/` directory. */
    val fileName: String,
    val durationMs: Long,
    val position: Int = 0,
    /** Playback rate; 1 = original. Pitch is unaffected. */
    val speed: Float = 1f,
    /** Pitch shift in cents (100 = one semitone). Tempo is unaffected. */
    val pitchCents: Int = 0,
    /** -1 = left only, 0 = centre, 1 = right only. */
    val pan: Float = 0f,
    val volume: Float = 1f,
    val loopStartMs: Long = -1,
    val loopEndMs: Long = -1,
    val loopEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A named point in a track; if [page] ≥ 0 the score can turn to that page when playback reaches it. */
@Entity(
    tableName = "audio_markers",
    foreignKeys = [ForeignKey(entity = AudioTrack::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")],
)
data class AudioMarker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val positionMs: Long,
    val label: String,
    val page: Int = -1,
)

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_tracks WHERE scoreId = :scoreId ORDER BY position, id")
    fun observeTracks(scoreId: Long): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE scoreId = :scoreId")
    suspend fun tracksFor(scoreId: Long): List<AudioTrack>

    @Query("SELECT COALESCE(MAX(position), -1) FROM audio_tracks WHERE scoreId = :scoreId")
    suspend fun maxPosition(scoreId: Long): Int

    @Insert
    suspend fun insertTrack(track: AudioTrack): Long

    @Update
    suspend fun updateTrack(track: AudioTrack)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT * FROM audio_markers WHERE trackId = :trackId ORDER BY positionMs")
    fun observeMarkers(trackId: Long): Flow<List<AudioMarker>>

    @Insert
    suspend fun insertMarker(marker: AudioMarker): Long

    @Update
    suspend fun updateMarker(marker: AudioMarker)

    @Query("DELETE FROM audio_markers WHERE id = :id")
    suspend fun deleteMarker(id: Long)
}
