package com.kevinconnors.scorestand

import android.app.Application
import com.kevinconnors.scorestand.data.AppDatabase
import com.kevinconnors.scorestand.data.BackupManager
import com.kevinconnors.scorestand.data.ScoreExporter
import com.kevinconnors.scorestand.data.ScoreRepository

class ScoreStandApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ScoreRepository by lazy { ScoreRepository(this, database) }
    val exporter: ScoreExporter by lazy { ScoreExporter(this, repository) }
    val backups: BackupManager by lazy { BackupManager(this, database) }
}
