package com.elyas.multiling

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Opening screen.
 *
 * The brand mark lands first with a slight overshoot, the wordmark and
 * tagline follow it up, and a thin progress line runs underneath so the
 * wait reads as loading rather than as a pause. Timings are short on
 * purpose: this is a doorway, not a destination.
 */
class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val root = FrameLayout(this)
        root.setBackgroundResource(R.drawable.ds_bg_app)

        // two colour orbs, drifting in behind the mark
        val orbA = View(this)
        orbA.setBackgroundResource(R.drawable.ds_glow_a)
        root.addView(orbA, FrameLayout.LayoutParams(
            dp(460f), dp(460f), Gravity.CENTER).also { it.topMargin = dp(-170f) })
        val orbB = View(this)
        orbB.setBackgroundResource(R.drawable.ds_glow_c)
        root.addView(orbB, FrameLayout.LayoutParams(
            dp(420f), dp(420f), Gravity.CENTER).also { it.topMargin = dp(190f) })

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        // ---- brand mark on a white plate, so the logo keeps its contrast
        val plate = LinearLayout(this)
        plate.gravity = Gravity.CENTER
        val plateBg = GradientDrawable()
        plateBg.setColor(Color.WHITE)
        plateBg.cornerRadius = 30 * d
        plate.background = plateBg
        plate.elevation = 18 * d
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_hindukush)
        logo.adjustViewBounds = true
        plate.addView(logo, LinearLayout.LayoutParams(
            dp(196f), LinearLayout.LayoutParams.WRAP_CONTENT))
        column.addView(plate, LinearLayout.LayoutParams(dp(250f), dp(122f)))

        fun text(t: String, sizeSp: Float, colorRes: Int, bold: Boolean, topDp: Float): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = sizeSp
            tv.setTextColor(Ui.color(this, colorRes))
            if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(topDp)
            column.addView(tv, lp)
            return tv
        }

        val title = text(getString(R.string.app_name), 27f, R.color.text_primary, true, 28f)
        title.letterSpacing = 0.01f
        val sub = text(getString(R.string.splash_sub), 15f, R.color.text_secondary, false, 7f)

        // ---- progress hairline: a brand-coloured bar sweeping a dim track
        val track = FrameLayout(this)
        val trackBg = GradientDrawable()
        trackBg.setColor(Ui.color(this, R.color.glass_fill))
        trackBg.cornerRadius = 2 * d
        track.background = trackBg
        val fill = View(this)
        fill.setBackgroundResource(R.drawable.ds_brand)
        track.addView(fill, FrameLayout.LayoutParams(0, dp(3f)))
        column.addView(track, LinearLayout.LayoutParams(dp(128f), dp(3f)).also {
            it.topMargin = dp(34f)
        })

        val ver = text(versionLabel(), 12f, R.color.text_tertiary, false, 22f)

        setContentView(root)

        // ---- entrance
        for ((i, orb) in listOf(orbA, orbB).withIndex()) {
            orb.alpha = 0f
            orb.animate().alpha(1f).setStartDelay(i * 120L).setDuration(900).start()
        }
        plate.scaleX = 0.74f; plate.scaleY = 0.74f; plate.alpha = 0f
        plate.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(560).setInterpolator(OvershootInterpolator(1.15f)).start()
        for ((i, v) in listOf<View>(title, sub, ver).withIndex()) {
            v.alpha = 0f
            v.translationY = 16 * d
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(250L + i * 110L).setDuration(430).start()
        }
        // the bar fills over the dwell time, so it finishes as we navigate
        track.post {
            val w = track.width
            val anim = android.animation.ValueAnimator.ofInt(0, w)
            anim.duration = 1120
            anim.startDelay = 180
            anim.addUpdateListener { va ->
                fill.layoutParams.width = va.animatedValue as Int
                fill.requestLayout()
            }
            anim.start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1420)
    }

    private fun versionLabel(): String = try {
        "v" + packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: Exception) { "" }
}
