package com.elyas.multiling

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-page privacy policy. The page is the SAME PRIVACY.md that is hosted
 * online, bundled in assets and rendered offline — the app has no INTERNET
 * permission, so remote pages can't be loaded in-app; the button at the
 * bottom opens the hosted copy in the browser instead.
 */
class PrivacyActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.about_privacy)
        val d = resources.displayMetrics.density

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.loadUrl("file:///android_asset/privacy.html")
        root.addView(web, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val online = TextView(this)
        online.text = getString(R.string.privacy_online)
        online.textSize = 15f
        online.gravity = Gravity.CENTER
        online.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.setColor(0xFF1A73E8.toInt())
        bg.cornerRadius = 12 * d
        online.background = bg
        online.setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins((16 * d).toInt(), (10 * d).toInt(),
            (16 * d).toInt(), (14 * d).toInt())
        online.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse(AboutActivity.PRIVACY_URL)))
            } catch (_: Exception) {
            }
        }
        root.addView(online, lp)

        setContentView(root)
    }
}
