package com.elyas.multiling

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** About tab: identity, the three sites, the manager's contact, privacy. */
class AboutFragment : Fragment(R.layout.frag_about) {

    companion object {
        /** No spaces: the number must stay a single LTR run inside RTL text. */
        private const val WHATSAPP_NUMBER = "93765893297"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        Ui.padForBars(view.findViewById(R.id.scroll), top = true, bottom = true)

        val version = try {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        view.findViewById<TextView>(R.id.version).text = getString(R.string.about_version, version)

        val sites = view.findViewById<LinearLayout>(R.id.sites)
        for ((label, url, tone) in listOf(
            Triple(R.string.site_ps, "https://hindukushpa.com", Ui.Tone.TEAL),
            Triple(R.string.site_fa, "https://hindokosh.com/", Ui.Tone.VIOLET),
            Triple(R.string.site_en, "https://hindukushen.com/", Ui.Tone.BLUE)
        )) {
            Rows.addNav(sites, R.drawable.ic_m_globe, tone, getString(label),
                url.removePrefix("https://").trimEnd('/'), R.drawable.ic_m_open) { open(url) }
        }

        Ui.tone(view.findViewById(R.id.person_box), view.findViewById(R.id.person_icon), Ui.Tone.GREEN)
        view.findViewById<View>(R.id.btn_whatsapp).setOnClickListener {
            // wa.me opens the chat in WhatsApp or WhatsApp Business
            open("https://wa.me/$WHATSAPP_NUMBER")
        }

        Rows.bindNav(view.findViewById(R.id.row_privacy), R.drawable.ic_m_shield, Ui.Tone.INDIGO,
            getString(R.string.about_privacy), null) {
            startActivity(Intent(c, PrivacyActivity::class.java))
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}
