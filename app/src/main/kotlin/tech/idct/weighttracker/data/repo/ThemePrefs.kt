package tech.idct.weighttracker.data.repo

import android.content.Context
import android.content.res.Configuration
import tech.idct.weighttracker.domain.ThemeChoice

/**
 * A one-value mirror of the stored theme choice, readable without touching the
 * database. The activity needs the answer before its first frame, and section 12
 * asks for true black on AMOLED — a white launch flash would undo that.
 */
object ThemePrefs {

    private const val FILE = "theme"
    private const val KEY = "choice"

    fun write(context: Context, choice: ThemeChoice) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, choice.name)
            .apply()
    }

    fun read(context: Context): ThemeChoice {
        val stored = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return stored?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM
    }

    fun isDark(context: Context): Boolean = when (read(context)) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
