package com.elyas.multiling

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/**
 * Applies the chosen light / dark / follow-system appearance before any
 * screen is created.
 *
 * Night mode has to go through AppCompatDelegate rather than through the
 * context wrapping [AppLocale] does for the language: AppCompat recomputes
 * the night bit from the application configuration when it builds each
 * activity's context, so a uiMode forced into a wrapped context would be
 * silently overwritten. setDefaultNightMode is the supported switch, and it
 * also recreates open activities when the choice changes.
 */
class HkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLook.apply(this)
    }
}

object AppLook {
    const val PREF_KEY = "app_theme"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"

    fun current(app: android.content.Context): String =
        PreferenceManager.getDefaultSharedPreferences(app).getString(PREF_KEY, SYSTEM) ?: SYSTEM

    fun set(app: android.content.Context, mode: String) {
        PreferenceManager.getDefaultSharedPreferences(app).edit().putString(PREF_KEY, mode).apply()
        apply(app)
    }

    fun apply(app: android.content.Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (current(app)) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
