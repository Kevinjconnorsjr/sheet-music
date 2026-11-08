package com.kevinconnors.scorestand.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pinch-zoom state for the reader: scale ≥ 1 and an offset kept inside the screen. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    /** [scale] rounded and updated only when a gesture ends — used to re-render pages sharply. */
    var settledScale by mutableFloatStateOf(1f)
        private set

    var size: IntSize = IntSize.Zero

    val isZoomed get() = scale > 1.01f

    /**
     * Applies a pinch/pan around [centroid] (screen pixels). Returns the part of [pan] that
     * couldn't be used because the page edge was reached, so callers can scroll or turn pages.
     */
    fun transform(centroid: Offset, pan: Offset, zoom: Float): Offset {
        val newScale = (scale * zoom).coerceIn(MIN, MAX)
        val z = newScale / scale
        val wanted = centroid - (centroid - offset) * z + pan
        val minX = size.width - size.width * newScale
        val minY = size.height - size.height * newScale
        val clamped = Offset(wanted.x.coerceIn(minX, 0f), wanted.y.coerceIn(minY, 0f))
        scale = newScale
        offset = clamped
        return wanted - clamped
    }

    fun settle() {
        if (scale < 1.02f) { scale = 1f; offset = Offset.Zero }
        // Half steps keep re-renders (and the bitmap cache) in check.
        settledScale = ((scale * 2).roundToInt() / 2f).coerceAtLeast(1f)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
        settledScale = 1f
    }

    companion object {
        const val MIN = 1f
        const val MAX = 4f
    }
}

/** How a single finger behaves while zoomed in. */
enum class SingleFingerPan {
    /** Nothing: one finger draws (or the layout handles it). */
    NONE,
    /** Sideways moves pan the zoomed page; up/down still scrolls the music. */
    HORIZONTAL,
    /** Moves pan the zoomed page in any direction (paged layouts). */
    BOTH,
}

/** Applies the zoom transform to the content. */
fun Modifier.zoomed(state: ZoomState) = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    translationX = state.offset.x
    translationY = state.offset.y
    transformOrigin = TransformOrigin(0f, 0f)
}

/**
 * Two fingers always zoom and pan (and never draw). Movement past the page edges goes to
 * [onScroll] (continuous layouts) or, once the fingers lift, to [onSwipePage] (paged layouts).
 * Single taps and single-finger gestures pass through to the page unless [singleFinger] says
 * to pan while zoomed in.
 */
fun Modifier.zoomGestures(
    state: ZoomState,
    singleFinger: SingleFingerPan,
    onScroll: (dy: Float) -> Unit,
    onSwipePage: (forward: Boolean) -> Unit,
) = pointerInput(state, singleFinger) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var multiTouch = false
        var swipeX = 0f
        var singleTravel = Offset.Zero
        var singlePanning = false
        while (true) {
            // The Initial pass sees touches before the pages, pager and drawing layer do.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                multiTouch = true
                val leftover = state.transform(event.calculateCentroid(useCurrent = true), event.calculatePan(), event.calculateZoom())
                swipeX += leftover.x
                if (abs(leftover.y) > 0f) onScroll(-leftover.y / state.scale)
                event.changes.forEach { it.consume() }
            } else if (!multiTouch && singleFinger != SingleFingerPan.NONE && state.isZoomed) {
                val change = pressed.first()
                val delta = change.position - change.previousPosition
                singleTravel += delta
                if (!singlePanning && singleTravel.getDistance() > viewConfiguration.touchSlop) singlePanning = true
                if (singlePanning) {
                    if (singleFinger == SingleFingerPan.BOTH) {
                        state.transform(Offset.Zero, delta, 1f)
                        change.consume()
                    } else {
                        // Leave the touch for the list so it still scrolls up and down.
                        state.transform(Offset.Zero, Offset(delta.x, 0f), 1f)
                    }
                }
            }
        }
        if (multiTouch) {
            state.settle()
            // A two-finger swipe at normal size turns the page in paged layouts.
            if (!state.isZoomed && abs(swipeX) > size.width * 0.18f) onSwipePage(swipeX < 0)
        }
    }
}
