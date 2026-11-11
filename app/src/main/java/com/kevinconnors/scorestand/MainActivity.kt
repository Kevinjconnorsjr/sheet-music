package com.kevinconnors.scorestand

import com.kevinconnors.scorestand.ui.TitleText
import android.content.Intent
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as ScoreStandApp).settings
        setContent {
            val titleDisplay by settings.titleDisplay.collectAsState()
            CompositionLocalProvider(LocalTitleDisplay provides titleDisplay) {
                ScoreStandTheme {
                    AppNavigation()
                }
            }
        }
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
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
