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
/** The same page on the web (docs/privacy.html, served by GitHub Pages). */
private const val ONLINE_URL = "https://elyasomarcodes.github.io/My-first-App/privacy.html"

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
                    Uri.parse(ONLINE_URL)))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * The page carries a light and a dark palette (dark under a
     * prefers-color-scheme query, for the web copy). In the app the choice
     * must follow the app's own look, so the right block is picked here, the
     * page background is made transparent, and Vazirmatn is wired to the
     * bundled font files.
     */
    private fun themed(html: String): String {
        val darkOpen = "@media (prefers-color-scheme:dark){:root{"
        var out = if (Ui.isNight(this)) {
            // promote the dark palette to the default (it already follows
            // the light one, so it wins by order)
            html.replace(darkOpen, "@media all{:root{")
        } else {
            html.replace(darkOpen, "@media not all{:root{")
        }
        val css = """<style>
            @font-face{font-family:'Vazirmatn';font-weight:400;
              src:url('file:///android_res/font/vazirmatn_regular.ttf')}
            @font-face{font-family:'Vazirmatn';font-weight:700;
              src:url('file:///android_res/font/vazirmatn_bold.ttf')}
            body{background:transparent!important;padding:4px 12px 8px!important}
            </style>"""
        out = out.replaceFirst("</head>", "$css</head>")
        return out
    }
}
