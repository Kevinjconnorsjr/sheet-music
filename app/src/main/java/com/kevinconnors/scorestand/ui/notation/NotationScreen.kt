package com.kevinconnors.scorestand.ui.notation

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kevinconnors.scorestand.notation.MidiWriter
import com.kevinconnors.scorestand.notation.PlaybackOptions
import com.kevinconnors.scorestand.notation.TempoMap
import com.kevinconnors.scorestand.ui.reader.ReaderViewModel
import com.kevinconnors.scorestand.ui.reader.audio.AudioPanel
import com.kevinconnors.scorestand.ui.tools.KeyboardPanel
import com.kevinconnors.scorestand.ui.tools.MetronomeIcon
import com.kevinconnors.scorestand.ui.tools.MetronomePanel
import com.kevinconnors.scorestand.ui.tools.TunerDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Lets the notation page (assets/notation/index.html) read the score and report back. */
private class NotationBridge(
    private val xml: String,
    private val onLoaded: (Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun getScoreXml(): String = xml
    @JavascriptInterface fun onLoaded(measures: Int) { main.post { onLoaded.invoke(measures) } }
    @JavascriptInterface fun onError(message: String) { main.post { onError.invoke(message) } }
}

private fun transposeLabel(n: Int) = when {
    n == 0 -> "Original key"
    n > 0 -> "Up $n semitone${if (n == 1) "" else "s"}"
    else -> "Down ${-n} semitone${if (n == -1) "" else "s"}"
}

/**
 * MusicXML scores: live notation (pinch to zoom) that can be transposed to any key and played
 * back as MIDI with a following cursor, adjustable tempo and selectable parts.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NotationContent(vm: ReaderViewModel, doc: ReaderViewModel.NotationDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val parsed = doc.parsed
    val player = vm.notationPlayer
    val transpose = doc.score.transposeSemitones

    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var appliedTranspose by remember { mutableIntStateOf(transpose) }
    var rekeying by remember { mutableStateOf(false) }

    var tempoPercent by rememberSaveable { mutableIntStateOf(100) }
    var muted by remember { mutableStateOf(emptySet<Int>()) }
    var sound by rememberSaveable { mutableStateOf<String?>(null) } // null = instruments from the score
    var position by remember { mutableDoubleStateOf(0.0) }

    var showPlayback by rememberSaveable { mutableStateOf(true) }
    var showAudio by rememberSaveable { mutableStateOf(false) }
    var showMetronome by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var showTuner by rememberSaveable { mutableStateOf(false) }

    val options = PlaybackOptions(transpose, tempoPercent, muted, sound?.let { MidiWriter.sounds[it] })
    val currentOptions by rememberUpdatedState(options)

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; vm.notationPlayer.stop() }
    }
    if (showKeyboard) DisposableEffect(Unit) { vm.piano.start(); onDispose { vm.piano.stop() } }
    BackHandler(onBack = onBack)

    // Changing key, tempo, parts or sound mid-playback carries on from the same spot.
    LaunchedEffect(options) {
        if (player.isPlaying) player.play(parsed, options, player.position())
    }
    // Re-draw the notation in the new key. Redrawing a long score takes a few seconds, so wait
    // for the taps to stop and redraw once.
    LaunchedEffect(transpose, ready) {
        if (ready && transpose != appliedTranspose) {
            delay(700)
            rekeying = true
            appliedTranspose = transpose
            webView?.evaluateJavascript("setTranspose($transpose)") { rekeying = false }
        }
    }
    // Follow playback with the cursor (the page scrolls along).
    LaunchedEffect(player.isPlaying, ready) {
        while (player.isPlaying && ready) {
            position = player.position()
            webView?.evaluateJavascript("seekCursor(${position / 4.0})", null)
            delay(120)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { vm.audio.importTracks(uris); showAudio = true }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            NotationTopBar(
                vm = vm, title = doc.score.title, subtitle = doc.score.composer, onBack = onBack,
                playbackOpen = showPlayback, onTogglePlayback = { showPlayback = !showPlayback },
                audioOpen = showAudio, onToggleAudio = { showAudio = !showAudio },
                onMetronome = { showMetronome = true }, onKeyboard = { showKeyboard = true }, onTuner = { showTuner = true },
                onShareMidi = {
                    vm.shareMidi(onReady = { context.startActivity(it) }, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(AndroidColor.WHITE)
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = true // pinch to zoom
                            settings.displayZoomControls = false
                            addJavascriptInterface(
                                NotationBridge(doc.xml, onLoaded = { ready = true }, onError = { renderError = it }),
                                "Android",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript("load($transpose, 1.0)", null)
                                }
                            }
                            loadUrl("file:///android_asset/notation/index.html")
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
                if (!ready && renderError == null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Engraving the score…", Modifier.padding(top = 12.dp), color = Color.Gray)
                    }
                }
                if (rekeying) {
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 4.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Changing key…")
                        }
                    }
                }
                renderError?.let {
                    Text("Couldn't draw this score: $it", Modifier.align(Alignment.Center).padding(32.dp), textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                AnimatedVisibility(showAudio) {
                    AudioPanel(vm.audio, currentPage = 0, onAddTrack = { audioPicker.launch(arrayOf("audio/*")) }, onClose = { showAudio = false })
                }
                AnimatedVisibility(showMetronome) { MetronomePanel(vm.metronome, onClose = { showMetronome = false }) }
                AnimatedVisibility(showKeyboard) { KeyboardPanel(vm.piano, onClose = { showKeyboard = false }) }
                AnimatedVisibility(showPlayback) {
                    PlaybackPanel(
                        parsedBars = parsed.measureStarts,
                        partNames = parsed.parts.map { it.name },
                        isPlaying = player.isPlaying,
                        position = if (player.isPlaying) position else player.position(),
                        tempoPercent = tempoPercent,
                        baseBpm = TempoMap(parsed.tempos, 100).bpmAt(position),
                        transpose = transpose,
                        muted = muted,
                        sound = sound,
                        onPlayPause = {
                            if (player.isPlaying) player.pause() else player.play(parsed, currentOptions, player.position())
                        },
                        onStop = {
                            player.stop(); position = 0.0
                            webView?.evaluateJavascript("hideCursor()", null)
                        },
                        onBar = { bar ->
                            val q = parsed.measureStarts[bar]
                            position = q
                            if (player.isPlaying) player.play(parsed, currentOptions, q) else player.seek(q)
                            if (ready) webView?.evaluateJavascript("seekCursor(${q / 4.0})", null)
                        },
                        onTempo = { tempoPercent = it.coerceIn(25, 200) },
                        onTranspose = { vm.setTranspose(it) },
                        onToggleMute = { part -> muted = if (part in muted) muted - part else muted + part },
                        onSolo = { part -> muted = parsed.parts.indices.toSet() - part },
                        onAllParts = { muted = emptySet() },
                        onSound = { sound = it },
                    )
                }
            }
        }
    }
    if (showTuner) TunerDialog(onDismiss = { showTuner = false })
}

@Composable
private fun NotationTopBar(
    vm: ReaderViewModel,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    playbackOpen: Boolean,
    onTogglePlayback: () -> Unit,
    audioOpen: Boolean,
    onToggleAudio: () -> Unit,
    onMetronome: () -> Unit,
    onKeyboard: () -> Unit,
    onTuner: () -> Unit,
    onShareMidi: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = if (vm.inSetlist) "${vm.setlistName ?: "Setlist"} · ${vm.setlistIndex + 1} of ${vm.setlistItems.size}" else subtitle
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (vm.inSetlist) {
                IconButton(enabled = vm.hasPreviousPiece(), onClick = vm::previousPiece) { Icon(Icons.Outlined.SkipPrevious, "Previous piece") }
                IconButton(enabled = vm.hasNextPiece(), onClick = vm::nextPiece) { Icon(Icons.Outlined.SkipNext, "Next piece") }
            }
            IconButton(onClick = onToggleAudio) {
                Icon(if (audioOpen) Icons.Filled.Headphones else Icons.Outlined.Headphones, if (audioOpen) "Hide audio" else "Audio tracks")
            }
            FilledTonalIconToggleButton(checked = playbackOpen, onCheckedChange = { onTogglePlayback() }) {
                Icon(Icons.Outlined.Tune, "Playback and key")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Metronome") }, leadingIcon = { Icon(MetronomeIcon, null) }, onClick = { menu = false; onMetronome() })
                    DropdownMenuItem(text = { Text("Keyboard") }, leadingIcon = { Icon(Icons.Outlined.Piano, null) }, onClick = { menu = false; onKeyboard() })
                    DropdownMenuItem(text = { Text("Tuner") }, leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) }, onClick = { menu = false; onTuner() })
                    DropdownMenuItem(text = { Text("Share MIDI file") }, leadingIcon = { Icon(Icons.Outlined.LibraryMusic, null) }, onClick = { menu = false; onShareMidi() })
                }
            }
        }
    }
}

@Composable
private fun PlaybackPanel(
    parsedBars: List<Double>,
    partNames: List<String>,
    isPlaying: Boolean,
    position: Double,
    tempoPercent: Int,
    baseBpm: Double,
    transpose: Int,
    muted: Set<Int>,
    sound: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onBar: (Int) -> Unit,
    onTempo: (Int) -> Unit,
    onTranspose: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    onSolo: (Int) -> Unit,
    onAllParts: () -> Unit,
    onSound: (String?) -> Unit,
) {
    val bar = parsedBars.indexOfLast { it <= position + 1e-6 }.coerceAtLeast(0)
    var dragBar by remember { mutableStateOf<Float?>(null) }
    var soundMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                    Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (isPlaying) "Pause" else "Play")
                }
                IconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, "Stop") }
                val shown = (dragBar?.roundToInt() ?: bar) + 1
                Text("Bar $shown / ${parsedBars.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(92.dp))
                if (parsedBars.size > 1) {
                    Slider(
                        value = dragBar ?: bar.toFloat(),
                        onValueChange = { dragBar = it },
                        onValueChangeFinished = { dragBar?.let { onBar(it.roundToInt()) }; dragBar = null },
                        valueRange = 0f..(parsedBars.size - 1).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SettingLine("Tempo", "$tempoPercent% · ♩ = ${(baseBpm * tempoPercent / 100).roundToInt()}", if (tempoPercent != 100) ({ onTempo(100) }) else null) {
                IconButton(onClick = { onTempo(tempoPercent - 5) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Slider(tempoPercent.toFloat(), { onTempo(it.roundToInt()) }, valueRange = 25f..200f, modifier = Modifier.weight(1f))
                IconButton(onClick = { onTempo(tempoPercent + 5) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            SettingLine("Key", transposeLabel(transpose), if (transpose != 0) ({ onTranspose(0) }) else null) {
                IconButton(enabled = transpose > -12, onClick = { onTranspose(transpose - 1) }) { Text("♭", style = MaterialTheme.typography.titleLarge) }
                Text(if (transpose > 0) "+$transpose" else "$transpose", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = transpose < 12, onClick = { onTranspose(transpose + 1) }) { Text("♯", style = MaterialTheme.typography.titleLarge) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    TextButton(onClick = { soundMenu = true }) { Text(sound ?: "Score's instruments"); Icon(Icons.Outlined.ArrowDropDown, null) }
                    DropdownMenu(soundMenu, { soundMenu = false }) {
                        DropdownMenuItem(text = { Text("Score's instruments") }, trailingIcon = { if (sound == null) Icon(Icons.Outlined.Check, null) }, onClick = { soundMenu = false; onSound(null) })
                        MidiWriter.sounds.keys.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, trailingIcon = { if (sound == name) Icon(Icons.Outlined.Check, null) }, onClick = { soundMenu = false; onSound(name) })
                        }
                    }
                }
                if (partNames.size > 1) {
                    partNames.forEachIndexed { i, name ->
                        FilterChip(selected = i !in muted, onClick = { onToggleMute(i) }, label = { Text(name, maxLines = 1) })
                    }
                    TextButton(onClick = onAllParts) { Text("All parts") }
                    var soloMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { soloMenu = true }) { Text("Solo…") }
                        DropdownMenu(soloMenu, { soloMenu = false }) {
                            partNames.forEachIndexed { i, name ->
                                DropdownMenuItem(text = { Text("Only $name") }, onClick = { soloMenu = false; onSolo(i) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String, onReset: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(104.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        content()
        Box(Modifier.width(64.dp)) { if (onReset != null) TextButton(onClick = onReset) { Text("Reset") } }
    }
}
