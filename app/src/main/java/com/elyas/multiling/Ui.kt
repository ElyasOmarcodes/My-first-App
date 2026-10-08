package com.elyas.multiling

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Small helpers shared by every screen. Layout lives in XML now; this is only
 * the glue XML cannot express — insets, tonal colours, digits, motion.
 */
object Ui {

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun color(c: Context, @ColorRes res: Int): Int = ContextCompat.getColor(c, res)

    fun attr(c: Context, @AttrRes a: Int): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(a, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(c, tv.resourceId) else tv.data
    }

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** A tonal pair: soft container colour and the strong colour drawn on it. */
    enum class Tone(@ColorRes val bg: Int, @ColorRes val fg: Int) {
        VIOLET(R.color.tone_violet_bg, R.color.tone_violet_fg),
        INDIGO(R.color.tone_indigo_bg, R.color.tone_indigo_fg),
        BLUE(R.color.tone_blue_bg, R.color.tone_blue_fg),
        CYAN(R.color.tone_cyan_bg, R.color.tone_cyan_fg),
        TEAL(R.color.tone_teal_bg, R.color.tone_teal_fg),
        GREEN(R.color.tone_green_bg, R.color.tone_green_fg),
        AMBER(R.color.tone_amber_bg, R.color.tone_amber_fg),
        ORANGE(R.color.tone_orange_bg, R.color.tone_orange_fg),
        ROSE(R.color.tone_rose_bg, R.color.tone_rose_fg),
        PINK(R.color.tone_pink_bg, R.color.tone_pink_fg)
    }

    /** Paint a tonal icon container (a bg_tone frame holding an ImageView). */
    fun tone(container: View, icon: ImageView?, t: Tone) {
        val c = container.context
        ViewCompat.setBackgroundTintList(container, ColorStateList.valueOf(color(c, t.bg)))
        icon?.imageTintList = ColorStateList.valueOf(color(c, t.fg))
    }

    /**
     * Edge to edge: content runs behind the system bars, with bar icons dark
     * on the light theme and light on the dark one.
     */
    fun edgeToEdge(a: Activity) {
        WindowCompat.setDecorFitsSystemWindows(a.window, false)
        val light = !isNight(a)
        WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /**
     * Pad [v] by the system bars it would otherwise sit under, on top of the
     * padding it already declares in XML.
     */
    fun padForBars(v: View, top: Boolean, bottom: Boolean, extraBottom: Int = 0) {
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                l, t + (if (top) bars.top else 0),
                r, b + (if (bottom) bars.bottom else 0) + extraBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(v)
    }

    /** Lift [v] above the navigation bar using its bottom margin. */
    fun marginForNavBar(v: View, base: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = base + bars.bottom
                view.layoutParams = it
            }
            insets
        }
        ViewCompat.requestApplyInsets(v)
    }

    /** Numbers in the UI language's own digits: ۱۲۳ for Pashto/Farsi. */
    fun digits(c: Context, n: Int): String {
        val s = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)
        if (AppLocale.current(c) == AppLocale.EN) return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(
            when (ch) {
                in '0'..'9' -> '۰' + (ch - '0')
                ',' -> '٬'
                else -> ch
            }
        )
        return sb.toString()
    }

    /** Gentle entrance: fade in while rising a few dp, staggered by index. */
    fun rise(v: View, index: Int = 0) {
        v.alpha = 0f
        v.translationY = dp(v.context, 14f).toFloat()
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(30L + index * 45L).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    fun padBottom(v: View, px: Int) = v.updatePadding(bottom = px)
}
