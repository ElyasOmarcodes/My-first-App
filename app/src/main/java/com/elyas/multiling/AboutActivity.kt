package com.elyas.multiling

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * About page: who made this, where to find it, and how to reach the people
 * behind it. Built from [Ui] so it carries the same surfaces and spacing as
 * the rest of the app.
 */
class AboutActivity : AppCompatActivity() {

    companion object {
        /** No spaces: the number has to stay a single LTR run inside RTL text. */
        private const val WHATSAPP_NUMBER = "93765893297"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = Ui.page(this, getString(R.string.about_title))
        val c = this

        // ---------------------------------------------------- identity card
        val id = Ui.card(c)
        id.gravity = Gravity.CENTER_HORIZONTAL
        id.setPadding(Ui.dp(c, 20f), Ui.dp(c, 26f), Ui.dp(c, 20f), Ui.dp(c, 24f))

        val plate = LinearLayout(c)
        plate.gravity = Gravity.CENTER
        val plateBg = android.graphics.drawable.GradientDrawable()
        plateBg.setColor(android.graphics.Color.WHITE)
        plateBg.cornerRadius = 24f * resources.displayMetrics.density
        plate.background = plateBg
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_hindukush)
        logo.adjustViewBounds = true
        plate.addView(logo, LinearLayout.LayoutParams(
            Ui.dp(c, 164f), LinearLayout.LayoutParams.WRAP_CONTENT))
        id.addView(plate, LinearLayout.LayoutParams(Ui.dp(c, 208f), Ui.dp(c, 104f)))

        val name = TextView(c)
        name.text = getString(R.string.app_name)
        name.textSize = 23f
        name.setTypeface(name.typeface, Typeface.BOLD)
        name.setTextColor(Ui.color(c, R.color.text_primary))
        name.gravity = Gravity.CENTER
        id.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 16f) })

        // version as a chip — a fact, not a sentence
        val ver = TextView(c)
        ver.text = getString(R.string.about_version, versionName())
        ver.textSize = 12.5f
        ver.setTextColor(Ui.color(c, R.color.brand_3))
        ver.gravity = Gravity.CENTER
        ver.setBackgroundResource(R.drawable.ds_chip)
        ver.setPadding(Ui.dp(c, 12f), Ui.dp(c, 5f), Ui.dp(c, 12f), Ui.dp(c, 5f))
        id.addView(ver, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 10f); it.gravity = Gravity.CENTER_HORIZONTAL })

        Ui.addCard(page, id, 8f)

        // ------------------------------------------------------- publisher
        val pub = Ui.card(c)
        pub.setPadding(Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f))
        val pubTitle = TextView(c)
        pubTitle.text = getString(R.string.about_publisher)
        pubTitle.textSize = 16.5f
        pubTitle.setTypeface(pubTitle.typeface, Typeface.BOLD)
        pubTitle.setTextColor(Ui.color(c, R.color.c_amber))
        pub.addView(pubTitle)
        val desc = Ui.body(c, getString(R.string.about_desc))
        desc.setPadding(0, Ui.dp(c, 8f), 0, 0)
        pub.addView(desc)
        Ui.addCard(page, pub)

        // ----------------------------------------------------------- sites
        page.addView(Ui.sectionHeader(c, getString(R.string.about_sites)))
        val sites = Ui.card(c)
        val siteList = listOf(
            Triple(getString(R.string.site_ps), "https://hindukushpa.com", R.color.c_teal),
            Triple(getString(R.string.site_fa), "https://hindokosh.com/", R.color.c_violet),
            Triple(getString(R.string.site_en), "https://hindukushen.com/", R.color.c_sky)
        )
        for ((i, s) in siteList.withIndex()) {
            if (i > 0) Ui.addDivider(sites)
            sites.addView(
                Ui.row(c, R.drawable.ic_key_info, Ui.color(c, s.third), s.first, s.second) {
                    open(s.second)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }
        Ui.addCard(page, sites, 4f)

        // --------------------------------------------------------- contact
        page.addView(Ui.sectionHeader(c, getString(R.string.contact_manager)))
        val contact = Ui.card(c)
        contact.setPadding(Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f), Ui.dp(c, 18f))

        val person = LinearLayout(c)
        person.orientation = LinearLayout.HORIZONTAL
        person.gravity = Gravity.CENTER_VERTICAL
        person.addView(
            Ui.badge(c, R.drawable.ic_key_info, Ui.color(c, R.color.c_mint)),
            LinearLayout.LayoutParams(Ui.dp(c, 44f), Ui.dp(c, 44f))
                .also { it.marginEnd = Ui.dp(c, 12f) })
        val who = LinearLayout(c)
        who.orientation = LinearLayout.VERTICAL
        val pName = TextView(c)
        pName.text = getString(R.string.contact_manager_name)
        pName.textSize = 16f
        pName.setTextColor(Ui.color(c, R.color.text_primary))
        who.addView(pName)
        val pNum = TextView(c)
        pNum.text = getString(R.string.contact_whatsapp_num)
        pNum.textSize = 14f
        pNum.setTextColor(Ui.color(c, R.color.text_secondary))
        // force the number to render left-to-right inside an RTL page
        pNum.textDirection = View.TEXT_DIRECTION_LTR
        who.addView(pNum)
        person.addView(who, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        contact.addView(person)

        contact.addView(
            Ui.primaryButton(c, getString(R.string.contact_whatsapp_btn)) { openWhatsApp() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = Ui.dp(c, 16f) })
        Ui.addCard(page, contact, 4f)

        // --------------------------------------------------------- privacy
        val legal = Ui.card(c)
        legal.addView(
            Ui.row(c, R.drawable.ic_cat_backup, Ui.color(c, R.color.c_indigo),
                getString(R.string.about_privacy)) {
                startActivity(Intent(c, PrivacyActivity::class.java))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        Ui.addCard(page, legal)

        val copy = TextView(c)
        copy.text = getString(R.string.about_copyright)
        copy.textSize = 12.5f
        copy.setTextColor(Ui.color(c, R.color.text_tertiary))
        copy.gravity = Gravity.CENTER
        page.addView(copy, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 26f) })
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    /** wa.me opens the chat in WhatsApp or WhatsApp Business, whichever is installed. */
    private fun openWhatsApp() {
        open("https://wa.me/$WHATSAPP_NUMBER")
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }
}
