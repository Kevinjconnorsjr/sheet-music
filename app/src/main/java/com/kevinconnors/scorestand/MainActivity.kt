package com.kevinconnors.scorestand

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.kevinconnors.scorestand.ui.AppNavigation
import com.kevinconnors.scorestand.ui.theme.ScoreStandTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreStandTheme {
                AppNavigation()
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
        val repo = (application as ScoreStandApp).repository
        lifecycleScope.launch {
            var ok = 0
            val errors = mutableListOf<String>()
            for (uri in uris) {
                try {
                    repo.import(uri); ok++
                } catch (e: Exception) {
                    errors += e.message ?: "Import failed"
                }
            }
            val msg = buildString {
                if (ok > 0) append("Added $ok score${if (ok == 1) "" else "s"} to your library")
                if (errors.isNotEmpty()) { if (ok > 0) append(". "); append(errors.first()) }
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
