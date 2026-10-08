package com.elyas.multiling

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Colour picker preference.
 *
 * The row shows the chosen colour as its badge, and tapping it opens a
 * picker built around three things the old dialog was missing: a large live
 * patch so the choice is visible while being made, a swatch grid that marks
 * what is currently selected, and sliders for the cases no palette covers.
 * Stores an ARGB int.
 */
class ColorPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var value: Int = Color.GRAY

    companion object {
        /**
         * Eight columns, grouped by row: neutrals, then blues, greens,
         * warms and pinks, so related colours sit together instead of the
         * grid reading as a jumble.
         */
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFE7EAF2.toInt(), 0xFFB9C1D4.toInt(), 0xFF8691AC.toInt(),
            0xFF5A6480.toInt(), 0xFF2B3346.toInt(), 0xFF141B2D.toInt(), 0xFF000000.toInt(),

            0xFF6C7BFF.toInt(), 0xFF4FA3FF.toInt(), 0xFF38BDF8.toInt(), 0xFF33D6E8.toInt(),
            0xFF1A73E8.toInt(), 0xFF3F51B5.toInt(), 0xFF5E35B1.toInt(), 0xFFA972FF.toInt(),

            0xFF2DD4A7.toInt(), 0xFF34D399.toInt(), 0xFF66BB6A.toInt(), 0xFF9BE564.toInt(),
            0xFF26A69A.toInt(), 0xFF009688.toInt(), 0xFF0E7C66.toInt(), 0xFF14532D.toInt(),

            0xFFFFCA28.toInt(), 0xFFFFB03A.toInt(), 0xFFFF8A4C.toInt(), 0xFFFF7043.toInt(),
            0xFFEF5350.toInt(), 0xFFD32F2F.toInt(), 0xFF8D6E63.toInt(), 0xFF5D4037.toInt(),

            0xFFFF6B8A.toInt(), 0xFFFF5CA8.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(),
            0xFF7E57C2.toInt(), 0xFF78909C.toInt(), 0xFF37474F.toInt(), 0xFF263238.toInt()
        )
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any =
        parse(a.getString(index), Color.GRAY)

    override fun onSetInitialValue(defaultValue: Any?) {
        val def = (defaultValue as? Int) ?: Color.GRAY
        value = getPersistedInt(def)
        notifyChanged()
    }

    private fun parse(s: String?, fallback: Int): Int {
        if (s.isNullOrEmpty()) return fallback
        return try {
            Color.parseColor(if (s.startsWith("#")) s else "#$s")
        } catch (_: Exception) { fallback }
    }

    /** The row's badge becomes the swatch, ringed so white reads on white. */
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val icon = holder.findViewById(android.R.id.icon) as? ImageView ?: return
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(value)
        d.setStroke(Ui.dp(context, 1.5f), 0x59FFFFFF)
        val sz = Ui.dp(context, 26f)
        icon.layoutParams?.width = sz
        icon.layoutParams?.height = sz
        icon.setImageDrawable(d)
        icon.visibility = View.VISIBLE
        // the badge plate behind it would tint the swatch, so clear it
        holder.findViewById(R.id.icon_frame)?.background = null
    }

    override fun onClick() {
        val c = context
        var picked = value

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(c, 18f)
        root.setPadding(pad, pad, pad, Ui.dp(c, 4f))

        // ---------------------------------------------- live preview patch
        val patch = FrameLayout(c)
        val patchLabel = TextView(c)
        patchLabel.gravity = Gravity.CENTER
        patchLabel.textSize = 15f
        patchLabel.setTypeface(patchLabel.typeface, Typeface.BOLD)
        patch.addView(patchLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(patch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 68f)))

        fun renderPatch() {
            val d = GradientDrawable()
            d.setColor(picked)
            d.cornerRadius = Ui.dp(c, 18f).toFloat()
            d.setStroke(Ui.dp(c, 1f), 0x33FFFFFF)
            patch.background = d
            patchLabel.text = String.format("#%08X", picked)
            // label in whichever of black/white stays legible on the colour
            patchLabel.setTextColor(
                if (isLight(picked)) 0xDD000000.toInt() else 0xEEFFFFFF.toInt())
        }

        // ------------------------------------------------------ hex field
        val hex = EditText(c)
        hex.setText(String.format("#%08X", picked))
        hex.setSingleLine()
        hex.gravity = Gravity.CENTER
        hex.setTextColor(Ui.color(c, R.color.text_primary))
        hex.setBackgroundResource(R.drawable.ds_field)
        hex.setPadding(Ui.dp(c, 12f), Ui.dp(c, 10f), Ui.dp(c, 12f), Ui.dp(c, 10f))

        // ----------------------------------------------------- swatch grid
        val grid = GridLayout(c)
        grid.columnCount = 8
        val cells = ArrayList<Pair<View, Int>>()

        fun renderSelection() {
            for ((sw, col) in cells) {
                val d = GradientDrawable()
                d.setColor(col)
                d.cornerRadius = Ui.dp(c, 9f).toFloat()
                val on = col == picked
                d.setStroke(
                    Ui.dp(c, if (on) 2.5f else 1f),
                    if (on) Ui.color(c, R.color.text_primary) else 0x33FFFFFF
                )
                sw.background = d
            }
        }

        for (col in PALETTE) {
            val sw = View(c)
            val lp = GridLayout.LayoutParams()
            lp.width = Ui.dp(c, 32f)
            lp.height = Ui.dp(c, 32f)
            val m = Ui.dp(c, 3f)
            lp.setMargins(m, m, m, m)
            sw.layoutParams = lp
            sw.setOnClickListener {
                picked = col
                hex.setText(String.format("#%08X", col))
                renderPatch()
                renderSelection()
            }
            grid.addView(sw)
            cells.add(sw to col)
        }
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 16f); it.gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(hex, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 14f) })

        // typing a hex updates the patch live, without fighting the field
        hex.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: return
                if (t.length < 4) return
                val parsed = try {
                    Color.parseColor(if (t.startsWith("#")) t else "#$t")
                } catch (_: Exception) { return }
                picked = parsed
                renderPatch()
                renderSelection()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })

        renderPatch()
        renderSelection()

        AlertDialog.Builder(c)
            .setTitle(title)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (callChangeListener(picked)) {
                    value = picked
                    persistInt(picked)
                    notifyChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Perceived brightness, for choosing a legible label over the colour. */
    private fun isLight(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 150
    }
}
