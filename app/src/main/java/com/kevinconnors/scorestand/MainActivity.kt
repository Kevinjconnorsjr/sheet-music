package com.kevinconnors.scorestand

import com.kevinconnors.scorestand.ui.TitleText
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.kevinconnors.scorestand.playlist.PlaybackService
import com.kevinconnors.scorestand.ui.LocalAskForNotifications
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import com.kevinconnors.scorestand.ui.LocalTitleDisplay
import com.kevinconnors.scorestand.ui.AppNavigation
import com.kevinconnors.scorestand.ui.theme.ScoreStandTheme

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var askedForNotifications = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Before the first playlist plays: allow the playback notification (Android 13+). */
    private fun askForNotifications() {
        if (askedForNotifications || Build.VERSION.SDK_INT < 33) return
        askedForNotifications = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as ScoreStandApp).settings
        setContent {
            val titleDisplay by settings.titleDisplay.collectAsState()
            CompositionLocalProvider(
                LocalTitleDisplay provides titleDisplay,
                LocalAskForNotifications provides ::askForNotifications,
            ) {
                ScoreStandTheme {
                    AppNavigation()
                }
            }
        }
        if (savedInstanceState == null) handleIncoming(intent)
        handleOpenNowPlaying(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
        handleOpenNowPlaying(intent)
    }

    /** The notification / lock-screen player was tapped: go to Now playing. */
    private fun handleOpenNowPlaying(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_NOW_PLAYING) (application as ScoreStandApp).nowPlayingRequest.value++
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller starts the PlaybackService, which gives listening playlists
        // background playback and the notification / lock-screen controls.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "com.kevinconnors.scorestand.OPEN_NOW_PLAYING"
    }

    /** Imports files opened with or shared to the app. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        // The library imports them the same way as its Import button: sheet music shared together
        // with recordings is saved as one score with its tracks, and recordings on their own are
        // attached to a score the user picks.
        (application as ScoreStandApp).incomingFiles.value = uris
    }
}
