package com.kevinconnors.scorestand.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinconnors.scorestand.ScoreStandApp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevinconnors.scorestand.ui.library.LibraryScreen
import com.kevinconnors.scorestand.ui.reader.ReaderScreen
import com.kevinconnors.scorestand.ui.setlist.SetlistScreen

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    // Files shared from another app (e.g. Google Drive's "Send a copy") are imported in the library.
    val app = LocalContext.current.applicationContext as ScoreStandApp
    val incoming by app.incomingFiles.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming != null && nav.currentDestination?.route != "library") nav.popBackStack("library", inclusive = false)
    }
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenScore = { nav.navigate("reader/$it") },
                onOpenSetlist = { nav.navigate("setlist/$it") },
            )
        }
        composable(
            "setlist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            SetlistScreen(
                setlistId = id,
                onBack = { nav.popBackStack() },
                onPlay = { scoreId, index -> nav.navigate("reader/$scoreId?setlist=$id&index=$index") },
            )
        }
        composable(
            "reader/{scoreId}?setlist={setlist}&index={index}",
            arguments = listOf(
                navArgument("scoreId") { type = NavType.LongType },
                navArgument("setlist") { type = NavType.LongType; defaultValue = 0L },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            val args = entry.arguments!!
            ReaderScreen(
                scoreId = args.getLong("scoreId"),
                setlistId = args.getLong("setlist"),
                setlistIndex = args.getInt("index"),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
