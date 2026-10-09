package com.elyas.multiling

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A frame that turns horizontal swipes into page turns. Once a drag is
 * clearly sideways it takes the gesture away from the children, so the
 * tile or button under the finger gets a cancel instead of a click.
 */
class SwipeFrame @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Called with +1 for "forward in reading order", -1 for back. */
    var onSwipe: ((Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; dragging = false
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.5f) dragging = true
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        vt?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; return true }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                if (abs(dx) > width / 6f || abs(vx) > 800) {
                    val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
                    val right = dx > 0
                    // in RTL, moving the page to the right reveals the next one
                    onSwipe?.invoke(if (right == rtl) 1 else -1)
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
