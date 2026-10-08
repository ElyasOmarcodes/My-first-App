package com.elyas.multiling

import androidx.appcompat.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * AutoText shortcuts: type a short token, get the full phrase.
 *
 * Rebuilt as a list of cards. The add form used to sit permanently at the
 * top of the screen, pushing the list — the thing you came to look at —
 * below the fold; it moves into a sheet opened from a button, so the list
 * owns the page.
 */
class AutoTextActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var store: AutoTextStore
    private lateinit var page: LinearLayout
    private var entries: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AutoTextStore(this)
        page = Ui.page(this, getString(R.string.autotext_title))
        refresh()
    }

    private fun refresh() {
        AutoTextStore.bumpDataVersion(this)
        entries = store.all()
        page.removeAllViews()
        val c = this

        // ------------------------------------------------------ explainer
        val intro = Ui.card(c)
        intro.setPadding(Ui.dp(c, 16f), Ui.dp(c, 16f), Ui.dp(c, 16f), Ui.dp(c, 16f))
        val introRow = LinearLayout(c)
        introRow.orientation = LinearLayout.HORIZONTAL
        introRow.addView(
            Ui.badge(c, R.drawable.ic_cat_autotext, Ui.color(c, R.color.c_lime)),
            LinearLayout.LayoutParams(Ui.dp(c, 44f), Ui.dp(c, 44f))
                .also { it.marginEnd = Ui.dp(c, 12f) })
        introRow.addView(
            Ui.body(c, getString(R.string.autotext_desc)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        intro.addView(introRow)
        Ui.addCard(page, intro, 8f)

        // ------------------------------------------------------ add button
        page.addView(
            Ui.primaryButton(c, getString(R.string.autotext_add)) { showSheet(null, null) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = Ui.dp(c, 14f) })

        // ----------------------------------------------------------- list
        if (entries.isEmpty()) {
            val empty = Ui.card(c)
            empty.setPadding(Ui.dp(c, 20f), Ui.dp(c, 34f), Ui.dp(c, 20f), Ui.dp(c, 34f))
            empty.gravity = Gravity.CENTER
            val tv = Ui.body(c, getString(R.string.autotext_list_hint), R.color.text_tertiary)
            tv.gravity = Gravity.CENTER
            empty.addView(tv)
            Ui.addCard(page, empty)
            return
        }

        page.addView(Ui.sectionHeader(c,
            getString(R.string.autotext_count, entries.size)))

        val card = Ui.card(c)
        for ((i, e) in entries.withIndex()) {
            if (i > 0) Ui.addDivider(card)
            card.addView(entryRow(e.first, e.second), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        Ui.addCard(page, card, 4f)
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(260).start()
    }

    /**
     * One shortcut: the token in a chip, the phrase under it, and a delete
     * button. Deleting used to be hidden behind a long-press with nothing to
     * suggest it was there.
     */
    private fun entryRow(shortcut: String, expansion: String): View {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.ds_row)
        row.setPadding(Ui.dp(c, 10f), Ui.dp(c, 12f), Ui.dp(c, 8f), Ui.dp(c, 12f))
        row.isClickable = true
        row.setOnClickListener { showSheet(shortcut, expansion) }

        val texts = LinearLayout(c)
        texts.orientation = LinearLayout.VERTICAL

        val tok = TextView(c)
        tok.text = shortcut
        tok.textSize = 14f
        tok.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        tok.setTextColor(Ui.color(c, R.color.c_lime))
        tok.setBackgroundResource(R.drawable.ds_chip)
        tok.setPadding(Ui.dp(c, 10f), Ui.dp(c, 4f), Ui.dp(c, 10f), Ui.dp(c, 4f))
        texts.addView(tok, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        val full = TextView(c)
        // a newline in a phrase is shown as a mark, not as a broken row
        full.text = expansion.replace("\n", " ⏎ ")
        full.textSize = 14f
        full.maxLines = 2
        full.ellipsize = android.text.TextUtils.TruncateAt.END
        full.setTextColor(Ui.color(c, R.color.text_secondary))
        full.setPadding(Ui.dp(c, 2f), Ui.dp(c, 6f), 0, 0)
        texts.addView(full)

        row.addView(texts, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val del = ImageView(c)
        del.setImageDrawable(
            Ui.icon(c, R.drawable.ic_key_trash, Ui.color(c, R.color.danger), 19f))
        del.scaleType = ImageView.ScaleType.FIT_CENTER
        del.background = androidx.appcompat.content.res.AppCompatResources
            .getDrawable(c, R.drawable.ds_btn_ghost)
        del.setPadding(Ui.dp(c, 9f), Ui.dp(c, 9f), Ui.dp(c, 9f), Ui.dp(c, 9f))
        del.setOnClickListener { confirmDelete(shortcut, expansion) }
        row.addView(del, LinearLayout.LayoutParams(Ui.dp(c, 38f), Ui.dp(c, 38f)))
        return row
    }

    private fun confirmDelete(shortcut: String, expansion: String) {
        AlertDialog.Builder(this)
            .setTitle(shortcut)
            .setMessage(R.string.autotext_delete_q)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // a shortcut can carry several phrases: delete only this one
                store.remove(shortcut, expansion)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Add when [oldShortcut] is null, otherwise edit that pair in place. */
    private fun showSheet(oldShortcut: String?, oldExpansion: String?) {
        val c = this
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val p = Ui.dp(c, 20f)
        box.setPadding(p, Ui.dp(c, 8f), p, 0)

        fun field(hint: String, value: String?, multi: Boolean): EditText {
            val e = EditText(c)
            e.hint = hint
            e.setText(value ?: "")
            e.setTextColor(Ui.color(c, R.color.text_primary))
            e.setHintTextColor(Ui.color(c, R.color.text_tertiary))
            e.setBackgroundResource(R.drawable.ds_field)
            e.setPadding(Ui.dp(c, 14f), Ui.dp(c, 12f), Ui.dp(c, 14f), Ui.dp(c, 12f))
            if (multi) {
                e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                e.maxLines = 8
                e.gravity = Gravity.TOP or Gravity.START
            } else {
                e.setSingleLine()
                e.setTypeface(Typeface.MONOSPACE)
            }
            return e
        }

        val shortIn = field(getString(R.string.autotext_shortcut_hint), oldShortcut, false)
        val fullIn = field(getString(R.string.autotext_expansion_hint), oldExpansion, true)
        box.addView(shortIn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(fullIn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 10f) })

        AlertDialog.Builder(c)
            .setTitle(if (oldShortcut == null) R.string.autotext_add else R.string.autotext_edit)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ns = shortIn.text.toString().trim()
                val ne = fullIn.text.toString().trim()
                when {
                    ns.isEmpty() || ne.isEmpty() ->
                        toast(getString(R.string.autotext_fill_both))
                    ns.contains(' ') ->
                        toast(getString(R.string.autotext_no_space))
                    else -> {
                        if (oldShortcut != null && oldExpansion != null) {
                            store.remove(oldShortcut, oldExpansion)
                        }
                        store.put(ns, ne)
                        refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
