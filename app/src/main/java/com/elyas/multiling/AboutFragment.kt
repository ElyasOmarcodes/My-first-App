package com.elyas.multiling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

/**
 * About tab: the developer's profile — photo in a colour ring, name and
 * title, a short bio, the platforms he builds for, his teaching, and contact
 * cards (tap opens the app, long-press copies) — followed by the app card.
 */
class AboutFragment : Fragment(R.layout.frag_about) {

    companion object {
        /** No spaces: the number must stay a single LTR run inside RTL text. */
        private const val WHATSAPP = "93766465848"
        private const val TELEGRAM = "Elyas_Omar"
        private const val EMAIL = "ElyasOmar100@gmail.com"
    }

    private var ringSpin: android.animation.ObjectAnimator? = null

    private fun spin(on: Boolean) {
        val a = ringSpin ?: return
        if (on) { if (a.isPaused) a.resume() else if (!a.isStarted) a.start() } else a.pause()
    }

    override fun onResume() { super.onResume(); spin(!isHidden) }
    override fun onPause() { spin(false); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); spin(!hidden) }
    override fun onDestroyView() { ringSpin?.cancel(); ringSpin = null; super.onDestroyView() }

    private class Contact(
        val icon: Int, val tone: Ui.Tone, val title: Int,
        val shown: String, val copy: String, val url: String
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        Ui.padForBars(view.findViewById(R.id.scroll), top = false, bottom = true)
        Ui.padForBars(view.findViewById(R.id.profile_head), top = true, bottom = false)

        // the colour ring: a sweep through the app's accent hues
        val ring = view.findViewById<View>(R.id.avatar_ring)
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = intArrayOf(
                0xFFF59E0B.toInt(), 0xFFEF4470.toInt(), 0xFF8B5CF6.toInt(),
                0xFF3B82F6.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt()
            )
        }
        // the ring turns slowly, like a live status ring (only while shown)
        ringSpin = android.animation.ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f).apply {
            duration = 14000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        // the watermark cap must stay inside the rounded corners
        view.findViewById<View>(R.id.teacher_card).clipToOutline = true

        section(view.findViewById(R.id.sec_builds), R.drawable.ic_m_devices, Ui.Tone.VIOLET, R.string.dev_builds_for)
        section(view.findViewById(R.id.sec_teach), R.drawable.ic_m_school, Ui.Tone.AMBER, R.string.dev_teaches)
        section(view.findViewById(R.id.sec_contact), R.drawable.ic_m_call, Ui.Tone.ROSE, R.string.dev_contact)
        section(view.findViewById(R.id.sec_app), R.drawable.ic_m_keyboard, Ui.Tone.BLUE, R.string.about_app)

        // platforms
        val plats = view.findViewById<LinearLayout>(R.id.platforms)
        val inf = LayoutInflater.from(c)
        val list = listOf(
            Triple(R.drawable.ic_m_android, Ui.Tone.GREEN, "Android"),
            Triple(R.drawable.ic_m_phone, Ui.Tone.BLUE, "iOS"),
            Triple(R.drawable.ic_m_desktop, Ui.Tone.CYAN, "Windows")
        )
        for ((i, p) in list.withIndex()) {
            val (icon, tone, name) = p
            val card = inf.inflate(R.layout.item_platform, plats, false) as MaterialCardView
            tint(card, tone)
            card.findViewById<ImageView>(R.id.plat_icon).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Ui.color(c, tone.fg))
            }
            card.findViewById<TextView>(R.id.plat_label).text = name
            val lp = card.layoutParams as LinearLayout.LayoutParams
            if (i > 0) lp.marginStart = Ui.dp(c, 12f)
            plats.addView(card, lp)
        }

        // contacts
        val box = view.findViewById<LinearLayout>(R.id.contacts)
        val contacts = listOf(
            Contact(R.drawable.ic_m_chat, Ui.Tone.GREEN, R.string.contact_whatsapp,
                "+$WHATSAPP", "+$WHATSAPP", "https://wa.me/$WHATSAPP"),
            Contact(R.drawable.ic_m_send, Ui.Tone.BLUE, R.string.contact_telegram,
                "@$TELEGRAM", "@$TELEGRAM", "https://t.me/$TELEGRAM"),
            Contact(R.drawable.ic_m_at, Ui.Tone.ROSE, R.string.contact_email,
                EMAIL, EMAIL, "mailto:$EMAIL")
        )
        for (ct in contacts) box.addView(contactCard(inf, box, ct))

        val version = try {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        view.findViewById<TextView>(R.id.version).text = getString(R.string.about_version, version)
        view.findViewById<TextView>(R.id.footer).text = getString(R.string.about_footer, version)

        Rows.bindNav(view.findViewById(R.id.row_privacy), R.drawable.ic_m_shield, Ui.Tone.INDIGO,
            getString(R.string.about_privacy), null) {
            startActivity(Intent(c, PrivacyActivity::class.java))
        }

        // cards float up in sequence the first time the tab shows
        var n = 0
        for (id in intArrayOf(R.id.avatar_wrap, R.id.dev_name, R.id.dev_title, R.id.bio_card,
            R.id.platforms, R.id.teacher_card, R.id.contacts)) {
            Ui.rise(view.findViewById(id), n++)
        }
    }

    private fun section(v: View, icon: Int, tone: Ui.Tone, title: Int) {
        Ui.tone(v.findViewById(R.id.sec_icon_box), v.findViewById<ImageView>(R.id.sec_icon).also {
            it.setImageResource(icon)
        }, tone)
        v.findViewById<TextView>(R.id.sec_title).setText(title)
    }

    /** Hue-washed card: a light tint of the tone with a matching outline. */
    private fun tint(card: MaterialCardView, tone: Ui.Tone) {
        val c = card.context
        val fg = Ui.color(c, tone.fg)
        val bg = Ui.color(c, tone.bg)
        val surface = Ui.color(c, R.color.card)
        card.setCardBackgroundColor(ColorUtils.blendARGB(surface, bg, 0.55f))
        card.strokeColor = ColorUtils.setAlphaComponent(fg, 0x55)
    }

    private fun contactCard(inf: LayoutInflater, parent: ViewGroup, ct: Contact): View {
        val c = parent.context
        val card = inf.inflate(R.layout.item_contact, parent, false) as MaterialCardView
        tint(card, ct.tone)
        Ui.tone(card.findViewById(R.id.ct_icon_box),
            card.findViewById<ImageView>(R.id.ct_icon).also { it.setImageResource(ct.icon) }, ct.tone)
        card.findViewById<TextView>(R.id.ct_title).setText(ct.title)
        card.findViewById<TextView>(R.id.ct_value).apply {
            text = ct.shown
            setTextColor(Ui.color(c, ct.tone.fg))
        }
        card.setOnClickListener { open(ct.url) }
        card.setOnLongClickListener { copy(c, ct.copy); true }
        card.findViewById<View>(R.id.ct_copy).setOnClickListener { copy(c, ct.copy) }
        return card
    }

    private fun copy(c: Context, text: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(text, text))
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        Toast.makeText(c, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}
