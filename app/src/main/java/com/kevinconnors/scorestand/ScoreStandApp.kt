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

class ScoreStandApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ScoreRepository by lazy { ScoreRepository(this, database) }
    val exporter: ScoreExporter by lazy { ScoreExporter(this, repository) }
    val backups: BackupManager by lazy { BackupManager(this, database) }
    val settings: AppSettings by lazy { AppSettings(this) }

    /** Files opened with or shared to the app, waiting for the library to import them. */
    val incomingFiles = MutableStateFlow<List<Uri>?>(null)
}
