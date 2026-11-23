package com.kevinconnors.scorestand

import com.kevinconnors.scorestand.ui.TitleText
import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import com.kevinconnors.scorestand.data.AppDatabase
import com.kevinconnors.scorestand.data.AppSettings
import com.kevinconnors.scorestand.data.BackupManager
import com.kevinconnors.scorestand.data.ScoreExporter
import com.kevinconnors.scorestand.data.ScoreRepository
import com.kevinconnors.scorestand.playlist.PlaylistPlayer

class ScoreStandApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ScoreRepository by lazy { ScoreRepository(this, database) }
    val exporter: ScoreExporter by lazy { ScoreExporter(this, repository) }
    val backups: BackupManager by lazy { BackupManager(this, database) }
    val settings: AppSettings by lazy { AppSettings(this) }

    /** Listening-playlist playback, kept for the app's lifetime so it carries on in the background. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val playlistPlayer: PlaylistPlayer by lazy { PlaylistPlayer(this, repository) }

    /** Bumped when the notification / lock-screen player is tapped: show Now playing. */
    val nowPlayingRequest = MutableStateFlow(0)

    /** Files opened with or shared to the app, waiting for the library to import them. */
    val incomingFiles = MutableStateFlow<List<Uri>?>(null)
}
