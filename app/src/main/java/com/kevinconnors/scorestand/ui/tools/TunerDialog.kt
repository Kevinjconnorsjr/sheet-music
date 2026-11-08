package com.kevinconnors.scorestand.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.kevinconnors.scorestand.practice.Notes
import com.kevinconnors.scorestand.practice.TunerEngine
import kotlin.math.abs

private val InTune = Color(0xFF2E7D32)
private val Close = Color(0xFFF9A825)
private val Off = Color(0xFFC62828)

/** Chromatic tuner using the microphone. */
@Composable
fun TunerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted && !asked) permission.launch(Manifest.permission.RECORD_AUDIO) }

    var a4 by rememberSaveable { mutableFloatStateOf(440f) }
    var useFlats by rememberSaveable { mutableStateOf(false) }
    var pitch by remember { mutableStateOf<Float?>(null) }

    if (granted) {
        DisposableEffect(Unit) {
            val main = Handler(Looper.getMainLooper())
            val engine = TunerEngine { hz -> main.post { pitch = hz } }
            engine.start()
            onDispose { engine.stop() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tuner", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (!granted) {
                    Text(
                        "The tuner listens through the microphone. Nothing is recorded or saved.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
                    if (asked) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "If nothing happens, allow the microphone for ScoreStand in your phone's Settings → Apps.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val reading = pitch?.let { Notes.reading(it, a4, useFlats) }
                    val color = when {
                        reading == null -> MaterialTheme.colorScheme.outline
                        abs(reading.cents) <= 5 -> InTune
                        abs(reading.cents) <= 15 -> Close
                        else -> Off
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(reading?.name ?: "–", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = color)
                        Text(reading?.octave?.toString() ?: "", fontSize = 24.sp, color = color, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Text(
                        reading?.let { "%.1f Hz  ·  %+d cents".format(it.frequency, it.cents) } ?: "Play a note",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    CentsMeter(reading?.cents, color)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("♭ flat", style = MaterialTheme.typography.labelSmall)
                        Text("sharp ♯", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { a4 = (a4 - 1).coerceAtLeast(400f) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text("A4 = ${a4.toInt()} Hz", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { a4 = (a4 + 1).coerceAtMost(480f) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        Spacer(Modifier.padding(4.dp))
                        FilterChip(selected = useFlats, onClick = { useFlats = !useFlats }, label = { Text("Show flats") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun CentsMeter(cents: Int?, color: Color) {
    val tick = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val mid = size.width / 2
        // Scale from −50 to +50 cents with a green "in tune" zone.
        drawRect(InTune.copy(alpha = 0.15f), Offset(mid - size.width * 0.05f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.1f, size.height))
        for (c in -50..50 step 10) {
            val x = mid + c / 50f * (size.width / 2 - 8)
            val h = if (c == 0) size.height else size.height * 0.4f
            drawLine(tick, Offset(x, size.height - h), Offset(x, size.height), strokeWidth = if (c == 0) 3f else 2f)
        }
        if (cents != null) {
            val x = mid + cents.coerceIn(-50, 50) / 50f * (size.width / 2 - 8)
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 8f, cap = StrokeCap.Round)
        }
    }
}
