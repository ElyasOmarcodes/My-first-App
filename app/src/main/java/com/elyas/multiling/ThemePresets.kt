package com.elyas.multiling

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Built-in theme presets: theme.txt files bundled in assets/themes.
 * Preset values in the theme picker start with "p_" (e.g. "p_multiling");
 * picking one applies its preference values. The plain built-in themes
 * (dark/light/black/blue/green) stay as before.
 */
object ThemePresets {

    const val DEFAULT = "p_multiling"

    /** Every preference a theme.txt may set — cleared before a preset is
     *  applied so presets always look the same, whatever was set before. */
    val VISUAL_KEYS = setOf(
        "col_custom", "col_bg",
        "col_key", "col_key_grad_on", "col_key_grad",
        "col_key_mode", "col_key_grad_style", "col_key_grad_dir",
        "col_special", "col_special_grad_on", "col_special_grad",
        "col_special_mode", "col_special_grad_style", "col_special_grad_dir",
        "col_text", "col_hint", "key_border", "hints", "preview",
        "key_height", "key_height_land", "font_scale", "hint_scale",
        "corner_radius", "key_gap", "bottom_gap", "sugg_font", "arrow_height"
    )

    fun isPreset(value: String?): Boolean = value != null && value.startsWith("p_")

    /** Apply assets/themes/<id>.txt into the default preferences. */
    fun apply(context: Context, value: String): Boolean {
        val name = value.removePrefix("p_")
        val text = try {
            context.assets.open("themes/$name.txt").bufferedReader().readText()
        } catch (_: Exception) {
            return false
        }
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        val e = p.edit()
        for (k in VISUAL_KEYS) e.remove(k)
        for (line in text.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size != 3) continue
            val (k, t, v) = parts
            if (k == "theme") continue // the picker keeps the preset id
            try {
                when (t) {
                    "b" -> e.putBoolean(k, v.toBoolean())
                    "i" -> e.putInt(k, v.toInt())
                    "s" -> e.putString(k, v)
                }
            } catch (_: Exception) {
            }
        }
        e.apply()
        return true
    }

    /** First run: make the MultiLing preset the factory default without
     *  touching an existing user's setup. */
    fun bootstrap(context: Context) {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        if (p.getBoolean("preset_init", false)) return
        p.edit().putBoolean("preset_init", true).apply()
        if (!p.contains("col_custom") && !p.contains("theme")) {
            if (apply(context, DEFAULT)) {
                p.edit().putString("theme", DEFAULT).apply()
            }
        }
    }
}
