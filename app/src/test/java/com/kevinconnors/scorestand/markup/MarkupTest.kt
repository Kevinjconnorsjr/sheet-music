package com.kevinconnors.scorestand.markup

import com.kevinconnors.scorestand.data.Converters
import com.kevinconnors.scorestand.data.Markup
import com.kevinconnors.scorestand.data.MarkupDao
import com.kevinconnors.scorestand.data.MarkupLayer
import com.kevinconnors.scorestand.data.MarkupType
import com.kevinconnors.scorestand.ui.reader.markup.MarkupSession
import com.kevinconnors.scorestand.ui.reader.markup.Stamp
import com.kevinconnors.scorestand.ui.reader.markup.isHitBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMarkupDao : MarkupDao {
    val layers = mutableListOf<MarkupLayer>()
    val markups = linkedMapOf<Long, Markup>()
    private var nextLayerId = 1L

    override suspend fun layers(scoreId: Long) = layers.filter { it.scoreId == scoreId }
    override suspend fun insertLayer(layer: MarkupLayer): Long {
        val id = nextLayerId++
        layers += layer.copy(id = id)
        return id
    }
    override suspend fun updateLayer(layer: MarkupLayer) { layers.replaceAll { if (it.id == layer.id) layer else it } }
    override suspend fun deleteLayer(id: Long) {
        layers.removeAll { it.id == id }
        markups.values.removeAll { it.layerId == id }
    }
    override suspend fun markups(scoreId: Long) = markups.values.filter { it.scoreId == scoreId }
    override suspend fun insert(items: List<Markup>) { items.forEach { markups[it.id] = it } }
    override suspend fun delete(ids: List<Long>) { ids.forEach { markups.remove(it) } }
}

private fun MarkupSession.ink(page: Int, vararg pts: Float) = Markup(
    id = newId(), scoreId = scoreId, layerId = activeLayerId, page = page,
    type = MarkupType.INK, color = 0, size = 0.004f, points = pts,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MarkupSessionTest {
    private suspend fun TestScope.session(dao: FakeMarkupDao) =
        MarkupSession(dao, scoreId = 7, scope = this).apply { load() }

    @Test
    fun loadCreatesDefaultLayer() = runTest {
        val dao = FakeMarkupDao()
        val s = session(dao)
        assertEquals(1, s.layers.size)
        assertEquals("My markings", s.layers.first().name)
        assertEquals(s.layers.first().id, s.activeLayerId)
    }

    @Test
    fun addUndoRedoKeepsMemoryAndDatabaseInSync() = runTest {
        val dao = FakeMarkupDao()
        val s = session(dao)
        val mark = s.ink(0, 0.1f, 0.1f, 0.2f, 0.2f)
        s.add(mark)
        advanceUntilIdle()
        assertEquals(listOf(mark), s.items.toList())
        assertTrue(mark.id in dao.markups)

        s.undo(); advanceUntilIdle()
        assertTrue(s.items.isEmpty())
        assertTrue(dao.markups.isEmpty())
        assertTrue(s.canRedo)

        s.redo(); advanceUntilIdle()
        assertEquals(1, s.items.size)
        assertTrue(mark.id in dao.markups)
        assertFalse(s.canRedo)
    }

    @Test
    fun eraseIsOneUndoStep() = runTest {
        val dao = FakeMarkupDao()
        val s = session(dao)
        val a = s.ink(0, 0.1f, 0.1f); val b = s.ink(0, 0.5f, 0.5f)
        s.add(a); s.add(b)
        s.removeLive(listOf(a)); s.removeLive(listOf(b)); s.commitRemoval(listOf(a, b))
        advanceUntilIdle()
        assertTrue(s.items.isEmpty())
        s.undo(); advanceUntilIdle()
        assertEquals(setOf(a.id, b.id), s.items.map { it.id }.toSet())
        assertEquals(setOf(a.id, b.id), dao.markups.keys)
    }

    @Test
    fun hiddenLayersAndOtherPagesAreNotVisible() = runTest {
        val dao = FakeMarkupDao()
        val s = session(dao)
        s.add(s.ink(0, 0.1f, 0.1f))
        s.add(s.ink(1, 0.1f, 0.1f))
        assertEquals(1, s.visibleOn(0).size)
        s.setVisible(s.layers.first(), false)
        assertTrue(s.visibleOn(0).isEmpty())
    }

    @Test
    fun clearPageOnlyTouchesActiveLayerAndPage() = runTest {
        val dao = FakeMarkupDao()
        val s = session(dao)
        val first = s.layers.first().id
        s.add(s.ink(0, 0.1f, 0.1f))
        s.add(s.ink(1, 0.1f, 0.1f))
        s.addLayer("Conductor"); advanceUntilIdle()
        s.add(s.ink(0, 0.3f, 0.3f)) // on Conductor layer
        s.setActiveLayer(first)
        s.clearPage(0); advanceUntilIdle()
        assertEquals(2, s.items.size)
        assertTrue(s.items.none { it.page == 0 && it.layerId == first })
    }
}

class HitTestTest {
    @Test
    fun inkHitAlongSegment() {
        val m = Markup(1, 1, 1, 0, MarkupType.INK, 0, 0.004f, floatArrayOf(0f, 0f, 1f, 0f))
        assertTrue(m.isHitBy(0.5f, 0.01f, 0.01f))
        assertFalse(m.isHitBy(0.5f, 0.1f, 0.01f))
    }

    @Test
    fun stampHitBox() {
        val m = Markup(1, 1, 1, 0, MarkupType.STAMP, 0, 0.05f, x = 0.5f, y = 0.5f, text = Stamp.CRESC.name)
        assertTrue(m.isHitBy(0.55f, 0.5f, 0.005f)) // hairpins are wide
        assertFalse(m.isHitBy(0.5f, 0.6f, 0.005f))
    }

    @Test
    fun pointsRoundTripThroughDatabaseConverter() {
        val c = Converters()
        val pts = floatArrayOf(0.1f, 0.25f, 0.333f, 1.5f)
        assertArrayEquals(pts, c.bytesToFloats(c.floatsToBytes(pts)), 0f)
        assertNull(c.floatsToBytes(null))
    }
}
