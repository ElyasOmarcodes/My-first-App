package com.elyas.multiling

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom keyboard view: draws the key grid, handles taps, long-press popups
 * with slide-to-select alternates, key repeat, key preview and space-bar
 * swipes for language switching.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onChar(text: String)
        fun onSpecial(code: Int)
        fun onLangSwipe(forward: Boolean)
        fun onSpaceLongPress()
    }

    var listener: Listener? = null

    // ------------------------------------------------------------ theming
    class Theme(
        val background: Int,
        val keyFill: Int,
        val specialFill: Int,
        val keyPressed: Int,
        val text: Int,
        val hint: Int,
        val accent: Int
    )

    companion object {
        val DARK = Theme(0xFF15171B.toInt(), 0xFF272B33.toInt(), 0xFF1D2026.toInt(),
            0xFF3C4250.toInt(), Color.WHITE, 0xFF9AA7C7.toInt(), 0xFF4FA3FF.toInt())
        val LIGHT = Theme(0xFFE8EAED.toInt(), 0xFFFFFFFF.toInt(), 0xFFD2D6DB.toInt(),
            0xFFBFC5CC.toInt(), 0xFF202124.toInt(), 0xFF6B7280.toInt(), 0xFF1A73E8.toInt())
        val BLACK = Theme(0xFF000000.toInt(), 0xFF141414.toInt(), 0xFF0A0A0A.toInt(),
            0xFF333333.toInt(), Color.WHITE, 0xFF8899AA.toInt(), 0xFF4FA3FF.toInt())

        fun themeByName(name: String): Theme = when (name) {
            "light" -> LIGHT
            "black" -> BLACK
            else -> DARK
        }
    }

    var theme: Theme = DARK
        set(value) { field = value; invalidate() }

    var keyHeightDp: Int = 52
    var fontScale: Float = 1f
    var showHints: Boolean = true
    var showPreview: Boolean = true
    var longPressTimeout: Long = 350
    var shiftState: Int = 0 // 0 off, 1 once, 2 locked

    private val density = resources.displayMetrics.density

    // ------------------------------------------------------------- key data
    private class PlacedKey(val def: KeyDef, val rect: RectF, val displayLabel: String)

    private var rows: List<List<KeyDef>> = emptyList()
    private var displayRows: List<List<String>> = emptyList()
    private var placed: ArrayList<PlacedKey> = ArrayList()

    fun setKeyboard(rows: List<List<KeyDef>>, displayRows: List<List<String>>) {
        this.rows = rows
        this.displayRows = displayRows
        pressedKey = null
        dismissPopups()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    // -------------------------------------------------------------- paints
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // --------------------------------------------------------------- state
    private var pressedKey: PlacedKey? = null
    private var downX = 0f
    private var downY = 0f
    private var spaceSwiped = false
    private var swipeDir = 0
    private var longPressFired = false

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = pressedKey ?: return
            if (key.def.repeatable) {
                emit(key)
                handler.postDelayed(this, 50)
            }
        }
    }

    // -------------------------------------------------------------- popups
    private var previewPopup: PopupWindow? = null
    private var previewText: TextView? = null
    private var altPopup: PopupWindow? = null
    private var altViews: List<TextView> = emptyList()
    private var altChars: List<String> = emptyList()
    private var altIndex = 0
    private var altPopupLeftInView = 0f
    private var altCellWidth = 0f

    // ------------------------------------------------------------- measure
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowH = (keyHeightDp * density).toInt()
        val height = rowH * max(1, rows.size) + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        placed = ArrayList()
        if (rows.isEmpty() || width == 0) return
        val rowH = keyHeightDp * density
        val gap = 1.5f * density
        val sidePad = 1.5f * density
        var y = paddingTop.toFloat()
        for ((ri, row) in rows.withIndex()) {
            val totalW = row.sumOf { it.width.toDouble() }.toFloat()
            val unit = (width - 2 * sidePad) / totalW
            var x = sidePad
            for ((ki, key) in row.withIndex()) {
                val w = key.width * unit
                val rect = RectF(x + gap, y + gap, x + w - gap, y + rowH - gap)
                val label = displayRows.getOrNull(ri)?.getOrNull(ki) ?: key.label
                placed.add(PlacedKey(key, rect, label))
                x += w
            }
            y += rowH
        }
    }

    // ---------------------------------------------------------------- draw
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val radius = 6f * density
        for (pk in placed) {
            val key = pk.def
            val isPressed = pk === pressedKey
            fillPaint.color = when {
                isPressed -> theme.keyPressed
                key.code == Keys.SHIFT && shiftState > 0 -> theme.accent
                key.code != 0 && key.code != Keys.SPACE -> theme.specialFill
                else -> theme.keyFill
            }
            canvas.drawRoundRect(pk.rect, radius, radius, fillPaint)

            // main label
            val isSpecial = key.code != 0
            val base = pk.rect.height() * (if (isSpecial) 0.38f else 0.46f)
            textPaint.textSize = base * fontScale
            textPaint.color =
                if (key.code == Keys.SHIFT && shiftState == 2) theme.background else theme.text
            val cx = pk.rect.centerX()
            val cy = pk.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(pk.displayLabel, cx, cy, textPaint)

            // hint (top corner)
            if (showHints && (key.code == 0 || key.hint != null)) {
                val hint = key.hint ?: key.shifted ?: key.alternates.firstOrNull()
                if (hint != null && (shiftState == 0 || key.code != 0)) {
                    hintPaint.textSize = pk.rect.height() * 0.24f * fontScale
                    hintPaint.color = theme.hint
                    canvas.drawText(
                        hint,
                        pk.rect.right - 4 * density,
                        pk.rect.top + hintPaint.textSize + 2 * density,
                        hintPaint
                    )
                }
            }
        }
    }

    // --------------------------------------------------------------- touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                spaceSwiped = false
                longPressFired = false
                val key = keyAt(event.x, event.y) ?: return true
                pressedKey = key
                invalidate()
                if (key.def.repeatable) {
                    emit(key)
                    handler.postDelayed(repeatRunnable, 400)
                } else {
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    if (showPreview && key.def.code == 0) showPreview(key)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val key = pressedKey ?: return true
                if (altPopup != null) {
                    updateAltSelection(event.x)
                    return true
                }
                if (key.def.code == Keys.SPACE) {
                    val dx = event.x - downX
                    if (abs(dx) > 40 * density) {
                        spaceSwiped = true
                        swipeDir = if (dx < 0) -1 else 1
                        handler.removeCallbacks(longPressRunnable)
                    }
                } else if (!key.rect.contains(event.x, event.y)) {
                    // finger slid off the key: cancel pending actions
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(repeatRunnable)
                    dismissPreview()
                    pressedKey = null
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(repeatRunnable)
                val key = pressedKey
                if (altPopup != null) {
                    commitAltSelection()
                } else if (key != null && !longPressFired) {
                    if (key.def.code == Keys.SPACE && spaceSwiped) {
                        listener?.onLangSwipe(swipeDir < 0)
                    } else if (!key.def.repeatable) {
                        emit(key)
                    }
                }
                dismissPreview()
                pressedKey = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(repeatRunnable)
                dismissPopups()
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): PlacedKey? {
        for (pk in placed) {
            if (pk.rect.contains(x, y)) return pk
        }
        // be forgiving: allow touches in the gaps
        var best: PlacedKey? = null
        var bestDist = Float.MAX_VALUE
        for (pk in placed) {
            val dx = max(0f, max(pk.rect.left - x, x - pk.rect.right))
            val dy = max(0f, max(pk.rect.top - y, y - pk.rect.bottom))
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = pk }
        }
        return if (bestDist < (12 * density) * (12 * density)) best else null
    }

    private fun emit(key: PlacedKey) {
        if (key.def.code == 0) listener?.onChar(key.displayLabel)
        else listener?.onSpecial(key.def.code)
    }

    // ---------------------------------------------------------- long press
    private fun onLongPress() {
        val key = pressedKey ?: return
        if (key.def.code == Keys.SPACE) {
            longPressFired = true
            dismissPreview()
            listener?.onSpaceLongPress()
            return
        }
        if (key.def.code == Keys.SYM) {
            // long-press on "123" opens voice input, like the mic hint shows
            longPressFired = true
            dismissPreview()
            listener?.onSpecial(Keys.MIC)
            return
        }
        val chars = key.def.popupChars(shiftState > 0)
        if (chars.isEmpty()) return
        longPressFired = true
        dismissPreview()
        showAltPopup(key, chars)
    }

    private fun showAltPopup(key: PlacedKey, chars: List<String>) {
        val cellW = max(key.rect.width(), 46 * density)
        val cellH = key.rect.height()
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        val bg = GradientDrawable()
        bg.setColor(theme.keyPressed)
        bg.cornerRadius = 8 * density
        container.background = bg
        val views = ArrayList<TextView>()
        for (c in chars) {
            val tv = TextView(context)
            tv.text = c
            tv.gravity = Gravity.CENTER
            tv.setTextColor(theme.text)
            tv.textSize = 26f
            tv.layoutParams = LinearLayout.LayoutParams(cellW.toInt(), cellH.toInt())
            container.addView(tv)
            views.add(tv)
        }
        altViews = views
        altChars = chars
        altCellWidth = cellW
        val totalW = cellW * chars.size
        var left = key.rect.centerX() - totalW / 2
        left = min(max(4 * density, left), width - totalW - 4 * density)
        altPopupLeftInView = left
        altIndex = 0
        highlightAlt()

        val popup = PopupWindow(container, totalW.toInt(), cellH.toInt(), false)
        popup.isClippingEnabled = false
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val yInWindow = loc[1] + key.rect.top - cellH - 8 * density
        popup.showAtLocation(this, Gravity.NO_GRAVITY, (loc[0] + left).toInt(), yInWindow.toInt())
        altPopup = popup
    }

    private fun updateAltSelection(x: Float) {
        if (altChars.isEmpty()) return
        val idx = ((x - altPopupLeftInView) / altCellWidth).toInt()
        altIndex = min(max(0, idx), altChars.size - 1)
        highlightAlt()
    }

    private fun highlightAlt() {
        for ((i, tv) in altViews.withIndex()) {
            if (i == altIndex) {
                val d = GradientDrawable()
                d.setColor(theme.accent)
                d.cornerRadius = 8 * density
                tv.background = d
            } else {
                tv.background = null
            }
        }
    }

    private fun commitAltSelection() {
        val c = altChars.getOrNull(altIndex)
        dismissPopups()
        if (c != null) listener?.onChar(c)
    }

    // -------------------------------------------------------------- preview
    private fun showPreview(key: PlacedKey) {
        dismissPreview()
        val tv = TextView(context)
        tv.text = key.displayLabel
        tv.gravity = Gravity.CENTER
        tv.setTextColor(theme.text)
        tv.textSize = 30f
        val bg = GradientDrawable()
        bg.setColor(theme.keyPressed)
        bg.cornerRadius = 8 * density
        tv.background = bg
        val w = max(key.rect.width(), 48 * density).toInt()
        val h = (key.rect.height() * 1.1f).toInt()
        val popup = PopupWindow(tv, w, h, false)
        popup.isClippingEnabled = false
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val x = loc[0] + key.rect.centerX() - w / 2f
        val y = loc[1] + key.rect.top - h - 6 * density
        popup.showAtLocation(this, Gravity.NO_GRAVITY, x.toInt(), y.toInt())
        previewPopup = popup
        previewText = tv
    }

    private fun dismissPreview() {
        previewPopup?.dismiss()
        previewPopup = null
        previewText = null
    }

    fun dismissPopups() {
        dismissPreview()
        altPopup?.dismiss()
        altPopup = null
        altViews = emptyList()
        altChars = emptyList()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        dismissPopups()
        super.onDetachedFromWindow()
    }
}
