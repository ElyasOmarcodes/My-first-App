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
 * Custom keyboard view: draws the key grid, handles multi-touch typing
 * (rollover — a second finger can press before the first lifts, so fast
 * typing never drops letters), long-press popups with slide-to-select
 * alternates, key repeat, key preview and space-bar swipes.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onChar(text: String)
        fun onSpecial(code: Int)
        fun onLangSwipe(forward: Boolean)
        fun onSpaceLongPress()
        fun onSymLongPress()
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
        val BLUE = Theme(0xFF0D1B2A.toInt(), 0xFF1B3A5C.toInt(), 0xFF122A44.toInt(),
            0xFF2E5F94.toInt(), Color.WHITE, 0xFF9FC2E8.toInt(), 0xFF64B5F6.toInt())
        val GREEN = Theme(0xFF0F1A12.toInt(), 0xFF1E3A26.toInt(), 0xFF16291B.toInt(),
            0xFF346644.toInt(), Color.WHITE, 0xFFA5D6A7.toInt(), 0xFF66BB6A.toInt())

        fun themeByName(name: String): Theme = when (name) {
            "light" -> LIGHT
            "black" -> BLACK
            "blue" -> BLUE
            "green" -> GREEN
            else -> DARK
        }
    }

    var theme: Theme = DARK
        set(value) { field = value; invalidate() }

    // custom colors (used when [customColors] is on, else the theme is used)
    var customColors: Boolean = false
    var colKeyFill: Int = 0
    var colKeyFill2: Int = 0        // 0 = no gradient
    var colSpecialFill: Int = 0
    var colSpecialFill2: Int = 0
    var colTextColor: Int = 0
    var colHintColor: Int = 0

    private val keyFillColor get() = if (customColors) colKeyFill else theme.keyFill
    private val keyFillColor2 get() = if (customColors) colKeyFill2 else 0
    private val specialFillColor get() = if (customColors) colSpecialFill else theme.specialFill
    private val specialFillColor2 get() = if (customColors) colSpecialFill2 else 0
    private val labelColor get() = if (customColors) colTextColor else theme.text
    private val hintColorNow get() = if (customColors) colHintColor else theme.hint

    var keyHeightDp: Int = 52
    var arrowRowScale: Float = 1f
    var fontScale: Float = 1f
    var hintScale: Float = 1f
    var cornerRadiusDp: Int = 6
    var keyGapDp: Float = 1.5f
    var showHints: Boolean = true
    var showPreview: Boolean = true
    var keyBorder: Boolean = false
    var spaceSwipeEnabled: Boolean = true
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
        pointers.clear()
        dismissPopups()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    // -------------------------------------------------------------- paints
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // --------------------------------------------------- multi-touch state
    private class PointerState(
        val id: Int,
        var key: PlacedKey?,
        val downX: Float,
        val downY: Float
    ) {
        var committed = false      // already emitted (rollover flush)
        var longPressFired = false
        var spaceSwiped = false
        var swipeDir = 0
        var cancelled = false
    }

    private val pointers = HashMap<Int, PointerState>()
    private var longPressPointerId = -1
    private var repeatPointerId = -1

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val st = pointers[repeatPointerId] ?: return
            val key = st.key ?: return
            if (key.def.repeatable && !st.cancelled) {
                emit(key)
                handler.postDelayed(this, 50)
            }
        }
    }

    // -------------------------------------------------------------- popups
    private var previewPopup: PopupWindow? = null
    private var altPopup: PopupWindow? = null
    private var altViews: List<TextView> = emptyList()
    private var altChars: List<String> = emptyList()
    private var altIndex = 0
    private var altPopupLeftInView = 0f
    private var altPopupTopInView = 0f
    private var altCellWidth = 0f
    private var altCellHeight = 0f
    private var altCols = 1

    // slide-to-select grid menu (long-press on 123, language menu)
    private var gridPopup: PopupWindow? = null
    private var gridViews: List<TextView> = emptyList()
    private var gridCount = 0
    private var gridHandler: ((Int) -> Unit)? = null
    private var gridCols = 3
    private var gridIndex = -1
    private var gridLeftInView = 0f
    private var gridTopInView = 0f
    private var gridCellW = 0f
    private var gridCellH = 0f

    private fun isArrowRow(row: List<KeyDef>): Boolean =
        row.isNotEmpty() && row.all { it.code in Keys.ARROW_RIGHT..Keys.ARROW_UP }

    private fun rowHeightPx(row: List<KeyDef>): Float {
        val base = keyHeightDp * density
        return if (isArrowRow(row)) base * arrowRowScale else base
    }

    // ------------------------------------------------------------- measure
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var h = (paddingTop + paddingBottom).toFloat()
        for (row in rows) h += rowHeightPx(row)
        val height = max((keyHeightDp * density).toInt(), h.toInt())
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        placed = ArrayList()
        if (rows.isEmpty() || width == 0) return
        val gap = keyGapDp * density
        val sidePad = keyGapDp * density
        var y = paddingTop.toFloat()
        for ((ri, row) in rows.withIndex()) {
            val rowH = rowHeightPx(row)
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
        val radius = cornerRadiusDp * density
        val pressedKeys = HashSet<PlacedKey>()
        for (st in pointers.values) {
            if (!st.cancelled) st.key?.let { pressedKeys.add(it) }
        }
        for (pk in placed) {
            val key = pk.def
            val isPressed = pressedKeys.contains(pk)
            val isSpecialFill = key.code != 0 && key.code != Keys.SPACE
            // resolve fill colour (+ optional gradient second colour)
            var fill1: Int
            var fill2 = 0
            when {
                isPressed -> fill1 = theme.keyPressed
                key.code == Keys.SHIFT && shiftState > 0 -> fill1 = theme.accent
                isSpecialFill -> { fill1 = specialFillColor; fill2 = specialFillColor2 }
                else -> { fill1 = keyFillColor; fill2 = keyFillColor2 }
            }
            if (fill2 != 0 && !isPressed) {
                fillPaint.shader = android.graphics.LinearGradient(
                    pk.rect.left, pk.rect.top, pk.rect.left, pk.rect.bottom,
                    fill1, fill2, android.graphics.Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.shader = null
                fillPaint.color = fill1
            }
            canvas.drawRoundRect(pk.rect, radius, radius, fillPaint)
            fillPaint.shader = null
            if (keyBorder) {
                borderPaint.color = hintColorNow and 0x60FFFFFF
                canvas.drawRoundRect(pk.rect, radius, radius, borderPaint)
            }

            val cx = pk.rect.centerX()
            val textColor =
                if (key.code == Keys.SHIFT && shiftState == 2) theme.background else labelColor

            // draw a round-fill icon for glyph keys, else the text label
            val iconRes = iconForKey(pk)
            val iconDrawable = if (iconRes != 0) getIcon(iconRes) else null
            if (iconDrawable != null) {
                val size = (pk.rect.height() * 0.44f * fontScale).toInt()
                val icx = cx.toInt()
                val icy = pk.rect.centerY().toInt()
                iconDrawable.setBounds(
                    icx - size / 2, icy - size / 2, icx + size / 2, icy + size / 2
                )
                androidx.core.graphics.drawable.DrawableCompat.setTint(iconDrawable, textColor)
                iconDrawable.draw(canvas)
            } else {
                // main label — shrink to fit wide labels (menu keys etc.)
                val isSpecial = key.code != 0
                var base = pk.rect.height() * (if (isSpecial) 0.36f else 0.46f)
                textPaint.textSize = base * fontScale
                val maxW = pk.rect.width() * 0.9f
                val measured = textPaint.measureText(pk.displayLabel)
                if (measured > maxW && measured > 0) {
                    base *= maxW / measured
                    textPaint.textSize = base * fontScale
                }
                textPaint.color = textColor
                val cy = pk.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(pk.displayLabel, cx, cy, textPaint)
            }

            // hint (top corner): drawn icon or small text
            if (key.hintIcon == Keys.ICON_MIC) {
                drawMicIcon(
                    canvas,
                    pk.rect.right - 10 * density,
                    pk.rect.top + 10 * density,
                    pk.rect.height() * 0.16f
                )
            } else if (showHints && (key.code == 0 || key.hint != null)) {
                val hint = key.hint ?: key.shifted ?: key.alternates.firstOrNull()
                if (hint != null && (shiftState == 0 || key.code != 0)) {
                    hintPaint.textSize = pk.rect.height() * 0.24f * fontScale * hintScale
                    hintPaint.color = hintColorNow
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

    private val iconCache = HashMap<Int, android.graphics.drawable.Drawable?>()

    private fun getIcon(res: Int): android.graphics.drawable.Drawable? =
        iconCache.getOrPut(res) {
            try {
                androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(context, res)?.mutate()
            } catch (_: Exception) { null }
        }

    /** Map a special key's code to a round-fill icon (0 = draw text label). */
    private fun iconForKey(pk: PlacedKey): Int = when (pk.def.code) {
        Keys.SHIFT -> R.drawable.ic_key_shift
        Keys.DELETE -> R.drawable.ic_key_backspace
        Keys.ENTER -> if (pk.displayLabel == "↵") R.drawable.ic_key_enter else 0
        Keys.ARROW_UP -> R.drawable.ic_key_arrow_up
        Keys.ARROW_DOWN -> R.drawable.ic_key_arrow_down
        Keys.ARROW_LEFT -> R.drawable.ic_key_arrow_left
        Keys.ARROW_RIGHT -> R.drawable.ic_key_arrow_right
        else -> 0
    }

    /** Small microphone glyph drawn with primitives (no emoji). */
    private fun drawMicIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = theme.hint
        p.style = Paint.Style.FILL
        // capsule body
        canvas.drawRoundRect(
            RectF(cx - s * 0.32f, cy - s * 0.95f, cx + s * 0.32f, cy + s * 0.15f),
            s * 0.32f, s * 0.32f, p
        )
        // cradle arc
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f
        canvas.drawArc(
            RectF(cx - s * 0.62f, cy - s * 0.55f, cx + s * 0.62f, cy + s * 0.55f),
            20f, 140f, false, p
        )
        // stem + base
        canvas.drawLine(cx, cy + s * 0.55f, cx, cy + s * 0.85f, p)
        canvas.drawLine(cx - s * 0.35f, cy + s * 0.9f, cx + s * 0.35f, cy + s * 0.9f, p)
    }

    // --------------------------------------------------------------- touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                onPointerDown(id, event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    onPointerMove(id, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                onPointerUp(id)
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                pointers.clear()
                dismissPopups()
                invalidate()
            }
        }
        return true
    }

    private fun onPointerDown(id: Int, x: Float, y: Float) {
        // a tap outside an open (tap-mode) grid menu just dismisses it
        if (gridPopup != null && longPressPointerId == -1) {
            dismissGridPopup()
            pointers[id] = PointerState(id, null, x, y)
            return
        }
        // Rollover: a new finger flushes any still-held character keys so
        // fast typing never loses the previous letter.
        if (altPopup != null) {
            // a second tap while the alternates popup is open commits nothing
            dismissAltPopup()
        }
        for (st in pointers.values) {
            val k = st.key ?: continue
            if (!st.committed && !st.longPressFired && !st.cancelled &&
                k.def.code == 0 && !st.spaceSwiped
            ) {
                emit(k)
                st.committed = true
            }
        }
        if (longPressPointerId != -1) {
            handler.removeCallbacks(longPressRunnable)
            longPressPointerId = -1
            dismissPreview()
        }

        val key = keyAt(x, y)
        val st = PointerState(id, key, x, y)
        pointers[id] = st
        if (key == null) return
        invalidate()

        if (key.def.repeatable) {
            emit(key)
            st.committed = true
            repeatPointerId = id
            handler.postDelayed(repeatRunnable, 400)
        } else {
            longPressPointerId = id
            handler.postDelayed(longPressRunnable, longPressTimeout)
            if (showPreview && key.def.code == 0) showPreview(key)
        }
    }

    private fun onPointerMove(id: Int, x: Float, y: Float) {
        val st = pointers[id] ?: return
        val key = st.key ?: return
        if (st.cancelled || st.committed) return

        if (gridPopup != null && id == longPressPointerId) {
            updateGridSelection(x, y)
            return
        }
        if (altPopup != null && id == longPressPointerId) {
            updateAltSelection(x, y)
            return
        }
        if (key.def.code == Keys.SPACE && spaceSwipeEnabled) {
            val dx = x - st.downX
            if (abs(dx) > 40 * density) {
                st.spaceSwiped = true
                st.swipeDir = if (dx < 0) -1 else 1
                if (id == longPressPointerId) {
                    handler.removeCallbacks(longPressRunnable)
                    longPressPointerId = -1
                }
            }
            return
        }
        // Cancel only when the finger really leaves the key area — small
        // slides during fast typing must not drop the letter.
        val slack = keyHeightDp * density * 0.7f
        val dx = max(0f, max(key.rect.left - x, x - key.rect.right))
        val dy = max(0f, max(key.rect.top - y, y - key.rect.bottom))
        if (dx > slack || dy > slack) {
            st.cancelled = true
            if (id == longPressPointerId) {
                handler.removeCallbacks(longPressRunnable)
                longPressPointerId = -1
                dismissPreview()
            }
            if (id == repeatPointerId) {
                handler.removeCallbacks(repeatRunnable)
                repeatPointerId = -1
            }
            invalidate()
        }
    }

    private fun onPointerUp(id: Int) {
        val st = pointers.remove(id) ?: return
        if (id == longPressPointerId) {
            handler.removeCallbacks(longPressRunnable)
            longPressPointerId = -1
        }
        if (id == repeatPointerId) {
            handler.removeCallbacks(repeatRunnable)
            repeatPointerId = -1
        }
        dismissPreview()

        val key = st.key
        if (gridPopup != null && st.longPressFired) {
            commitGridSelection()
        } else if (altPopup != null && st.longPressFired) {
            commitAltSelection()
        } else if (key != null && !st.longPressFired && !st.committed && !st.cancelled) {
            if (key.def.code == Keys.SPACE && st.spaceSwiped) {
                listener?.onLangSwipe(st.swipeDir < 0)
            } else {
                emit(key)
            }
        }
        invalidate()
    }

    private fun cancelTimers() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        longPressPointerId = -1
        repeatPointerId = -1
    }

    private fun keyAt(x: Float, y: Float): PlacedKey? {
        for (pk in placed) {
            if (pk.rect.contains(x, y)) return pk
        }
        // be forgiving: snap touches in the gaps to the nearest key
        var best: PlacedKey? = null
        var bestDist = Float.MAX_VALUE
        for (pk in placed) {
            val dx = max(0f, max(pk.rect.left - x, x - pk.rect.right))
            val dy = max(0f, max(pk.rect.top - y, y - pk.rect.bottom))
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = pk }
        }
        return if (bestDist < (16 * density) * (16 * density)) best else null
    }

    private fun emit(key: PlacedKey) {
        if (key.def.code == 0) listener?.onChar(key.displayLabel)
        else listener?.onSpecial(key.def.code)
    }

    // ---------------------------------------------------------- long press
    private fun onLongPress() {
        val id = longPressPointerId
        longPressPointerId = -1
        val st = pointers[id] ?: return
        val key = st.key ?: return
        if (st.cancelled || st.committed || st.spaceSwiped) return

        if (key.def.code == Keys.SPACE) {
            st.longPressFired = true
            longPressPointerId = id // keep tracking this pointer for the menu
            dismissPreview()
            listener?.onSpaceLongPress()
            return
        }
        if (key.def.code == Keys.SYM) {
            st.longPressFired = true
            longPressPointerId = id // keep tracking this pointer for the grid
            dismissPreview()
            listener?.onSymLongPress()
            return
        }
        val chars = key.def.popupChars(shiftState > 0)
        if (chars.isEmpty()) return
        st.longPressFired = true
        longPressPointerId = id // keep tracking this pointer for the popup
        dismissPreview()
        showAltPopup(key, chars)
    }

    private fun showAltPopup(key: PlacedKey, chars: List<String>) {
        // multi-row grid popup, like classic multilingual keyboards:
        // slide over any cell (also up/down between rows) and release
        val cols = min(chars.size, 6)
        val rowsCount = (chars.size + cols - 1) / cols
        var cellW = max(key.rect.width(), 46 * density)
        cellW = min(cellW, (width - 8 * density) / cols)
        val cellH = min(key.rect.height(), keyHeightDp * density)

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bg = GradientDrawable()
        bg.setColor(theme.keyPressed)
        bg.cornerRadius = 8 * density
        container.background = bg
        val views = ArrayList<TextView>()
        var i = 0
        for (r in 0 until rowsCount) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutDirection = View.LAYOUT_DIRECTION_LTR
            for (c in 0 until cols) {
                if (i >= chars.size) break
                val tv = TextView(context)
                tv.text = chars[i]
                tv.gravity = Gravity.CENTER
                tv.setTextColor(theme.text)
                tv.textSize = if (chars[i].length > 2) 14f else 22f
                tv.maxLines = 1
                tv.layoutParams = LinearLayout.LayoutParams(cellW.toInt(), cellH.toInt())
                rowLayout.addView(tv)
                views.add(tv)
                i++
            }
            container.addView(rowLayout)
        }
        altViews = views
        altChars = chars
        altCellWidth = cellW
        altCellHeight = cellH
        altCols = cols
        val totalW = cellW * cols
        val totalH = cellH * rowsCount
        // first cell starts above the pressed key so the initial highlight
        // sits on the advertised hint character
        var left = key.rect.centerX() - cellW / 2
        left = min(max(4 * density, left), width - totalW - 4 * density)
        altPopupLeftInView = left
        val top = max(4 * density - totalH + cellH, key.rect.top - totalH - 8 * density)
        altPopupTopInView = top
        altIndex = 0
        highlightAlt()

        val popup = PopupWindow(container, totalW.toInt(), totalH.toInt(), false)
        popup.isClippingEnabled = false
        val loc = IntArray(2)
        getLocationInWindow(loc)
        popup.showAtLocation(
            this, Gravity.NO_GRAVITY,
            (loc[0] + left).toInt(), (loc[1] + top).toInt()
        )
        altPopup = popup
    }

    /**
     * Slide-to-select menu grid: while the finger that opened it is still
     * down, glide over an item and release to activate it (the [initial]
     * item is pre-highlighted, so releasing in place activates it).
     * The cells are also tappable, so the same grid works after the
     * finger has lifted (e.g. the language menu opened from this menu).
     */
    fun showGridMenu(labels: List<String>, initial: Int = 0, cols: Int = 3, onSelect: (Int) -> Unit) {
        dismissPopups()
        gridCols = cols
        gridHandler = onSelect
        val rowsCount = (labels.size + cols - 1) / cols
        gridCellW = (width * (if (cols == 1) 0.45f else 0.9f)) / cols
        gridCellH = keyHeightDp * density * (if (cols == 1) 0.72f else 1f)
        val totalW = gridCellW * cols
        val totalH = gridCellH * rowsCount

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bg = GradientDrawable()
        bg.setColor(theme.keyPressed)
        bg.cornerRadius = 10 * density
        container.background = bg

        val views = ArrayList<TextView>()
        var i = 0
        for (r in 0 until rowsCount) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutDirection = View.LAYOUT_DIRECTION_LTR
            for (c in 0 until cols) {
                if (i >= labels.size) break
                val idx = i
                val tv = TextView(context)
                tv.text = labels[i]
                tv.gravity = Gravity.CENTER
                tv.setTextColor(theme.text)
                tv.textSize = if (cols == 1) 14f else 17f
                tv.layoutParams =
                    LinearLayout.LayoutParams(gridCellW.toInt(), gridCellH.toInt())
                tv.setOnClickListener {
                    val h = gridHandler
                    dismissGridPopup()
                    h?.invoke(idx)
                }
                rowLayout.addView(tv)
                views.add(tv)
                i++
            }
            container.addView(rowLayout)
        }
        gridViews = views
        gridCount = labels.size
        gridIndex = if (initial in labels.indices) initial else -1
        highlightGrid()

        gridLeftInView = (width - totalW) / 2
        gridTopInView = max(4 * density, height - totalH - (keyHeightDp * density) * 2.2f)

        val popup = PopupWindow(container, totalW.toInt(), totalH.toInt(), false)
        popup.isClippingEnabled = false
        popup.isTouchable = true
        val loc = IntArray(2)
        getLocationInWindow(loc)
        popup.showAtLocation(
            this, Gravity.NO_GRAVITY,
            (loc[0] + gridLeftInView).toInt(), (loc[1] + gridTopInView).toInt()
        )
        gridPopup = popup
    }

    private fun updateGridSelection(x: Float, y: Float) {
        if (gridCount == 0) return
        val col = ((x - gridLeftInView) / gridCellW).toInt()
        val row = ((y - gridTopInView) / gridCellH).toInt()
        if (x >= gridLeftInView && y >= gridTopInView && col in 0 until gridCols && row >= 0) {
            val idx = row * gridCols + col
            // outside the item range keeps the current selection
            if (idx in 0 until gridCount) gridIndex = idx
        }
        highlightGrid()
    }

    private fun highlightGrid() {
        for ((i, tv) in gridViews.withIndex()) {
            if (i == gridIndex) {
                val d = GradientDrawable()
                d.setColor(theme.accent)
                d.cornerRadius = 10 * density
                tv.background = d
            } else {
                tv.background = null
            }
        }
    }

    private fun commitGridSelection() {
        val idx = gridIndex
        val h = gridHandler
        dismissGridPopup()
        if (idx >= 0) h?.invoke(idx)
    }

    private fun dismissGridPopup() {
        gridPopup?.dismiss()
        gridPopup = null
        gridViews = emptyList()
        gridCount = 0
        gridIndex = -1
        gridHandler = null
    }

    private fun updateAltSelection(x: Float, y: Float) {
        if (altChars.isEmpty()) return
        val rowsCount = (altChars.size + altCols - 1) / altCols
        val col = min(max(0, ((x - altPopupLeftInView) / altCellWidth).toInt()), altCols - 1)
        val row = min(max(0, ((y - altPopupTopInView) / altCellHeight).toInt()), rowsCount - 1)
        altIndex = min(row * altCols + col, altChars.size - 1)
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
        dismissAltPopup()
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
    }

    private fun dismissPreview() {
        previewPopup?.dismiss()
        previewPopup = null
    }

    private fun dismissAltPopup() {
        altPopup?.dismiss()
        altPopup = null
        altViews = emptyList()
        altChars = emptyList()
    }

    fun dismissPopups() {
        dismissPreview()
        dismissAltPopup()
        dismissGridPopup()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        dismissPopups()
        super.onDetachedFromWindow()
    }
}
