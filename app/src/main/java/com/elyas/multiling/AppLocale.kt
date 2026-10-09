package com.elyas.multiling

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * The app's own UI language (Pashto / Farsi / English), independent of the
 * system language and of the keyboard's typing languages.
 *
 * Applied by wrapping the base context of every Activity AND of the input
 * method service, so the keyboard's own labels follow the same choice.
 */
object AppLocale {

    const val PREF_KEY = "app_lang"
    const val PS = "ps"
    const val FA = "fa"
    const val EN = "en"

    /** true once the user has picked a language (first-run dialog done) */
    private const val PREF_CHOSEN = "app_lang_chosen"

    fun current(context: Context): String {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        return p.getString(PREF_KEY, PS) ?: PS
    }

    fun isChosen(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_CHOSEN, false)

    fun setLanguage(context: Context, code: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_KEY, code)
            .putBoolean(PREF_CHOSEN, true)
            .apply()
    }

    /** Wrap a base context so its resources resolve in the chosen language. */
    fun wrap(base: Context, withLook: Boolean = true): Context {
        val code = current(base)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLayoutDirection(locale)
        }
        // carry the app's own light/dark choice too, so a screen built on
        // this context is already in the right mode even before AppCompat
        // applies its night override on top
        if (withLook) {
            val mode = AppLook.current(base.applicationContext ?: base)
            if (mode != AppLook.SYSTEM) {
                val night = if (mode == AppLook.DARK) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
                config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
        }
        return ContextWrapper(base.createConfigurationContext(config))
    }
}
