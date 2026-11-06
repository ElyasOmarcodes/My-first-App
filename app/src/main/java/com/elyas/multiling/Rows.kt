package com.elyas.multiling

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

/** Binders for the few reusable pieces the screens are made of. */
object Rows {

    /** Bind an item_nav_row (inflated or <include>d). */
    fun bindNav(
        row: View, icon: Int, tone: Ui.Tone, title: CharSequence, sub: CharSequence?,
        endIcon: Int = R.drawable.ic_m_chevron, onClick: () -> Unit
    ) {
        val box = row.findViewById<View>(R.id.row_icon_box)
        val iv = row.findViewById<ImageView>(R.id.row_icon)
        iv.setImageResource(icon)
        Ui.tone(box, iv, tone)
        row.findViewById<TextView>(R.id.row_title).text = title
        val s = row.findViewById<TextView>(R.id.row_sub)
        s.text = sub ?: ""
        s.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        row.findViewById<ImageView>(R.id.row_end).setImageResource(endIcon)
        row.setOnClickListener { onClick() }
        Ui.tip(row, Ui.tipText(title, sub))
    }

    fun addNav(
        parent: ViewGroup, icon: Int, tone: Ui.Tone, title: CharSequence, sub: CharSequence?,
        endIcon: Int = R.drawable.ic_m_chevron, onClick: () -> Unit
    ): View {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.item_nav_row, parent, false)
        bindNav(row, icon, tone, title, sub, endIcon, onClick)
        parent.addView(row)
        return row
    }

    /** Bind an item_card_head. */
    fun bindHead(head: View, icon: Int, tone: Ui.Tone, title: CharSequence, sub: CharSequence?) {
        val box = head.findViewById<View>(R.id.head_icon_box)
        val iv = head.findViewById<ImageView>(R.id.head_icon)
        iv.setImageResource(icon)
        Ui.tone(box, iv, tone)
        head.findViewById<TextView>(R.id.head_title).text = title
        val s = head.findViewById<TextView>(R.id.head_sub)
        s.text = sub ?: ""
        s.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * The three app-language tiles. Returns a function that moves the
     * selection, so a caller can preview a choice before committing it.
     */
    fun languageOptions(
        container: LinearLayout, selected: String, onPick: (String) -> Unit
    ): (String) -> Unit {
        val c = container.context
        // each language named in its own script; the line under it in the UI language
        val items = listOf(
            Triple(AppLocale.PS, "پښتو", c.getString(R.string.lang_sub_ps)),
            Triple(AppLocale.FA, "دري · فارسي", c.getString(R.string.lang_sub_fa)),
            Triple(AppLocale.EN, "English", c.getString(R.string.lang_sub_en))
        )
        val tiles = ArrayList<Pair<String, MaterialCardView>>()
        val inflater = LayoutInflater.from(c)
        fun paint(code: String) {
            for ((k, card) in tiles) {
                val on = k == code
                card.strokeWidth = Ui.dp(c, if (on) 2f else 1f)
                card.strokeColor = Ui.attr(c, if (on) androidx.appcompat.R.attr.colorPrimary
                    else com.google.android.material.R.attr.colorOutlineVariant)
                card.setCardBackgroundColor(
                    if (on) Ui.attr(c, com.google.android.material.R.attr.colorSecondaryContainer)
                    else Ui.color(c, R.color.card))
                card.findViewById<View>(R.id.opt_check).visibility =
                    if (on) View.VISIBLE else View.INVISIBLE
                card.findViewById<TextView>(R.id.opt_title).setTextColor(
                    Ui.attr(c, if (on) com.google.android.material.R.attr.colorOnSecondaryContainer
                        else com.google.android.material.R.attr.colorOnSurface))
            }
        }
        for ((code, title, sub) in items) {
            val card = inflater.inflate(R.layout.item_option, container, false) as MaterialCardView
            card.findViewById<TextView>(R.id.opt_title).text = title
            card.findViewById<TextView>(R.id.opt_sub).text = sub
            card.setOnClickListener { paint(code); onPick(code) }
            container.addView(card)
            tiles.add(code to card)
        }
        paint(selected)
        return ::paint
    }

    fun tint(v: ImageView, color: Int) {
        v.imageTintList = ColorStateList.valueOf(color)
    }
}
