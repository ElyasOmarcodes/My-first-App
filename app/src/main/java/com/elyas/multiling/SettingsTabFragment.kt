package com.elyas.multiling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView

/**
 * Settings tab: app language as option tiles, light / auto / dark as a
 * segmented button, then every keyboard area as a row in grouped cards.
 * Each area opens in [SettingsActivity].
 */
class SettingsTabFragment : Fragment(R.layout.frag_settings) {

    private class Area(val screen: String, val icon: Int, val tone: Ui.Tone, val title: Int, val sub: Int)

    private val sections = listOf(
        R.string.set_keyboard to listOf(
            Area("langs", R.drawable.ic_m_globe, Ui.Tone.INDIGO, R.string.pref_cat_langs, R.string.screen_langs_sum),
            Area("typing", R.drawable.ic_m_spellcheck, Ui.Tone.GREEN, R.string.pref_cat_typing, R.string.screen_typing_sum),
            Area("autotext", R.drawable.ic_m_snippet, Ui.Tone.TEAL, R.string.pref_cat_autotext, R.string.screen_autotext_sum),
            Area("control", R.drawable.ic_m_arrows, Ui.Tone.AMBER, R.string.pref_cat_control, R.string.screen_control_sum),
            Area("feedback", R.drawable.ic_m_vibration, Ui.Tone.ORANGE, R.string.pref_cat_feedback, R.string.screen_feedback_sum)
        ),
        R.string.set_look to listOf(
            Area("look", R.drawable.ic_m_keyboard, Ui.Tone.BLUE, R.string.pref_cat_look, R.string.screen_look_sum),
            Area("colors", R.drawable.ic_m_palette, Ui.Tone.PINK, R.string.pref_cat_colors, R.string.screen_colors_sum),
            Area("sizes", R.drawable.ic_m_text_fields, Ui.Tone.CYAN, R.string.pref_cat_sizes, R.string.screen_sizes_sum)
        ),
        R.string.set_data to listOf(
            Area("backup", R.drawable.ic_m_book, Ui.Tone.ROSE, R.string.pref_cat_dict, R.string.screen_backup_sum)
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        Ui.padForBars(view.findViewById(R.id.scroll), top = true, bottom = true)

        // ---- app language
        Rows.bindHead(view.findViewById(R.id.head_lang), R.drawable.ic_m_translate, Ui.Tone.BLUE,
            getString(R.string.app_lang_title), getString(R.string.app_lang_sum))
        val current = AppLocale.current(c)
        Rows.languageOptions(view.findViewById(R.id.lang_options), current) { code ->
            if (code != current) {
                AppLocale.setLanguage(c, code)
                // let the tile animate its selection before the UI reloads
                view.postDelayed({ activity?.recreate() }, 180)
            }
        }

        // ---- light / auto / dark
        Rows.bindHead(view.findViewById(R.id.head_look), R.drawable.ic_m_palette, Ui.Tone.VIOLET,
            getString(R.string.look_title), getString(R.string.look_sub))
        val group = view.findViewById<MaterialButtonToggleGroup>(R.id.look_group)
        group.check(when (AppLook.current(c)) {
            AppLook.LIGHT -> R.id.look_light
            AppLook.DARK -> R.id.look_dark
            else -> R.id.look_auto
        })
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.look_light -> AppLook.LIGHT
                R.id.look_dark -> AppLook.DARK
                else -> AppLook.SYSTEM
            }
            if (mode != AppLook.current(c)) AppLook.switchTo(requireActivity(), mode)
        }

        // ---- keyboard areas, grouped
        val host = view.findViewById<LinearLayout>(R.id.sections)
        val inflater = LayoutInflater.from(c)
        for ((label, areas) in sections) {
            val head = TextView(c)
            androidx.core.widget.TextViewCompat.setTextAppearance(head, R.style.TextAppearance_Hk_Section)
            head.text = getString(label)
            head.setPadding(Ui.dp(c, 8f), Ui.dp(c, 26f), Ui.dp(c, 8f), Ui.dp(c, 10f))
            host.addView(head)

            val card = inflater.inflate(R.layout.item_group_card, host, false) as MaterialCardView
            val rows = card.findViewById<LinearLayout>(R.id.group_rows)
            for (a in areas) {
                Rows.addNav(rows, a.icon, a.tone, getString(a.title), getString(a.sub)) {
                    startActivity(SettingsActivity.intent(c, a.screen))
                }
            }
            host.addView(card)
        }
    }
}
