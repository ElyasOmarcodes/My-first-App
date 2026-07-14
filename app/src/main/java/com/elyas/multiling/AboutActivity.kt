package com.elyas.multiling

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** «د پروګرام په اړه» — app, publisher (Hindukush Voice) and policy info. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.about_title)
        val d = resources.displayMetrics.density

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding((24 * d).toInt(), (28 * d).toInt(), (24 * d).toInt(), (28 * d).toInt())

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_hindukush)
        logo.adjustViewBounds = true
        root.addView(logo, LinearLayout.LayoutParams(
            (220 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))

        fun text(t: CharSequence, sizeSp: Float, bold: Boolean = false,
                 topDp: Int = 8, colorAttr: Int = 0): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = sizeSp
            if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            if (colorAttr != 0) tv.setTextColor(colorAttr)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (topDp * d).toInt()
            lp.gravity = Gravity.CENTER_HORIZONTAL
            root.addView(tv, lp)
            return tv
        }

        text("هندوکش کیبورډ", 24f, bold = true, topDp = 14)
        text(getString(R.string.about_version, versionName()), 13f, topDp = 2)
        text(getString(R.string.about_publisher), 17f, bold = true, topDp = 22)
        text(getString(R.string.about_desc), 14f, topDp = 8)

        fun linkRow(label: String, url: String) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 15f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(0xFF4FA3FF.toInt())
            val bg = GradientDrawable()
            bg.setColor(0x144FA3FF)
            bg.cornerRadius = 12 * d
            tv.background = bg
            tv.setPadding((20 * d).toInt(), (12 * d).toInt(),
                (20 * d).toInt(), (12 * d).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (10 * d).toInt()
            tv.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            root.addView(tv, lp)
        }

        text(getString(R.string.about_sites), 15f, bold = true, topDp = 24)
        linkRow("پښتو — hindukushpa.com", "https://hindukushpa.com")
        linkRow("دري — hindokosh.com", "https://hindokosh.com/")
        linkRow("English — hindukushen.com", "https://hindukushen.com/")

        // privacy policy: full text offline, in a dialog
        val privacy = TextView(this)
        privacy.text = getString(R.string.about_privacy)
        privacy.textSize = 15f
        privacy.gravity = Gravity.CENTER
        privacy.setTextColor(0xFF4FA3FF.toInt())
        privacy.setPadding(0, (26 * d).toInt(), 0, 0)
        privacy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about_privacy)
                .setMessage(R.string.privacy_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        root.addView(privacy)

        text("© 2026 هندوکش غږ — Hindukush Voice", 12.5f, topDp = 30,
            colorAttr = Color.GRAY)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }
}
