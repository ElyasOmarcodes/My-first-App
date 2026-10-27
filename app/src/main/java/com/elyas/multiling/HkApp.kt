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
        AutoTextStore.bootstrapDefaults(this)
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

    /** The night state a screen should be in right now. */
    fun wantsNight(c: android.content.Context): Boolean = when (current(c.applicationContext)) {
        LIGHT -> false
        DARK -> true
        else -> (c.applicationContext.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Header button: flip what is showing now. */
    fun toggle(a: android.app.Activity, nightNow: Boolean) =
        switchTo(a, if (nightNow) LIGHT else DARK)

    /**
     * Change the look and make sure the screen follows. AppCompat normally
     * recreates the activity itself; because our activities sit on a
     * locale-wrapped context that path can be skipped, so if the screen did
     * not change by the next frame it is recreated here.
     */
    fun switchTo(a: android.app.Activity, mode: String) {
        set(a.applicationContext, mode)
        val want = wantsNight(a)
        a.window.decorView.post {
            if (a.isFinishing || a.isChangingConfigurations) return@post
            if (Ui.isNight(a) != want) a.recreate()
        }
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
