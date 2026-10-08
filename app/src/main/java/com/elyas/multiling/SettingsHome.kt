package com.elyas.multiling

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * The settings landing page.
 *
 * Deliberately NOT a PreferenceFragmentCompat. The stock preference list can
 * show a title, a summary and a flat icon, and nothing else — which is why
 * this screen used to look like system settings rather than like this app.
 * Built by hand instead, each area gets its own accent colour, so the page
 * is navigable by colour before it is read.
 *
 * The sub-screens stay preference fragments; only this index is hand-built.
 */
class SettingsHome : Fragment() {

    /** One row of the index: icon, accent, title, summary, and where it goes. */
    private class Entry(
        val icon: Int,
        val accent: Int,
        val title: Int,
        val summary: Int,
        val action: (SettingsHome) -> Unit
    )

    private fun entries(): List<List<Entry>> = listOf(
        // ---- group 1: what language anything is in
        listOf(
            Entry(R.drawable.ic_cat_applang, R.color.c_violet,
                R.string.app_lang_title, R.string.app_lang_sum) { it.pickAppLanguage() },
            Entry(R.drawable.ic_cat_lang, R.color.c_indigo,
                R.string.pref_cat_langs, R.string.screen_langs_sum) { it.open("langs", R.string.pref_cat_langs) }
        ),
        // ---- group 2: how it looks
        listOf(
            Entry(R.drawable.ic_cat_look, R.color.c_sky,
                R.string.pref_cat_look, R.string.screen_look_sum) { it.open("look", R.string.pref_cat_look) },
            Entry(R.drawable.ic_cat_look, R.color.c_pink,
                R.string.pref_cat_colors, R.string.screen_colors_sum) { it.open("colors", R.string.pref_cat_colors) },
            Entry(R.drawable.ic_cat_size, R.color.c_cyan,
                R.string.pref_cat_sizes, R.string.screen_sizes_sum) { it.open("sizes", R.string.pref_cat_sizes) }
        ),
        // ---- group 3: how it behaves
        listOf(
            Entry(R.drawable.ic_cat_typing, R.color.c_mint,
                R.string.pref_cat_typing, R.string.screen_typing_sum) { it.open("typing", R.string.pref_cat_typing) },
            Entry(R.drawable.ic_cat_autotext, R.color.c_lime,
                R.string.pref_cat_autotext, R.string.screen_autotext_sum) { it.open("autotext", R.string.pref_cat_autotext) },
            Entry(R.drawable.ic_cat_control, R.color.c_amber,
                R.string.pref_cat_control, R.string.screen_control_sum) { it.open("control", R.string.pref_cat_control) },
            Entry(R.drawable.ic_cat_feedback, R.color.c_orange,
                R.string.pref_cat_feedback, R.string.screen_feedback_sum) { it.open("feedback", R.string.pref_cat_feedback) }
        ),
        // ---- group 4: data and the app itself
        listOf(
            Entry(R.drawable.ic_cat_backup, R.color.c_teal,
                R.string.pref_cat_dict, R.string.screen_backup_sum) { it.open("backup", R.string.pref_cat_dict) },
            Entry(R.drawable.ic_key_info, R.color.c_rose,
                R.string.about_title, R.string.screen_about_sum) { it.openAbout() }
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?
    ): View {
        val c = requireContext()
        val scroll = ScrollView(c)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        val g = c.resources.getDimensionPixelSize(R.dimen.gutter)
        column.setPadding(g, 0, g, Ui.dp(c, 28f))
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(heroCard(c))

        val cards = ArrayList<View>()
        for (group in entries()) {
            val card = Ui.card(c)
            for ((i, e) in group.withIndex()) {
                if (i > 0) Ui.addDivider(card)
                card.addView(
                    Ui.row(c, e.icon, Ui.color(c, e.accent),
                        getString(e.title), getString(e.summary)) { e.action(this) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                )
            }
            Ui.addCard(column, card)
            cards.add(card)
        }

        // staggered entrance, so the page assembles instead of appearing
        for ((i, v) in cards.withIndex()) {
            v.alpha = 0f
            v.translationY = Ui.dp(c, 18f).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(40L + i * 55L).setDuration(340).start()
        }
        return scroll
    }

    /**
     * Header card: the keyboard's own identity, so settings opens with the
     * thing being configured rather than straight into a list.
     */
    private fun heroCard(c: Context): View {
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f))

        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(
            Ui.badge(c, R.drawable.ic_key_settings, Ui.color(c, R.color.brand_2)),
            LinearLayout.LayoutParams(Ui.dp(c, 48f), Ui.dp(c, 48f))
                .also { it.marginEnd = Ui.dp(c, 14f) })

        val texts = LinearLayout(c)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(c)
        t.text = getString(R.string.settings_title)
        t.textSize = 19f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(Ui.color(c, R.color.text_primary))
        texts.addView(t)
        val s = TextView(c)
        s.text = getString(R.string.app_name)
        s.textSize = 13f
        s.setTextColor(Ui.color(c, R.color.text_tertiary))
        texts.addView(s)
        top.addView(texts, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        return card
    }

    // ------------------------------------------------------------ actions
    private fun open(screen: String, titleRes: Int) {
        (activity as? SettingsActivity)?.openScreen(screen, getString(titleRes))
    }

    private fun openAbout() {
        startActivity(android.content.Intent(requireContext(), AboutActivity::class.java))
    }

    private fun pickAppLanguage() {
        (activity as? SettingsActivity)?.showAppLanguagePicker()
    }
}
