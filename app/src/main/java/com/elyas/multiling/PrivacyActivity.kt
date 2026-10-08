package com.elyas.multiling

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The privacy policy, read offline.
 *
 * It is the same PRIVACY.md that is published online, bundled in assets —
 * the app holds no INTERNET permission, so a remote page cannot be loaded
 * in-app; the button at the bottom hands the hosted copy to the browser.
 *
 * The page is a WebView, so it cannot be built from [Ui] like the other
 * screens. Instead it is dropped into the same chrome: the app background,
 * the same top bar, and a transparent WebView so the gradient shows through.
 */
class PrivacyActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        val c = this

        val root = FrameLayout(c)
        root.setBackgroundResource(R.drawable.ds_bg_app)

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL

        val bar = Ui.topBar(c, getString(R.string.about_privacy)) {
            onBackPressedDispatcher.onBackPressed()
        }
        column.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 60f)))

        val web = WebView(c)
        web.settings.javaScriptEnabled = false
        web.setBackgroundColor(Color.TRANSPARENT)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.loadUrl("file:///android_asset/privacy.html")
        column.addView(web, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(c)
        footer.orientation = LinearLayout.VERTICAL
        val g = resources.getDimensionPixelSize(R.dimen.gutter)
        footer.setPadding(g, Ui.dp(c, 10f), g, Ui.dp(c, 14f))
        footer.addView(
            Ui.ghostButton(c, getString(R.string.privacy_online)) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/ElyasOmarcodes/My-first-App/blob/main/PRIVACY.md")))
                } catch (_: Exception) {
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        column.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bar.setPadding(0, sys.top, 0, 0)
            bar.layoutParams.height = Ui.dp(c, 60f) + sys.top
            bar.requestLayout()
            footer.setPadding(g, Ui.dp(c, 10f), g, Ui.dp(c, 14f) + sys.bottom)
            insets
        }
        setContentView(root)
    }
}
