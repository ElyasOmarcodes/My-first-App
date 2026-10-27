package com.elyas.multiling

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import android.view.animation.PathInterpolator

/**
 * The floating tab bar of the app shell.
 *
 * Every tab shows its icon over its label; the selected one sits on a
 * rounded highlight in the tonal container colour, with a filled icon and a
 * bold label. Switching fades the highlight across, grows it out from the
 * icon and gives the icon a small spring.
 *
 * Unlike BottomNavigationView it never pads itself for the system bars, so
 * the bar keeps one compact height on gesture and 3-button navigation alike;
 * the activity lifts the whole card above the navigation bar instead.
 */
class FloatingTabBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    class Tab(val id: String, val icon: Int, val iconSelected: Int, val label: Int)

    private class Holder(val tab: Tab, val cell: View, val pill: View,
                         val icon: ImageView, val label: TextView)

    private val holders = ArrayList<Holder>()
    var selected: String? = null
        private set

    /** Selection changed (by the user or [select]). */
    var onSelect: ((String) -> Unit)? = null
    /** The current tab was tapped again. */
    var onReselect: ((String) -> Unit)? = null

    private val colPill = Ui.attr(context, com.google.android.material.R.attr.colorSecondaryContainer)
    private val colOn = Ui.attr(context, com.google.android.material.R.attr.colorOnSecondaryContainer)
    private val colOff = Ui.attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant)

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    fun setTabs(tabs: List<Tab>) {
        removeAllViews()
        holders.clear()
        val inf = LayoutInflater.from(context)
        for (t in tabs) {
            val cell = inf.inflate(R.layout.item_nav_tab, this, false)
            val h = Holder(t, cell, cell.findViewById(R.id.nav_pill),
                cell.findViewById(R.id.nav_icon), cell.findViewById(R.id.nav_label))
            h.label.setText(t.label)
            h.icon.setImageResource(t.icon)
            h.cell.contentDescription = context.getString(t.label)
            Ui.tip(h.cell, context.getString(t.label))
            paint(h, false)
            cell.setOnClickListener {
                if (selected == t.id) {
                    onReselect?.invoke(t.id)
                } else {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    select(t.id, animate = true)
                    onSelect?.invoke(t.id)
                }
            }
            holders.add(h)
            addView(cell)
        }
    }

    /** Move the selection; [animate] false for the first layout. */
    fun select(id: String, animate: Boolean) {
        if (id == selected) return
        val old = holders.firstOrNull { it.tab.id == selected }
        val new = holders.firstOrNull { it.tab.id == id } ?: return
        selected = id
        old?.let { setState(it, false, animate) }
        setState(new, true, animate)
    }

    private fun setState(h: Holder, on: Boolean, animate: Boolean) {
        h.icon.setImageResource(if (on) h.tab.iconSelected else h.tab.icon)
        h.label.setTypeface(Fonts.get(context), if (on) android.graphics.Typeface.BOLD
            else android.graphics.Typeface.NORMAL)
        h.cell.isSelected = on
        ViewCompat.setStateDescription(h.cell, if (on) h.label.text else null)
        if (!animate) { paint(h, on); return }
        // fade the highlight and the icon/label colour across
        ValueAnimator.ofFloat(if (on) 0f else 1f, if (on) 1f else 0f).apply {
            duration = 240
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { paintFraction(h, it.animatedValue as Float) }
            start()
        }
        if (on) {
            // the highlight grows out from the icon, and the icon springs
            h.pill.scaleX = 0.82f
            h.pill.scaleY = 0.82f
            h.pill.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.6f)).start()
            h.icon.scaleX = 0.8f
            h.icon.scaleY = 0.8f
            h.icon.animate().scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(OvershootInterpolator(2.6f)).start()
        }
    }

    private fun paint(h: Holder, on: Boolean) = paintFraction(h, if (on) 1f else 0f)

    private val argb = ArgbEvaluator()

    private fun paintFraction(h: Holder, f: Float) {
        val pillCol = argb.evaluate(f, colPill and 0x00FFFFFF, colPill) as Int
        ViewCompat.setBackgroundTintList(h.pill, ColorStateList.valueOf(pillCol))
        val fg = argb.evaluate(f, colOff, colOn) as Int
        h.icon.imageTintList = ColorStateList.valueOf(fg)
        h.label.setTextColor(fg)
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
}
