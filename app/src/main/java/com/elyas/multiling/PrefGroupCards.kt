package com.elyas.multiling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws each run of preference rows inside one rounded card, with section
 * headings left outside it — the grouped look of a native settings screen.
 *
 * A decoration rather than wrapper views, so the stock PreferenceFragment
 * keeps its RecyclerView and recycling untouched. A heading is recognised by
 * the tag on pref_category.xml.
 *
 * Only visible children are known, so a run cut off by the top or bottom of
 * the viewport is extended past that edge: its rounded corner is then drawn
 * off-screen. That is also right when the heading has just scrolled away,
 * because the first row's top is then at or above the viewport top anyway.
 */
class PrefGroupCards(c: Context) : RecyclerView.ItemDecoration() {

    private val radius = Ui.dp(c, 26f).toFloat()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(c, R.color.card)
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(c, R.color.card_stroke)
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(c, 1f).toFloat()
    }
    private val rect = RectF()

    private fun isHeading(v: View) = v.tag == "pref_category"

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val n = parent.childCount
        var i = 0
        while (i < n) {
            val first = parent.getChildAt(i)
            if (isHeading(first)) { i++; continue }
            var j = i
            while (j + 1 < n && !isHeading(parent.getChildAt(j + 1))) j++
            val last = parent.getChildAt(j)

            var top = first.top + first.translationY
            var bottom = last.bottom + last.translationY
            // the run continues above / below what is on screen
            if (i == 0 && parent.getChildAdapterPosition(first) > 0) top = -radius * 2
            if (j == n - 1) {
                val pos = parent.getChildAdapterPosition(last)
                val count = parent.adapter?.itemCount ?: 0
                if (pos in 0 until count - 1) bottom = parent.height + radius * 2
            }
            val half = stroke.strokeWidth / 2f
            rect.set(first.left + half, top + half, first.right - half, bottom - half)
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, stroke)
            i = j + 1
        }
    }
}
