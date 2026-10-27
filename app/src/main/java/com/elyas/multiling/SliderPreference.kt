package com.elyas.multiling

import android.content.Context
import android.content.res.TypedArray
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider

/**
 * An integer setting on a Material slider — the replacement for the stock
 * SeekBarPreference. Reads the same XML attributes (`android:max`,
 * `app:min`, `android:defaultValue`) so the preference files barely change.
 *
 * While dragging, the value shows in a floating bubble and the pill next to
 * the title, each step ticks the haptics lightly, and the value is saved
 * when the finger lifts — so the live keyboard preview redraws once instead
 * of on every step.
 */
class SliderPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    companion object {
        private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
        private const val NS_APP = "http://schemas.android.com/apk/res-auto"

        /** What each slider measures, for the value pill. */
        private val UNITS = mapOf(
            "font_scale" to "%", "hint_scale" to "%", "arrow_height" to "%",
            "sound_vol" to "%", "vibrate_ms" to "ms"
        )
    }

    private val min: Int = attrs?.getAttributeIntValue(NS_APP, "min", 0) ?: 0
    private val max: Int = maxOf(min + 1, attrs?.getAttributeIntValue(NS_ANDROID, "max", 100) ?: 100)
    private var value: Int = min
    private var lastTick = 0L

    init {
        layoutResource = R.layout.pref_slider
        // the row itself is not a button; the slider takes the touches
        isSelectable = false
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, min)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt((defaultValue as? Int) ?: min).coerceIn(min, max)
    }

    private fun label(v: Int): String = Ui.digits(context, v) + (UNITS[key] ?: "")

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val slider = holder.findViewById(R.id.slider) as? Slider ?: return
        val pill = holder.findViewById(R.id.slider_value) as? TextView

        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = min.toFloat()
        slider.valueTo = max.toFloat()
        slider.stepSize = 1f
        slider.value = value.toFloat()
        slider.isEnabled = isEnabled
        slider.setLabelFormatter { label(it.toInt()) }
        pill?.text = label(value)

        var tracking = false
        slider.addOnChangeListener { s, v, fromUser ->
            pill?.text = label(v.toInt())
            if (!fromUser) return@addOnChangeListener
            val now = SystemClock.uptimeMillis()
            if (now - lastTick > 35) {
                lastTick = now
                s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            // keyboard / accessibility adjustments have no touch to end
            if (!tracking) commit(v.toInt(), s)
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) { tracking = true }
            override fun onStopTrackingTouch(s: Slider) {
                tracking = false
                commit(s.value.toInt(), s)
            }
        })
    }

    private fun commit(v: Int, s: Slider) {
        if (v == value) return
        if (callChangeListener(v)) {
            value = v
            persistInt(v)
        } else {
            s.value = value.toFloat()
        }
    }
}
