package com.kevinconnors.scorestand.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How the reader lays out the pages of a score. */
enum class ViewMode(val label: String) {
    SINGLE("Single page"),
    TWO_PAGE("Two pages side by side"),
    HALF_PAGE("Half-page turns"),
    VERTICAL("Continuous vertical scroll"),
}

@Entity(tableName = "scores")
data class Score(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val composer: String = "",
    /** Comma-separated tags. */
    val tags: String = "",
    /** File name inside the app's private `scores/` directory. */
    val fileName: String,
    val mimeType: String,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0,
    val lastPage: Int = 0,
    val viewMode: ViewMode = ViewMode.SINGLE,
    /** Metronome tempo remembered for this piece; 0 = not set yet. */
    @ColumnInfo(defaultValue = "0") val metronomeBpm: Int = 0,
    @ColumnInfo(defaultValue = "4") val beatsPerBar: Int = 4,
    /** Crop the white margins so the music fills more of the screen. */
    @ColumnInfo(defaultValue = "0") val trimMargins: Boolean = false,
) {
    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "setlists")
data class Setlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "setlist_entries",
    foreignKeys = [
        ForeignKey(entity = Setlist::class, parentColumns = ["id"], childColumns = ["setlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Score::class, parentColumns = ["id"], childColumns = ["scoreId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("setlistId"), Index("scoreId")],
)
data class SetlistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setlistId: Long,
    val scoreId: Long,
    val position: Int,
)

data class SetlistWithCount(
    @Embedded val setlist: Setlist,
    val count: Int,
)

data class SetlistItem(
    val entryId: Long,
    val position: Int,
    @Embedded val score: Score,
)
