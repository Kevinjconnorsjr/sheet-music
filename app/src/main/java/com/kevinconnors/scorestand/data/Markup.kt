package com.kevinconnors.scorestand.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Update
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class MarkupType { INK, HIGHLIGHT, TEXT, STAMP }

/** A named, hideable group of markings on one score (e.g. "My markings", "Conductor"). */
@Entity(
    tableName = "markup_layers",
    foreignKeys = [ForeignKey(entity = Score::class, parentColumns = ["id"], childColumns = ["scoreId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scoreId")],
)
data class MarkupLayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreId: Long,
    val name: String,
    val visible: Boolean = true,
    val position: Int = 0,
)

/**
 * One marking on a page. Coordinates are in "page-width units": x and y are divided by the
 * page's width, so markings scale with the page at any zoom or screen size.
 *
 * - INK / HIGHLIGHT: [points] holds x0,y0,x1,y1,…; [size] is the stroke width.
 * - TEXT: [text] drawn with its top-left at ([x], [y]); [size] is the font size.
 * - STAMP: [text] holds the [com.kevinconnors.scorestand.ui.reader.markup.Stamp] name, centred on
 *   ([x], [y]); [size] is the symbol size.
 */
@Entity(
    tableName = "markups",
    foreignKeys = [
        ForeignKey(entity = Score::class, parentColumns = ["id"], childColumns = ["scoreId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MarkupLayer::class, parentColumns = ["id"], childColumns = ["layerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("scoreId"), Index("layerId")],
)
class Markup(
    /** Assigned by the app (not the database) so new marks show instantly. */
    @PrimaryKey val id: Long,
    val scoreId: Long,
    val layerId: Long,
    val page: Int,
    val type: MarkupType,
    val color: Int,
    val size: Float,
    val points: FloatArray? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val text: String? = null,
) {
    override fun equals(other: Any?) = other is Markup && other.id == id
    override fun hashCode() = id.hashCode()
}

/**
 * A tappable jump in the music — a repeat, D.S., D.C., coda… Tapping the badge at
 * ([fromX], [fromY]) on [fromPage] goes to [toPage], scrolled to [toY] in continuous mode.
 * Coordinates are in page-width units like [Markup].
 */
@Entity(
    tableName = "page_links",
    foreignKeys = [ForeignKey(entity = Score::class, parentColumns = ["id"], childColumns = ["scoreId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scoreId")],
)
data class PageLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreId: Long,
    val fromPage: Int,
    val fromX: Float,
    val fromY: Float,
    val toPage: Int,
    val toY: Float,
    val label: String,
)

class Converters {
    @TypeConverter
    fun floatsToBytes(value: FloatArray?): ByteArray? = value?.let {
        ByteBuffer.allocate(it.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(it) }.array()
    }

    @TypeConverter
    fun bytesToFloats(value: ByteArray?): FloatArray? = value?.let {
        val buffer = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        FloatArray(buffer.remaining()).also { out -> buffer.get(out) }
    }
}

@Dao
interface MarkupDao {
    @Query("SELECT * FROM markup_layers WHERE scoreId = :scoreId ORDER BY position, id")
    suspend fun layers(scoreId: Long): List<MarkupLayer>

    @Insert
    suspend fun insertLayer(layer: MarkupLayer): Long

    @Update
    suspend fun updateLayer(layer: MarkupLayer)

    @Query("DELETE FROM markup_layers WHERE id = :id")
    suspend fun deleteLayer(id: Long)

    @Query("SELECT * FROM markups WHERE scoreId = :scoreId")
    suspend fun markups(scoreId: Long): List<Markup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<Markup>)

    @Query("DELETE FROM markups WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM page_links WHERE scoreId = :scoreId")
    suspend fun links(scoreId: Long): List<PageLink>

    @Insert
    suspend fun insertLink(link: PageLink): Long

    @Query("DELETE FROM page_links WHERE id = :id")
    suspend fun deleteLink(id: Long)
}
