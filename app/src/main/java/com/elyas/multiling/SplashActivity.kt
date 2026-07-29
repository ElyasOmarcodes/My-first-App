package com.elyas.multiling

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Branded launch screen: Hindukush Voice motif, app name, soft fade-in. */
class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF101A33.toInt(), 0xFF1B2E55.toInt())
        )
        root.background = bg

        // logo card (Hindukush wordmark)
        val card = LinearLayout(this)
        card.gravity = Gravity.CENTER
        val cardBg = GradientDrawable()
        cardBg.setColor(Color.WHITE)
        cardBg.cornerRadius = 26 * density
        card.background = cardBg
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_hindukush)
        logo.adjustViewBounds = true
        card.addView(
            logo,
            LinearLayout.LayoutParams(
                (200 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(card, LinearLayout.LayoutParams(
            (248 * density).toInt(), (120 * density).toInt()))

        fun text(t: String, sizeSp: Float, color: Int, bold: Boolean, topDp: Int): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = sizeSp
            tv.setTextColor(color)
            if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (topDp * density).toInt()
            root.addView(tv, lp)
            return tv
        }

        val title = text(getString(R.string.app_name), 26f, Color.WHITE, true, 26)
        val sub = text(getString(R.string.splash_sub), 15f, 0xFF9FB4DC.toInt(), false, 6)
        val ver = text(versionLabel(), 12f, 0x66FFFFFF, false, 40)

        setContentView(root)

        // soft entrance
        card.scaleX = 0.72f; card.scaleY = 0.72f; card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(520).setInterpolator(
                android.view.animation.OvershootInterpolator(1.1f)).start()
        for ((i, v) in listOf(title, sub, ver).withIndex()) {
            v.alpha = 0f
            v.translationY = 14 * density
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(260L + i * 120L).setDuration(420).start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1250)
    }

    private fun versionLabel(): String = try {
        "v" + packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: Exception) { "" }
}
