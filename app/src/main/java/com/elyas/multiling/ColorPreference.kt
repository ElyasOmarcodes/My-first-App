package com.elyas.multiling

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A simple color picker preference: shows the current color as a swatch and
 * opens a dialog with a palette grid plus a hex field. Stores an ARGB int.
 */
class ColorPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var value: Int = Color.GRAY

    companion object {
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFF9E9E9E.toInt(), 0xFF616161.toInt(),
            0xFF37474F.toInt(), 0xFF263238.toInt(), 0xFF15171B.toInt(), 0xFF000000.toInt(),
            0xFF4FA3FF.toInt(), 0xFF1A73E8.toInt(), 0xFF3F51B5.toInt(), 0xFF7E57C2.toInt(),
            0xFF66BB6A.toInt(), 0xFF26A69A.toInt(), 0xFF00ACC1.toInt(), 0xFF009688.toInt(),
            0xFFEF5350.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(), 0xFFFF7043.toInt(),
            0xFFFFA726.toInt(), 0xFFFFCA28.toInt(), 0xFF8D6E63.toInt(), 0xFF78909C.toInt()
        )
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        val s = a.getString(index)
        return parse(s, Color.GRAY)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val def = (defaultValue as? Int) ?: Color.GRAY
        value = getPersistedInt(def)
        notifyChanged()
    }

    private fun parse(s: String?, fallback: Int): Int {
        if (s.isNullOrEmpty()) return fallback
        return try { Color.parseColor(if (s.startsWith("#")) s else "#$s") } catch (_: Exception) { fallback }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // reuse the icon frame as a color swatch
        val icon = holder.findViewById(android.R.id.icon) as? android.widget.ImageView
        if (icon != null) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(value)
            d.setStroke(2, 0x66888888)
            val density = context.resources.displayMetrics.density
            val sz = (28 * density).toInt()
            icon.layoutParams?.width = sz
            icon.layoutParams?.height = sz
            icon.setImageDrawable(d)
            icon.visibility = View.VISIBLE
        }
    }

    override fun onClick() {
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        val pad = (16 * density).toInt()
        root.setPadding(pad, pad, pad, 0)

        val grid = GridLayout(context)
        grid.columnCount = 8
        val dialog = AlertDialog.Builder(context)

        val hex = EditText(context)
        hex.setText(String.format("#%08X", value))
        hex.setSingleLine()

        for (c in PALETTE) {
            val sw = View(context)
            val d = GradientDrawable()
            d.setColor(c)
            d.cornerRadius = 6 * density
            d.setStroke(1, 0x66888888)
            sw.background = d
            val lp = GridLayout.LayoutParams()
            lp.width = (34 * density).toInt()
            lp.height = (34 * density).toInt()
            lp.setMargins((3 * density).toInt(), (3 * density).toInt(),
                (3 * density).toInt(), (3 * density).toInt())
            sw.layoutParams = lp
            sw.setOnClickListener { hex.setText(String.format("#%08X", c)) }
            grid.addView(sw)
        }

        root.addView(grid)
        root.addView(hex)

        dialog.setTitle(title)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val c = parse(hex.text.toString(), value)
                if (callChangeListener(c)) {
                    value = c
                    persistInt(c)
                    notifyChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
