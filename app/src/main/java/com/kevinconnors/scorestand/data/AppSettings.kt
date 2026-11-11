package com.kevinconnors.scorestand.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How names that don't fit on one line are shown. */
enum class TitleDisplay(val label: String) {
    /** The whole name, over as many lines as needed. */
    WRAP("Wrap to show the whole name"),
    /** One line: the start, "…", then the end. */
    MIDDLE("Shorten in the middle"),
}

/** Small app-wide preferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _titleDisplay = MutableStateFlow(
        TitleDisplay.entries.firstOrNull { it.name == prefs.getString(KEY_TITLES, null) } ?: TitleDisplay.WRAP
    )
    val titleDisplay: StateFlow<TitleDisplay> = _titleDisplay

    fun setTitleDisplay(value: TitleDisplay) {
        _titleDisplay.value = value
        prefs.edit().putString(KEY_TITLES, value.name).apply()
    }

    private companion object {
        const val KEY_TITLES = "title_display"
    }
}
