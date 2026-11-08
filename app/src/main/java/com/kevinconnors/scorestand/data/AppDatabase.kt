package com.kevinconnors.scorestand.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Score::class, Setlist::class, SetlistEntry::class, MarkupLayer::class, Markup::class, AudioTrack::class, AudioMarker::class, PageLink::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds markup layers + markups
        AutoMigration(from = 2, to = 3), // adds audio tracks + markers
        AutoMigration(from = 3, to = 4), // adds page links + per-score metronome settings
        AutoMigration(from = 4, to = 5), // adds per-score "trim margins"
        AutoMigration(from = 5, to = 6), // adds MusicXML transposition
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao
    abstract fun setlistDao(): SetlistDao
    abstract fun markupDao(): MarkupDao
    abstract fun audioDao(): AudioDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scorestand.db").build()
    }
}
