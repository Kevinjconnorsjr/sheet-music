package com.kevinconnors.scorestand.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HorizontalSplit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinconnors.scorestand.data.ViewMode
import com.kevinconnors.scorestand.ui.theme.ReaderBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(scoreId: Long, setlistId: Long, setlistIndex: Int, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-$scoreId-$setlistId-$setlistIndex",
        factory = viewModelFactory {
            initializer { ReaderViewModel(this[APPLICATION_KEY]!!, scoreId, setlistId, setlistIndex) }
        },
    )
    val loaded = vm.loaded
    Box(Modifier.fillMaxSize().background(ReaderBackground), contentAlignment = Alignment.Center) {
        when {
            vm.error != null -> Text(vm.error!!, color = Color.White, modifier = Modifier.padding(32.dp))
            loaded == null -> CircularProgressIndicator()
            else -> key(loaded.score.id) { ReaderContent(vm, loaded, onBack) }
        }
    }
}

private fun ViewMode.icon(): ImageVector = when (this) {
    ViewMode.SINGLE -> Icons.Outlined.Description
    ViewMode.TWO_PAGE -> Icons.Outlined.AutoStories
    ViewMode.HALF_PAGE -> Icons.Outlined.HorizontalSplit
    ViewMode.VERTICAL -> Icons.Outlined.SwapVert
}

@Composable
private fun ReaderContent(vm: ReaderViewModel, loaded: ReaderViewModel.Loaded, onBack: () -> Unit) {
    val score = loaded.score
    val renderer = loaded.renderer
    val pageCount = renderer.pageCount
    val scope = rememberCoroutineScope()

    var controller by remember { mutableStateOf<PageController?>(null) }
    val register: (PageController?) -> Unit = { c -> controller = c }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var lastKnownPage by remember { mutableIntStateOf(loaded.initialPage) }

    // Vertical-scroll settings, kept for the session.
    var autoScroll by remember { mutableStateOf(false) }
    var autoSpeed by rememberSaveable { mutableFloatStateOf(24f) }
    var widthFraction by rememberSaveable { mutableFloatStateOf(1f) }

    ImmersiveMode(hideSystemBars = !chromeVisible)

    LaunchedEffect(controller) {
        snapshotFlow { controller?.currentPage }.filterNotNull().distinctUntilChanged().collect {
            lastKnownPage = it
            vm.saveProgress(it)
        }
    }

    fun goNext() = scope.launch { if (controller?.next() == false) vm.nextPiece() }
    fun goPrevious() = scope.launch { if (controller?.previous() == false) vm.previousPiece() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            // Bluetooth page-turn pedals present themselves as keyboards.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.PageDown, Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter, Key.NumPadEnter -> { goNext(); true }
                    Key.PageUp, Key.DirectionLeft, Key.DirectionUp -> { goPrevious(); true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> goPrevious()
                        offset.x > third * 2 -> goNext()
                        else -> chromeVisible = !chromeVisible
                    }
                }
            },
    ) {
        key(score.viewMode) {
            when (score.viewMode) {
                ViewMode.SINGLE -> SinglePageView(renderer, lastKnownPage, register)
                ViewMode.TWO_PAGE -> TwoPageView(renderer, lastKnownPage, register)
                ViewMode.HALF_PAGE -> HalfPageView(renderer, lastKnownPage, register)
                ViewMode.VERTICAL -> VerticalScrollView(
                    renderer = renderer,
                    initialPage = lastKnownPage,
                    register = register,
                    widthFraction = widthFraction,
                    autoScroll = autoScroll,
                    autoScrollDpPerSecond = autoSpeed,
                    onAutoScrollReachedEnd = { autoScroll = false },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopChrome(
                vm = vm,
                title = score.title,
                composer = score.composer,
                mode = score.viewMode,
                onBack = onBack,
                onMode = { mode ->
                    autoScroll = false
                    vm.setViewMode(mode, controller?.currentPage ?: lastKnownPage)
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomChrome(
                page = controller?.currentPage ?: lastKnownPage,
                pageCount = pageCount,
                onJump = { p -> scope.launch { controller?.jumpTo(p) } },
                vertical = score.viewMode == ViewMode.VERTICAL,
                autoScroll = autoScroll,
                onToggleAutoScroll = { autoScroll = !autoScroll },
                autoSpeed = autoSpeed,
                onAutoSpeed = { autoSpeed = it },
                widthFraction = widthFraction,
                onWidthFraction = { widthFraction = it },
            )
        }
    }
}

@Composable
private fun TopChrome(
    vm: ReaderViewModel,
    title: String,
    composer: String,
    mode: ViewMode,
    onBack: () -> Unit,
    onMode: (ViewMode) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = if (vm.inSetlist) {
                    "${vm.setlistName ?: "Setlist"} · ${vm.setlistIndex + 1} of ${vm.setlistItems.size}"
                } else composer
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (vm.inSetlist) {
                IconButton(enabled = vm.hasPreviousPiece(), onClick = vm::previousPiece) { Icon(Icons.Outlined.SkipPrevious, "Previous piece") }
                IconButton(enabled = vm.hasNextPiece(), onClick = vm::nextPiece) { Icon(Icons.Outlined.SkipNext, "Next piece") }
            }
            Box {
                IconButton(onClick = { modeMenu = true }) { Icon(mode.icon(), "Page layout") }
                DropdownMenu(modeMenu, { modeMenu = false }) {
                    ViewMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            leadingIcon = { Icon(m.icon(), null) },
                            trailingIcon = { if (m == mode) Icon(Icons.Outlined.Check, null) },
                            onClick = { modeMenu = false; if (m != mode) onMode(m) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomChrome(
    page: Int,
    pageCount: Int,
    onJump: (Int) -> Unit,
    vertical: Boolean,
    autoScroll: Boolean,
    onToggleAutoScroll: () -> Unit,
    autoSpeed: Float,
    onAutoSpeed: (Float) -> Unit,
    widthFraction: Float,
    onWidthFraction: (Float) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (vertical) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onToggleAutoScroll) {
                        Icon(if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (autoScroll) "Stop auto-scroll" else "Start auto-scroll")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Scroll speed", style = MaterialTheme.typography.labelMedium)
                    Slider(autoSpeed, onAutoSpeed, valueRange = 4f..160f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("Width", style = MaterialTheme.typography.labelMedium)
                    Slider(widthFraction, onWidthFraction, valueRange = 0.4f..1f, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                var dragging by remember { mutableStateOf(false) }
                var sliderValue by remember { mutableFloatStateOf(page.toFloat()) }
                if (!dragging) sliderValue = page.toFloat()
                val shown = if (dragging) sliderValue.roundToInt() else page
                Text("Page ${shown + 1} of $pageCount", style = MaterialTheme.typography.labelLarge)
                if (pageCount > 1) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { dragging = true; sliderValue = it },
                        onValueChangeFinished = { dragging = false; onJump(sliderValue.roundToInt()) },
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

/** Keeps the screen awake while reading and hides system bars when the toolbars are hidden. */
@Composable
private fun ImmersiveMode(hideSystemBars: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(hideSystemBars) {
        val w = window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        if (hideSystemBars) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
