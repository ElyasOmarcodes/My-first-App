package com.elyas.multiling

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Learns where this user's fingers actually land.
 *
 * Every touch keyboard has to deal with the same systematic error: the
 * digitiser reports the centroid of the contact patch, which sits below and
 * slightly behind the point the user believes they are pressing. Gboard
 * handles it with a "global level correction where all keys share one
 * parameter" (Sivek, *Spatial model personalization in Gboard*, 2022), then
 * refines per key.
 *
 * We do the global level only, and we LEARN it instead of guessing: a fixed
 * offset baked in by a developer who cannot measure this user's hands would
 * be as likely to hurt as help. Starting from zero means the keyboard behaves
 * exactly as before until it has real evidence, and the correction can only
 * ever move the touch point by a fraction of a key.
 */
class TouchCalibration(private val context: Context) {

    companion object {
        private const val KEY_DX = "touch_bias_dx"
        private const val KEY_DY = "touch_bias_dy"
        private const val KEY_N = "touch_bias_n"

        /** Below this many taps the estimate is noise, so stay at zero. */
        private const val MIN_SAMPLES = 300

        /** Only ever apply half of the measured drift. */
        private const val DAMPING = 0.5f

        /** Never move a touch more than this share of the key's half-size. */
        private const val MAX_SHIFT = 0.45f

        /** Samples decay so the model tracks a changing grip (or a new user). */
        private const val HORIZON = 4000f
    }

    private var dx = 0f
    private var dy = 0f
    private var n = 0f
    private var loaded = false
    private var unsaved = 0

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val p = prefs()
            dx = p.getFloat(KEY_DX, 0f)
            dy = p.getFloat(KEY_DY, 0f)
            n = p.getFloat(KEY_N, 0f)
        } catch (_: Exception) {
        }
    }

    /**
     * Record one tap: how far it landed from the centre of the key it hit,
     * as a fraction of that key's half-width and half-height so the estimate
     * survives a keyboard resize or a different screen.
     */
    fun record(offXFrac: Float, offYFrac: Float) {
        ensureLoaded()
        if (offXFrac.isNaN() || offYFrac.isNaN()) return
        // a tap that landed outside the key it was attributed to tells us
        // nothing about aim, only that the fallback snapped it somewhere
        if (offXFrac < -1f || offXFrac > 1f || offYFrac < -1f || offYFrac > 1f) return
        val w = if (n < HORIZON) n else HORIZON
        dx = (dx * w + offXFrac) / (w + 1f)
        dy = (dy * w + offYFrac) / (w + 1f)
        n = w + 1f
        if (++unsaved >= 50) save()
    }

    fun save() {
        if (!loaded || unsaved == 0) return
        unsaved = 0
        try {
            prefs().edit().putFloat(KEY_DX, dx).putFloat(KEY_DY, dy)
                .putFloat(KEY_N, n).apply()
        } catch (_: Exception) {
        }
    }

    /** Horizontal correction to subtract from a touch, in key half-widths. */
    fun shiftXFrac(): Float = correction(dx)

    /** Vertical correction to subtract from a touch, in key half-heights. */
    fun shiftYFrac(): Float = correction(dy)

    private fun correction(mean: Float): Float {
        ensureLoaded()
        if (n < MIN_SAMPLES) return 0f
        val v = mean * DAMPING
        return v.coerceIn(-MAX_SHIFT, MAX_SHIFT)
    }

    fun reset() {
        ensureLoaded()
        dx = 0f; dy = 0f; n = 0f; unsaved = 1
        save()
    }

    /** Taps recorded so far — shown in settings so the user can see it work. */
    fun samples(): Int {
        ensureLoaded()
        return n.toInt()
    }
}
