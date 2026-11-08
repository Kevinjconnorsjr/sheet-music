package com.kevinconnors.scorestand.ui.reader

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.filled.Headphones
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
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
import com.kevinconnors.scorestand.ui.reader.audio.AudioPanel
import com.kevinconnors.scorestand.data.PageLink
import com.kevinconnors.scorestand.ui.ConfirmDialog
import com.kevinconnors.scorestand.ui.reader.markup.LinkLabelDialog
import com.kevinconnors.scorestand.ui.reader.markup.MarkupOverlay
import com.kevinconnors.scorestand.ui.reader.markup.MarkupSession
import com.kevinconnors.scorestand.ui.tools.MetronomeIcon
import com.kevinconnors.scorestand.ui.tools.MetronomePanel
import com.kevinconnors.scorestand.ui.tools.TunerDialog
import com.kevinconnors.scorestand.ui.reader.markup.MarkupToolbar
import com.kevinconnors.scorestand.ui.reader.markup.TextNoteDialog
import com.kevinconnors.scorestand.ui.reader.markup.Tool
import com.kevinconnors.scorestand.ui.reader.markup.textMarkup
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
    var annotating by rememberSaveable { mutableStateOf(false) }
    val tools = vm.tools
    val markup = loaded.markup
    // Normal swiping/scrolling is off while a drawing tool is in use.
    val drawing = annotating && tools.tool != Tool.PAN
    var pendingText by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    val audio = vm.audio
    var showAudio by rememberSaveable { mutableStateOf(false) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { audio.importTrack(it); showAudio = true }
    }
    val context = LocalContext.current
    var showMetronome by rememberSaveable { mutableStateOf(false) }
    var showTuner by rememberSaveable { mutableStateOf(false) }
    // Jump links: where a followed link came from (for the "back" chip), a destination being
    // labelled, and a link the user asked to delete.
    var returnTo by remember { mutableStateOf<Int?>(null) }
    var newLinkDest by remember { mutableStateOf<Triple<MarkupSession.LinkStart, Int, Float>?>(null) }
    var deletingLink by remember { mutableStateOf<PageLink?>(null) }
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

    // Markers linked to pages turn the music as the recording plays.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        audio.pageRequests.collect { page -> if (page < pageCount && page != c.currentPage) c.jumpTo(page) }
    }
    LaunchedEffect(Unit) {
        audio.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    fun goNext() = scope.launch { if (controller?.next() == false) vm.nextPiece() }
    fun goPrevious() = scope.launch { if (controller?.previous() == false) vm.previousPiece() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BackHandler { if (annotating) annotating = false else onBack() }

    Box(
        Modifier
            .fillMaxSize()
            // Bluetooth page-turn pedals present themselves as keyboards.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.PageDown, Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter, Key.NumPadEnter -> { goNext(); true }
                    Key.PageUp, Key.DirectionLeft, Key.DirectionUp -> { goPrevious(); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { audio.togglePlay(); true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(drawing) {
                if (drawing) return@pointerInput
                detectTapGestures { offset ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> goPrevious()
                        offset.x > third * 2 -> goNext()
                        else -> if (!annotating) chromeVisible = !chromeVisible
                    }
                }
            },
    ) {
        val overlay: @Composable (Int) -> Unit = { page ->
            MarkupOverlay(
                session = markup, settings = tools, page = page, editing = annotating,
                onRequestText = { p, x, y -> pendingText = Triple(p, x, y) },
                onLinkSpot = { p, x, y ->
                    val from = markup.pendingLinkFrom
                    if (from == null) markup.pendingLinkFrom = MarkupSession.LinkStart(p, x, y)
                    else newLinkDest = Triple(from, p, y)
                },
                onLinkTapped = { link ->
                    if (annotating) deletingLink = link
                    else scope.launch {
                        returnTo = controller?.currentPage
                        controller?.jumpTo(link.toPage, link.toY)
                    }
                },
            )
        }
        CompositionLocalProvider(LocalPageOverlay provides overlay) {
          key(score.viewMode) {
            when (score.viewMode) {
                ViewMode.SINGLE -> SinglePageView(renderer, lastKnownPage, register, userScrollEnabled = !drawing)
                ViewMode.TWO_PAGE -> TwoPageView(renderer, lastKnownPage, register, userScrollEnabled = !drawing)
                ViewMode.HALF_PAGE -> HalfPageView(renderer, lastKnownPage, register, wholePagesOnly = annotating)
                ViewMode.VERTICAL -> VerticalScrollView(
                    renderer = renderer,
                    initialPage = lastKnownPage,
                    register = register,
                    widthFraction = widthFraction,
                    userScrollEnabled = !drawing,
                    autoScroll = autoScroll && !annotating,
                    autoScrollDpPerSecond = autoSpeed,
                    onAutoScrollReachedEnd = { autoScroll = false },
                )
            }
          }
        }

        AnimatedVisibility(
            visible = chromeVisible || annotating,
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
                annotating = annotating,
                onAnnotate = { annotating = true; autoScroll = false },
                audioOpen = showAudio,
                onToggleAudio = { showAudio = !showAudio },
                onMetronome = { showMetronome = true },
                onTuner = { showTuner = true },
                exporting = vm.exporting,
                onExport = {
                    vm.exportPdf(
                        onReady = { context.startActivity(it) },
                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                    )
                },
                onMode = { mode ->
                    autoScroll = false
                    vm.setViewMode(mode, controller?.currentPage ?: lastKnownPage)
                },
            )
        }

        AnimatedVisibility(
            visible = annotating,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MarkupToolbar(
                session = markup,
                settings = tools,
                currentPage = controller?.currentPage ?: lastKnownPage,
                pageCount = pageCount,
                onPreviousPage = { goPrevious() },
                onNextPage = { goNext() },
                onDone = { annotating = false; markup.pendingLinkFrom = null },
            )
        }

        // After following a jump link, offer a way back.
        returnTo?.let { back ->
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (chromeVisible || annotating) 64.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.AssistChip(
                    onClick = { scope.launch { controller?.jumpTo(back) }; returnTo = null },
                    label = { Text("Back to page ${back + 1}") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardReturn, null) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(18.dp).pointerInput(Unit) { detectTapGestures { returnTo = null } })
                    },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            AnimatedVisibility(
                visible = showMetronome && !annotating,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                MetronomePanel(vm.metronome, onClose = { showMetronome = false })
            }
            // The audio panel stays up while the toolbars are hidden, so you can play along full-screen.
            AnimatedVisibility(
                visible = showAudio && !annotating,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                AudioPanel(
                    audio = audio,
                    currentPage = controller?.currentPage ?: lastKnownPage,
                    onAddTrack = { audioPicker.launch(arrayOf("audio/*")) },
                    onClose = { showAudio = false },
                )
            }
            AnimatedVisibility(
                visible = chromeVisible && !annotating,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
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

    if (showTuner) TunerDialog(onDismiss = { showTuner = false })
    newLinkDest?.let { (from, toPage, toY) ->
        LinkLabelDialog(
            toPage = toPage,
            onDismiss = { newLinkDest = null; markup.pendingLinkFrom = null },
            onCreate = { label -> markup.addLink(from, toPage, toY, label); newLinkDest = null },
        )
    }
    deletingLink?.let { link ->
        ConfirmDialog(
            title = "Remove \"${link.label}\" link?", message = "The jump to page ${link.toPage + 1} will be removed.", confirmLabel = "Remove",
            onDismiss = { deletingLink = null },
            onConfirm = { markup.deleteLink(link); deletingLink = null },
        )
    }

    pendingText?.let { (page, x, y) ->
        TextNoteDialog(onDismiss = { pendingText = null }) { text ->
            markup.add(textMarkup(markup, tools, page, x, y, text))
            pendingText = null
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
    annotating: Boolean,
    onAnnotate: () -> Unit,
    audioOpen: Boolean,
    onToggleAudio: () -> Unit,
    onMetronome: () -> Unit,
    onTuner: () -> Unit,
    exporting: Boolean,
    onExport: () -> Unit,
    onMode: (ViewMode) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
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
            if (!annotating) {
                IconButton(onClick = onToggleAudio) {
                    Icon(if (audioOpen) Icons.Filled.Headphones else Icons.Outlined.Headphones, if (audioOpen) "Hide audio" else "Audio tracks")
                }
                IconButton(onClick = onAnnotate) { Icon(Icons.Outlined.Draw, "Mark up") }
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
            if (!annotating) {
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MoreVert, "More")
                    }
                    DropdownMenu(moreMenu, { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("Metronome") }, leadingIcon = { Icon(MetronomeIcon, null) }, onClick = { moreMenu = false; onMetronome() })
                        DropdownMenuItem(text = { Text("Tuner") }, leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) }, onClick = { moreMenu = false; onTuner() })
                        DropdownMenuItem(
                            text = { Text("Share as PDF (with markings)") }, enabled = !exporting,
                            leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { moreMenu = false; onExport() },
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
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
