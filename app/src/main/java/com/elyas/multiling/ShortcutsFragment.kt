package com.elyas.multiling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Shortcuts tab (AutoText): type a short token, get the whole phrase.
 * One list per keyboard language, picked from a row of language cards; a
 * list of cards, an extended FAB that shrinks while you scroll, and a
 * bottom sheet for adding or editing — validation shows on the field itself.
 */
class ShortcutsFragment : Fragment(R.layout.frag_shortcuts) {

    private lateinit var store: AutoTextStore
    private var query = ""

    /** Each language: its own name, the letter on its badge, its hue. */
    private class LangInfo(val code: String, val name: String, val badge: String, val tone: Ui.Tone)

    private val langs = listOf(
        LangInfo("ps", "پښتو", "ښ", Ui.Tone.VIOLET),
        LangInfo("fa", "دري / فارسي", "د", Ui.Tone.TEAL),
        LangInfo("ar", "العربية", "ع", Ui.Tone.AMBER),
        LangInfo("ur", "اردو", "ے", Ui.Tone.GREEN),
        LangInfo("en", "English", "En", Ui.Tone.BLUE)
    )

    private fun current(): LangInfo = langs.first { it.code == store.lang }

    /** Hues the rows cycle through, so a long list does not read as a wall. */
    private val tones = arrayOf(Ui.Tone.VIOLET, Ui.Tone.TEAL, Ui.Tone.AMBER, Ui.Tone.BLUE,
        Ui.Tone.ROSE, Ui.Tone.GREEN, Ui.Tone.INDIGO, Ui.Tone.ORANGE)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        store = AutoTextStore.selected(c)
        val scroll = view.findViewById<NestedScrollView>(R.id.scroll)
        Ui.padForBars(scroll, top = true, bottom = true)

        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab)
        Ui.marginForNavBar(fab, Ui.dp(c, 98f))
        fab.setOnClickListener { edit(null, null) }
        // the FAB folds to its icon while the list moves, like native lists
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
            if (y > oldY + 4 && fab.isExtended) fab.shrink()
            else if (y < oldY - 4 && !fab.isExtended) fab.extend()
        })

        Ui.tone(view.findViewById(R.id.empty_icon_box), view.findViewById(R.id.empty_icon), Ui.Tone.TEAL)
        view.findViewById<View>(R.id.empty_restore).setOnClickListener {
            val n = store.restoreDefaults()
            Toast.makeText(c, getString(R.string.shortcuts_restored, Ui.digits(c, n)), Toast.LENGTH_SHORT).show()
            refresh()
        }
        view.findViewById<View>(R.id.sc_hero).clipToOutline = true
        // live filter over shortcut and phrase
        view.findViewById<TextInputEditText>(R.id.search_in).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) {
                    query = e?.toString()?.trim() ?: ""
                    refresh(animate = false)
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    private fun refresh(animate: Boolean = true) {
        val v = view ?: return
        val c = requireContext()
        AutoTextStore.bumpDataVersion(c)
        val all = store.all()
        v.findViewById<TextView>(R.id.count).text =
            getString(R.string.shortcuts_count, Ui.digits(c, all.size))
        bindLangs(v)
        // the worked example speaks the selected language
        all.firstOrNull()?.let { (k, t) ->
            v.findViewById<TextView>(R.id.demo_key).text = k
            v.findViewById<TextView>(R.id.demo_text).text = t.replace("\n", " ")
        }
        val entries = if (query.isEmpty()) all else all.filter { (k, t) ->
            k.contains(query, ignoreCase = true) || t.contains(query, ignoreCase = true)
        }
        val list = v.findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        v.findViewById<View>(R.id.empty).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.empty_title).setText(
            if (all.isEmpty()) R.string.shortcuts_empty else R.string.shortcuts_no_match)
        v.findViewById<View>(R.id.empty_sub).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.empty_restore).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(c)
        for ((i, e) in entries.withIndex()) {
            val item = inflater.inflate(R.layout.item_shortcut, list, false)
            val tone = tones[i % tones.size]
            item.findViewById<TextView>(R.id.sc_initial).apply {
                text = e.first.take(1)
                androidx.core.view.ViewCompat.setBackgroundTintList(this,
                    android.content.res.ColorStateList.valueOf(Ui.color(c, tone.bg)))
                setTextColor(Ui.color(c, tone.fg))
            }
            item.findViewById<TextView>(R.id.sc_key).apply {
                text = e.first
                setTextColor(Ui.color(c, tone.fg))
            }
            // a line break inside a phrase is shown as a mark, not a broken card
            item.findViewById<TextView>(R.id.sc_text).text = e.second.replace("\n", " ⏎ ")
            item.setOnClickListener { edit(e.first, e.second) }
            item.findViewById<View>(R.id.sc_delete).setOnClickListener { confirmDelete(e.first, e.second) }
            Ui.tip(item.findViewById(R.id.sc_delete), getString(R.string.learned_delete))
            list.addView(item)
            if (animate && i < 12) Ui.rise(item, i)
        }
    }

    /**
     * The language row: the selected language sits on its tone with a
     * coloured outline, the rest are quiet outlined cards. Picking one
     * switches the list (and what import/export work on).
     */
    private fun bindLangs(v: View) {
        val c = requireContext()
        val row = v.findViewById<LinearLayout>(R.id.langs)
        val inflater = LayoutInflater.from(c)
        if (row.childCount != langs.size) {
            row.removeAllViews()
            for (l in langs) {
                val card = inflater.inflate(R.layout.item_lang_pill, row, false)
                card.setOnClickListener { pick(l.code) }
                row.addView(card)
            }
        }
        for ((i, l) in langs.withIndex()) {
            val card = row.getChildAt(i) as com.google.android.material.card.MaterialCardView
            val on = l.code == store.lang
            val fg = Ui.color(c, l.tone.fg)
            val bg = Ui.color(c, l.tone.bg)
            val surface = Ui.color(c, R.color.card)
            card.setCardBackgroundColor(if (on) androidx.core.graphics.ColorUtils.blendARGB(surface, bg, 0.7f) else surface)
            card.strokeColor = if (on) fg else Ui.color(c, R.color.card_stroke)
            card.isSelected = on
            card.contentDescription = l.name
            card.findViewById<TextView>(R.id.lp_badge).apply {
                text = l.badge
                androidx.core.view.ViewCompat.setBackgroundTintList(this,
                    android.content.res.ColorStateList.valueOf(if (on) fg else bg))
                setTextColor(if (on) Ui.color(c, android.R.color.white) else fg)
            }
            card.findViewById<TextView>(R.id.lp_name).text = l.name
            val n = if (on) store.all().size else AutoTextStore(c, l.code).all().size
            card.findViewById<TextView>(R.id.lp_count).text =
                getString(R.string.shortcuts_lang_count, Ui.digits(c, n))
        }
    }

    private fun pick(code: String) {
        if (code == store.lang) return
        val c = requireContext()
        store.save()
        store = AutoTextStore(c, code)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(c)
            .edit().putString("autotext_lang", code).apply()
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        refresh()
    }

    private fun confirmDelete(key: String, text: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(key)
            .setMessage(R.string.autotext_delete_q)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // a shortcut can carry several phrases: remove only this one
                store.remove(key, text)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Add when [oldKey] is null, otherwise edit that pair in place. */
    private fun edit(oldKey: String?, oldText: String?) {
        val c = requireContext()
        val sheet = BottomSheetDialog(c)
        val v = layoutInflater.inflate(R.layout.sheet_shortcut, null)
        v.findViewById<TextView>(R.id.sheet_title).text = getString(
            if (oldKey == null) R.string.autotext_add else R.string.autotext_edit) + " · " + current().name
        val keyBox = v.findViewById<TextInputLayout>(R.id.key_box)
        val textBox = v.findViewById<TextInputLayout>(R.id.text_box)
        val keyIn = v.findViewById<TextInputEditText>(R.id.key_in)
        val textIn = v.findViewById<TextInputEditText>(R.id.text_in)
        keyIn.setText(oldKey ?: "")
        textIn.setText(oldText ?: "")

        v.findViewById<View>(R.id.sheet_save).setOnClickListener {
            val k = keyIn.text?.toString()?.trim() ?: ""
            val t = textIn.text?.toString()?.trim() ?: ""
            keyBox.error = when {
                k.isEmpty() -> getString(R.string.autotext_fill_both)
                k.contains(' ') -> getString(R.string.autotext_no_space)
                else -> null
            }
            textBox.error = if (t.isEmpty()) getString(R.string.autotext_fill_both) else null
            if (keyBox.error != null || textBox.error != null) return@setOnClickListener
            if (oldKey != null && oldText != null) store.remove(oldKey, oldText)
            store.put(k, t)
            sheet.dismiss()
            refresh()
        }
        sheet.setContentView(v)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }
}
