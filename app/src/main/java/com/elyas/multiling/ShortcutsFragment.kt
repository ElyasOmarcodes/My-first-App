package com.elyas.multiling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
 * A list of cards, an extended FAB that shrinks while you scroll, and a
 * bottom sheet for adding or editing — validation shows on the field itself.
 */
class ShortcutsFragment : Fragment(R.layout.frag_shortcuts) {

    private lateinit var store: AutoTextStore
    private var query = ""

    /** Hues the rows cycle through, so a long list does not read as a wall. */
    private val tones = arrayOf(Ui.Tone.VIOLET, Ui.Tone.TEAL, Ui.Tone.AMBER, Ui.Tone.BLUE,
        Ui.Tone.ROSE, Ui.Tone.GREEN, Ui.Tone.INDIGO, Ui.Tone.ORANGE)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        store = AutoTextStore(c)
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
        val entries = if (query.isEmpty()) all else all.filter { (k, t) ->
            k.contains(query, ignoreCase = true) || t.contains(query, ignoreCase = true)
        }
        val list = v.findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        v.findViewById<View>(R.id.empty).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.empty_title).setText(
            if (all.isEmpty()) R.string.shortcuts_empty else R.string.shortcuts_no_match)
        v.findViewById<View>(R.id.empty_sub).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

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
        v.findViewById<TextView>(R.id.sheet_title)
            .setText(if (oldKey == null) R.string.autotext_add else R.string.autotext_edit)
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
