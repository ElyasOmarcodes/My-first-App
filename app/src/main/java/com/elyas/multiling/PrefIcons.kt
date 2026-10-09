package com.elyas.multiling

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup

/**
 * Gives every settings row an icon that says what it does, on a soft tile of
 * its own colour — the same tonal style as the main settings tab, so moving
 * into a sub-screen does not drop into a plain list.
 *
 * Icons are matched by preference key, so the XML files stay free of them
 * and one table keeps the visual language consistent.
 */
object PrefIcons {

    /** key → icon, tone. Colours follow meaning: size cyan/blue, text teal,
     *  learning violet, sound teal, danger rose, files amber. */
    private val MAP: Map<String, Pair<Int, Ui.Tone>> = mapOf(
        // look
        "theme" to (R.drawable.ic_m_palette to Ui.Tone.VIOLET),
        "key_border" to (R.drawable.ic_m_border to Ui.Tone.INDIGO),
        "hints" to (R.drawable.ic_m_label to Ui.Tone.BLUE),
        "key_font_vazir" to (R.drawable.ic_m_font to Ui.Tone.PINK),
        "preview" to (R.drawable.ic_m_zoom to Ui.Tone.TEAL),
        // sizes
        "key_height" to (R.drawable.ic_m_height to Ui.Tone.VIOLET),
        "key_height_land" to (R.drawable.ic_m_landscape to Ui.Tone.INDIGO),
        "key_gap" to (R.drawable.ic_m_grid to Ui.Tone.BLUE),
        "corner_radius" to (R.drawable.ic_m_corner to Ui.Tone.CYAN),
        "font_scale" to (R.drawable.ic_m_format_size to Ui.Tone.TEAL),
        "hint_scale" to (R.drawable.ic_m_text_fields to Ui.Tone.GREEN),
        "sugg_font" to (R.drawable.ic_m_short_text to Ui.Tone.AMBER),
        "nav_gap_auto" to (R.drawable.ic_m_bottom_bar to Ui.Tone.ORANGE),
        "bottom_gap" to (R.drawable.ic_m_align_bottom to Ui.Tone.ROSE),
        // typing
        "suggestions" to (R.drawable.ic_m_bulb to Ui.Tone.AMBER),
        "autocorrect" to (R.drawable.ic_m_spellcheck to Ui.Tone.GREEN),
        "seed_dict" to (R.drawable.ic_m_book to Ui.Tone.BLUE),
        "bigrams" to (R.drawable.ic_m_next to Ui.Tone.INDIGO),
        "learn_words" to (R.drawable.ic_m_school to Ui.Tone.VIOLET),
        "learned_words" to (R.drawable.ic_m_list to Ui.Tone.TEAL),
        "double_space" to (R.drawable.ic_m_space to Ui.Tone.CYAN),
        "autocaps" to (R.drawable.ic_m_title to Ui.Tone.PINK),
        "longpress" to (R.drawable.ic_m_touch to Ui.Tone.ORANGE),
        "clip_chip_repeat" to (R.drawable.ic_m_paste to Ui.Tone.ROSE),
        // languages
        "languages" to (R.drawable.ic_m_translate to Ui.Tone.BLUE),
        "space_swipe" to (R.drawable.ic_m_swap to Ui.Tone.TEAL),
        // control
        "arrows" to (R.drawable.ic_m_arrows to Ui.Tone.INDIGO),
        "arrow_height" to (R.drawable.ic_m_height to Ui.Tone.VIOLET),
        // feedback
        "vibrate" to (R.drawable.ic_m_vibration to Ui.Tone.VIOLET),
        "vibrate_ms" to (R.drawable.ic_m_timer to Ui.Tone.INDIGO),
        "sound" to (R.drawable.ic_m_volume to Ui.Tone.TEAL),
        "sound_type" to (R.drawable.ic_m_music to Ui.Tone.CYAN),
        "sound_vol" to (R.drawable.ic_m_volume to Ui.Tone.BLUE),
        // shortcuts
        "autotext_on" to (R.drawable.ic_m_snippet to Ui.Tone.VIOLET),
        "autotext_manage" to (R.drawable.ic_m_edit to Ui.Tone.BLUE),
        "autotext_import" to (R.drawable.ic_m_download to Ui.Tone.TEAL),
        "autotext_export" to (R.drawable.ic_m_upload to Ui.Tone.AMBER),
        // backup
        "settings_export" to (R.drawable.ic_m_upload to Ui.Tone.BLUE),
        "settings_import" to (R.drawable.ic_m_download to Ui.Tone.TEAL),
        "settings_reset" to (R.drawable.ic_m_restore to Ui.Tone.ROSE),
        "dict_import" to (R.drawable.ic_m_download to Ui.Tone.GREEN),
        "dict_export" to (R.drawable.ic_m_upload to Ui.Tone.AMBER),
        "clean_typos" to (R.drawable.ic_m_magic to Ui.Tone.ORANGE),
        "clear_learned" to (R.drawable.ic_m_sweep to Ui.Tone.ROSE),
        // colours
        "col_custom" to (R.drawable.ic_m_palette to Ui.Tone.PINK),
        "col_bg" to (R.drawable.ic_m_paint to Ui.Tone.VIOLET),
        "screen_col_keys" to (R.drawable.ic_m_keyboard to Ui.Tone.BLUE),
        "screen_col_special" to (R.drawable.ic_m_star to Ui.Tone.AMBER),
        "screen_col_text" to (R.drawable.ic_m_text_fields to Ui.Tone.TEAL),
        "theme_export" to (R.drawable.ic_m_upload to Ui.Tone.AMBER),
        "theme_import" to (R.drawable.ic_m_download to Ui.Tone.GREEN),
        "col_text" to (R.drawable.ic_m_title to Ui.Tone.BLUE),
        "col_hint" to (R.drawable.ic_m_label to Ui.Tone.TEAL)
    )

    /** Colour-group rows share keys across the "keys" and "special" pages. */
    private fun lookup(key: String): Pair<Int, Ui.Tone>? {
        MAP[key]?.let { return it }
        return when {
            key.endsWith("_mode") -> R.drawable.ic_m_gradient to Ui.Tone.VIOLET
            key.endsWith("_grad_style") -> R.drawable.ic_m_gradient to Ui.Tone.TEAL
            key.endsWith("_grad_dir") -> R.drawable.ic_m_direction to Ui.Tone.AMBER
            key.endsWith("_grad") -> R.drawable.ic_m_contrast to Ui.Tone.PINK
            key == "col_key" || key == "col_special" -> R.drawable.ic_m_circle to Ui.Tone.BLUE
            else -> null
        }
    }

    /** Walk the screen and give each row its tile. */
    fun apply(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (p is PreferenceGroup) {
                apply(p)
                continue
            }
            val k = p.key ?: continue
            val (icon, tone) = lookup(k) ?: continue
            tile(p.context, icon, tone)?.let { p.icon = it }
        }
    }

    /** A 40dp rounded tile in the tone's container colour with the icon
     *  centred in its accent colour. */
    fun tile(c: Context, icon: Int, tone: Ui.Tone): Drawable? {
        val glyph = AppCompatResources.getDrawable(c, icon)?.mutate() ?: return null
        DrawableCompat.setTint(glyph, Ui.color(c, tone.fg))
        val size = Ui.dp(c, 36f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Ui.dp(c, 13f).toFloat()
            setColor(Ui.color(c, tone.bg))
            setSize(size, size)
        }
        val pad = Ui.dp(c, 7.5f)
        val inset = InsetDrawable(glyph, pad)
        return LayerDrawable(arrayOf(bg, inset)).apply {
            setBounds(0, 0, size, size)
        }
    }
}
