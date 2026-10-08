package com.elyas.multiling

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

/**
 * The privacy policy, read offline under a large collapsing app bar.
 *
 * It is the same PRIVACY.md that is published online, bundled in assets —
 * the app holds no INTERNET permission, so the hosted copy opens in the
 * browser instead. The page is restyled with the app's own colours so it
 * reads correctly in both light and dark.
 */
class PrivacyActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_privacy)
        findViewById<CollapsingToolbarLayout>(R.id.collapsing).title = getString(R.string.about_privacy)
        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        Ui.padForBars(findViewById(R.id.scroll), top = false, bottom = true)

        val web = findViewById<WebView>(R.id.web)
        web.settings.javaScriptEnabled = false
        web.setBackgroundColor(Color.TRANSPARENT)
        web.isVerticalScrollBarEnabled = false
        val html = try {
            assets.open("privacy.html").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
        web.loadDataWithBaseURL("file:///android_asset/", themed(html), "text/html", "utf-8", null)

        findViewById<android.view.View>(R.id.btn_online).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/ElyasOmarcodes/My-first-App/blob/main/PRIVACY.md")))
            } catch (_: Exception) {
            }
        }
    }

    /** Append a stylesheet in the current theme's colours; it wins by order. */
    private fun themed(html: String): String {
        fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)
        val text = hex(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface))
        val muted = hex(Ui.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        val link = hex(Ui.attr(this, androidx.appcompat.R.attr.colorPrimary))
        val line = hex(Ui.color(this, R.color.card_stroke))
        val css = """<style>
            html,body{background:transparent!important;color:$text!important;
              font-family:sans-serif;line-height:1.7;margin:0;padding:8px 10px;}
            h1,h2,h3{color:$text!important;line-height:1.35}
            p,li,td{color:$text!important}
            small,.muted,blockquote{color:$muted!important}
            a{color:$link!important}
            hr,table,td,th{border-color:$line!important}
            </style>"""
        return if (html.contains("</head>", ignoreCase = true))
            html.replaceFirst("</head>", "$css</head>", ignoreCase = true)
        else css + html
    }
}
