package com.kevinconnors.scorestand.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kevinconnors.scorestand.render.PageRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Lets the reader chrome, tap zones and page-turn pedals drive whichever layout is showing. */
interface PageController {
    val currentPage: Int
    suspend fun next(): Boolean
    suspend fun previous(): Boolean
    suspend fun jumpTo(page: Int)

    /** Jumps to a spot on a page ([yUnits] in page-width units). Paged layouts just show the page. */
    suspend fun jumpTo(page: Int, yUnits: Float) = jumpTo(page)
}

@Composable
private fun RegisterController(controller: PageController, register: (PageController?) -> Unit) {
    DisposableEffect(controller) {
        register(controller)
        onDispose { register(null) }
    }
}

@Composable
private fun rememberPageBitmap(renderer: PageRenderer, index: Int, widthPx: Int): State<ImageBitmap?> {
    // Keeps showing the previous bitmap while a sharper one renders, so resizes don't flash white.
    val state = remember(renderer) { mutableStateOf(renderer.cached(index, widthPx)?.asImageBitmap()) }
    LaunchedEffect(renderer, index, widthPx) {
        if (widthPx > 0) state.value = renderer.render(index, widthPx).asImageBitmap()
    }
    return state
}

/** Content drawn on top of each page, sized exactly to it (used for markings). */
val LocalPageOverlay = compositionLocalOf<(@Composable (page: Int) -> Unit)?> { null }

/** A page image that fills its modifier's size (callers size it to the page's aspect ratio). */
@Composable
fun PageBitmap(renderer: PageRenderer, index: Int, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color.White)) {
        val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 1080
        val image by rememberPageBitmap(renderer, index, widthPx)
        image?.let { Image(it, contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        LocalPageOverlay.current?.invoke(index)
    }
}

/** Fits a page inside the available space, preserving its aspect ratio. */
@Composable
fun FittedPage(
    renderer: PageRenderer,
    index: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val ratio = renderer.aspectRatio(index)
        val boxRatio = maxWidth / maxHeight
        val (w, h) = if (ratio > boxRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
        PageBitmap(renderer, index, Modifier.size(w, h))
    }
}

@Composable
fun SinglePageView(renderer: PageRenderer, initialPage: Int, register: (PageController?) -> Unit, userScrollEnabled: Boolean) {
    val count = renderer.pageCount
    val state = rememberPagerState(initialPage.coerceIn(0, count - 1)) { count }
    val controller = remember(state) {
        object : PageController {
            override val currentPage get() = state.currentPage
            override suspend fun next() =
                if (state.currentPage < count - 1) { state.animateScrollToPage(state.currentPage + 1); true } else false
            override suspend fun previous() =
                if (state.currentPage > 0) { state.animateScrollToPage(state.currentPage - 1); true } else false
            override suspend fun jumpTo(page: Int) = state.scrollToPage(page.coerceIn(0, count - 1))
        }
    }
    RegisterController(controller, register)
    HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }, userScrollEnabled = userScrollEnabled) { page ->
        FittedPage(renderer, page, Modifier.fillMaxSize().padding(4.dp))
    }
}

@Composable
fun TwoPageView(renderer: PageRenderer, initialPage: Int, register: (PageController?) -> Unit, userScrollEnabled: Boolean) {
    val count = renderer.pageCount
    val spreads = (count + 1) / 2
    val state = rememberPagerState((initialPage / 2).coerceIn(0, spreads - 1)) { spreads }
    val controller = remember(state) {
        object : PageController {
            override val currentPage get() = state.currentPage * 2
            override suspend fun next() =
                if (state.currentPage < spreads - 1) { state.animateScrollToPage(state.currentPage + 1); true } else false
            override suspend fun previous() =
                if (state.currentPage > 0) { state.animateScrollToPage(state.currentPage - 1); true } else false
            override suspend fun jumpTo(page: Int) = state.scrollToPage((page / 2).coerceIn(0, spreads - 1))
        }
    }
    RegisterController(controller, register)
    HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }, userScrollEnabled = userScrollEnabled) { spread ->
        val left = spread * 2
        val right = left + 1
        Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FittedPage(renderer, left, Modifier.weight(1f).fillMaxHeight(), Alignment.CenterEnd)
            if (right < count) {
                FittedPage(renderer, right, Modifier.weight(1f).fillMaxHeight(), Alignment.CenterStart)
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Half-page turns: each "turn" first replaces only the top half with the next page,
 * so the player can read ahead while still finishing the bottom of the current page.
 * Positions run 0..2(n-1); even = a whole page, odd = top of page p+1 over bottom of page p.
 */
@Composable
fun HalfPageView(renderer: PageRenderer, initialPage: Int, register: (PageController?) -> Unit, wholePagesOnly: Boolean) {
    val count = renderer.pageCount
    val maxPos = (count - 1) * 2
    var pos by remember { mutableIntStateOf((initialPage * 2).coerceIn(0, maxPos)) }
    val controller = remember {
        object : PageController {
            override val currentPage get() = pos / 2
            override suspend fun next() = if (pos < maxPos) { pos++; true } else false
            override suspend fun previous() = if (pos > 0) { pos--; true } else false
            override suspend fun jumpTo(page: Int) { pos = (page * 2).coerceIn(0, maxPos) }
        }
    }
    RegisterController(controller, register)

    val page = pos / 2
    // While marking up, show whole pages so every mark lands on a definite page.
    if (pos % 2 == 0 || wholePagesOnly) {
        FittedPage(renderer, page, Modifier.fillMaxSize().padding(4.dp))
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            val ratio = renderer.aspectRatio(page)
            val boxRatio = maxWidth / maxHeight
            val (w, h) = if (ratio > boxRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
            Box(Modifier.size(w, h)) {
                Column(Modifier.fillMaxSize()) {
                    // Top half: the next page. Each half shows a full-size page, clipped.
                    Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                        PageBitmap(renderer, page + 1, Modifier.wrapContentHeight(Alignment.Top, unbounded = true).size(w, h))
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                        PageBitmap(renderer, page, Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true).size(w, h))
                    }
                }
                HorizontalDivider(Modifier.align(Alignment.Center), thickness = 3.dp, color = Color(0xFF1E3A5F))
            }
        }
    }
}

/**
 * All pages stacked in one continuous vertical strip. Tap zones / pedals scroll by most of a
 * screen; optional auto-scroll moves the music up at a steady speed.
 */
@Composable
fun VerticalScrollView(
    renderer: PageRenderer,
    initialPage: Int,
    register: (PageController?) -> Unit,
    widthFraction: Float,
    userScrollEnabled: Boolean,
    autoScroll: Boolean,
    autoScrollDpPerSecond: Float,
    onAutoScrollReachedEnd: () -> Unit,
) {
    val count = renderer.pageCount
    val listState = rememberLazyListState(initialPage.coerceIn(0, count - 1))
    var viewportHeight by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val currentWidthFraction by rememberUpdatedState(widthFraction)
    val controller = remember(listState) {
        object : PageController {
            override val currentPage: Int
                get() {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    if (visible.isEmpty()) return listState.firstVisibleItemIndex
                    val viewEnd = listState.layoutInfo.viewportEndOffset
                    // The page occupying most of the screen.
                    return visible.maxBy { item ->
                        minOf(item.offset + item.size, viewEnd) - maxOf(item.offset, 0)
                    }.index
                }
            override suspend fun next(): Boolean {
                if (!listState.canScrollForward) return false
                listState.animateScrollBy(viewportHeight * 0.85f); return true
            }
            override suspend fun previous(): Boolean {
                if (!listState.canScrollBackward) return false
                listState.animateScrollBy(-viewportHeight * 0.85f); return true
            }
            override suspend fun jumpTo(page: Int) = listState.scrollToItem(page.coerceIn(0, count - 1))
            override suspend fun jumpTo(page: Int, yUnits: Float) {
                // Land a little above the target so the music leading into it is visible.
                val pageWidthPx = viewportWidth * currentWidthFraction
                val offset = (yUnits * pageWidthPx - viewportHeight * 0.15f).toInt().coerceAtLeast(0)
                listState.animateScrollToItem(page.coerceIn(0, count - 1), offset)
            }
        }
    }
    RegisterController(controller, register)

    val density = LocalDensity.current
    val speedPx by rememberUpdatedState(with(density) { autoScrollDpPerSecond.dp.toPx() })
    val onEnd by rememberUpdatedState(onAutoScrollReachedEnd)
    LaunchedEffect(autoScroll, listState) {
        if (!autoScroll) return@LaunchedEffect
        var last = withFrameNanos { it }
        var carry = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            carry += speedPx * (now - last) / 1_000_000_000f
            last = now
            if (!listState.canScrollForward) { onEnd(); break }
            if (carry >= 1f) {
                val step = carry.toInt().toFloat()
                carry -= step
                try {
                    listState.scrollBy(step)
                } catch (e: CancellationException) {
                    // A finger on the screen interrupts us; pause briefly, then carry on.
                    if (!isActive) throw e
                    delay(600)
                    last = withFrameNanos { it }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height; viewportWidth = it.width },
        userScrollEnabled = userScrollEnabled,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count, key = { it }) { i ->
            Box(Modifier.fillMaxWidth(widthFraction)) {
                PageBitmap(renderer, i, Modifier.fillMaxWidth().aspectRatio(renderer.aspectRatio(i)))
            }
        }
    }
}
