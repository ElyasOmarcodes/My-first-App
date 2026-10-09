package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton

/**
 * Home tab: a status hero that knows whether the keyboard is set up, three
 * colourful tiles with real numbers, quick ways into the most-used settings,
 * a field to try the keyboard, and a rotating card of typing tips.
 */
class HomeFragment : Fragment(R.layout.frag_home) {

    private val main = Handler(Looper.getMainLooper())
    private var typing: Runnable? = null
    private var artAnim: android.animation.Animator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val c = requireContext()
        Ui.padForBars(view.findViewById(R.id.scroll), top = true, bottom = true)

        // ---- light / dark toggle in the header
        val night = Ui.isNight(c)
        view.findViewById<ImageView>(R.id.img_theme)
            .setImageResource(if (night) R.drawable.ic_m_light else R.drawable.ic_m_dark)
        view.findViewById<View>(R.id.btn_theme).setOnClickListener {
            AppLook.toggle(requireActivity(), night)
        }

        // ---- stat tiles: gradient, icon, label, and a glow in their own colour
        tile(view.findViewById(R.id.tile_langs), R.drawable.bg_tile_rose,
            R.drawable.ic_m_translate, R.string.stat_langs, R.color.grad_rose_a)
        tile(view.findViewById(R.id.tile_words), R.drawable.bg_tile_amber,
            R.drawable.ic_m_spellcheck, R.string.stat_words, R.color.grad_amber_a)
        tile(view.findViewById(R.id.tile_shortcuts), R.drawable.bg_tile_cyan,
            R.drawable.ic_m_snippet, R.string.stat_shortcuts, R.color.grad_cyan_a)

        // ---- quick actions
        quick(view.findViewById(R.id.quick_look), R.drawable.ic_m_palette, Ui.Tone.BLUE,
            R.string.quick_look_title, R.string.quick_look_sub) {
            startActivity(SettingsActivity.intent(c, "look"))
        }
        quick(view.findViewById(R.id.quick_langs), R.drawable.ic_m_globe, Ui.Tone.TEAL,
            R.string.quick_langs_title, R.string.quick_langs_sub) {
            startActivity(SettingsActivity.intent(c, "langs"))
        }
        quick(view.findViewById(R.id.quick_typing), R.drawable.ic_m_spellcheck, Ui.Tone.GREEN,
            R.string.quick_typing_title, R.string.quick_typing_sub) {
            startActivity(SettingsActivity.intent(c, "typing"))
        }
        quick(view.findViewById(R.id.quick_sizes), R.drawable.ic_m_format_size, Ui.Tone.AMBER,
            R.string.quick_sizes_title, R.string.quick_sizes_sub) {
            startActivity(SettingsActivity.intent(c, "sizes"))
        }
        view.findViewById<View>(R.id.sec_quick).let { sec ->
            Ui.tone(sec.findViewById(R.id.sec_icon_box),
                sec.findViewById<ImageView>(R.id.sec_icon).also { it.setImageResource(R.drawable.ic_m_sparkle) },
                Ui.Tone.VIOLET)
            sec.findViewById<TextView>(R.id.sec_title).setText(R.string.quick_access)
        }
        Ui.tone(view.findViewById(R.id.try_icon_box), view.findViewById(R.id.try_icon), Ui.Tone.BLUE)
        Ui.tip(view.findViewById(R.id.btn_theme), getString(R.string.look_title))

        // the keyboard illustration floats gently
        view.findViewById<View>(R.id.hero_art).let { art ->
            artAnim = android.animation.ObjectAnimator.ofFloat(art, View.TRANSLATION_Y, 0f, -Ui.dp(c, 6f).toFloat()).apply {
                duration = 2600
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
        }

        Ui.tone(view.findViewById(R.id.intro_icon_box), view.findViewById(R.id.intro_icon), Ui.Tone.AMBER)
        startTyping(view.findViewById(R.id.org_intro))

        view.findViewById<View>(R.id.hero).clipToOutline = true
        Ui.rise(view.findViewById(R.id.hero), 0)
    }

    override fun onResume() {
        super.onResume()
        // back from system settings, the setup state has likely changed
        bindStatus()
        bindStats()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { bindStatus(); bindStats() }
    }

    // ------------------------------------------------------------- status
    private fun bindStatus() {
        val v = view ?: return
        val c = requireContext()
        val enabled = isEnabled(c)
        val selected = enabled && isSelected(c)
        val title = v.findViewById<TextView>(R.id.hero_title)
        val body = v.findViewById<TextView>(R.id.hero_body)
        val btn = v.findViewById<MaterialButton>(R.id.hero_btn)
        val chip = v.findViewById<TextView>(R.id.hero_chip)
        chip.text = "v" + versionName(c)
        step(v.findViewById(R.id.step_enable), 1, enabled)
        step(v.findViewById(R.id.step_select), 2, selected)
        when {
            !enabled -> {
                title.setText(R.string.status_enable_title)
                body.setText(R.string.status_enable_body)
                btn.visibility = View.VISIBLE
                btn.setText(R.string.btn_enable)
                btn.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            }
            !selected -> {
                title.setText(R.string.status_select_title)
                body.setText(R.string.status_select_body)
                btn.visibility = View.VISIBLE
                btn.setText(R.string.btn_pick)
                btn.setOnClickListener {
                    (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                }
            }
            else -> {
                title.setText(R.string.status_ready_title)
                body.setText(R.string.status_ready_body)
                btn.visibility = View.GONE
            }
        }
    }

    /** A setup step: white with a check when done, glassy with its number when not. */
    private fun step(tv: TextView, n: Int, done: Boolean) {
        val c = tv.context
        val violet = Ui.color(c, R.color.grad_violet_a)
        androidx.core.view.ViewCompat.setBackgroundTintList(tv, android.content.res.ColorStateList.valueOf(
            if (done) android.graphics.Color.WHITE else 0x33FFFFFF))
        tv.setTextColor(if (done) violet else android.graphics.Color.WHITE)
        val label = tv.text.toString().substringAfter("  ")
        if (done) {
            val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(c, R.drawable.ic_m_check_circle)?.mutate()
            d?.setTint(violet)
            val s = Ui.dp(c, 18f)
            d?.setBounds(0, 0, s, s)
            tv.setCompoundDrawablesRelative(d, null, null, null)
            tv.text = label
        } else {
            tv.setCompoundDrawablesRelative(null, null, null, null)
            tv.text = Ui.digits(c, n) + "  " + label
        }
    }

    private fun isEnabled(c: Context): Boolean = try {
        val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == c.packageName }
    } catch (_: Exception) { false }

    private fun isSelected(c: Context): Boolean = try {
        (Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            .startsWith(c.packageName + "/")
    } catch (_: Exception) { false }

    private fun versionName(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // -------------------------------------------------------------- stats
    private fun bindStats() {
        val v = view ?: return
        val c = requireContext().applicationContext
        val p = PreferenceManager.getDefaultSharedPreferences(c)
        // same rule the keyboard uses to decide which languages it cycles
        val enabled = p.getStringSet("languages", null) ?: setOf("ps", "fa", "ar", "en")
        val langs = Layouts.ALL.count { enabled.isEmpty() || enabled.contains(it.code) }
            .coerceAtLeast(1)
        setTile(v, R.id.tile_langs, langs)
        setTile(v, R.id.tile_shortcuts, try { AutoTextStore(c).all().size } catch (_: Exception) { 0 })

        // learned words: read the small per-language files off the main thread
        Thread {
            var words = 0
            try {
                c.filesDir.listFiles { f -> f.name.startsWith("dict_") && f.name.endsWith(".txt") }
                    ?.forEach { f -> words += WordStore.countVisible(f) }
            } catch (_: Exception) {
            }
            main.post { view?.let { setTile(it, R.id.tile_words, words) } }
        }.start()
    }

    private fun setTile(root: View, id: Int, n: Int) {
        root.findViewById<View>(id).findViewById<TextView>(R.id.tile_value).text =
            Ui.digits(requireContext(), n)
    }

    private fun tile(t: View, bg: Int, icon: Int, label: Int, glow: Int) {
        t.setBackgroundResource(bg)
        t.findViewById<ImageView>(R.id.tile_icon).setImageResource(icon)
        t.findViewById<TextView>(R.id.tile_label).setText(label)
        Ui.tip(t, getString(label))
        t.findViewById<TextView>(R.id.tile_value).text = "–"
        t.clipToOutline = true
        // a shadow in the tile's own colour, as in the inspiration
        if (Build.VERSION.SDK_INT >= 28) {
            val col = Ui.color(t.context, glow)
            t.outlineSpotShadowColor = col
            t.outlineAmbientShadowColor = col
        }
    }

    private fun quick(card: View, icon: Int, tone: Ui.Tone, title: Int, sub: Int, onClick: () -> Unit) {
        val c = card.context
        val box = card.findViewById<View>(R.id.quick_icon_box)
        val iv = card.findViewById<ImageView>(R.id.quick_icon)
        iv.setImageResource(icon)
        // tinted card: hue fill with a matching hairline; the icon sits on a
        // plate of the card colour so it reads as a separate chip
        (card as com.google.android.material.card.MaterialCardView).apply {
            setCardBackgroundColor(Ui.color(c, tone.bg))
            strokeColor = Ui.color(c, tone.bg)
        }
        androidx.core.view.ViewCompat.setBackgroundTintList(
            box, android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.card)))
        iv.imageTintList = android.content.res.ColorStateList.valueOf(Ui.color(c, tone.fg))
        card.findViewById<TextView>(R.id.quick_title).setText(title)
        card.findViewById<TextView>(R.id.quick_sub).setText(sub)
        card.setOnClickListener { onClick() }
        Ui.tip(card, Ui.tipText(getString(title), getString(sub)))
    }

    // ------------------------------------------------------- typing tips
    /**
     * Types one tip a character at a time, rests on it, then moves to the
     * next — a small live demo of a keyboard at work.
     */
    private fun startTyping(tv: TextView) {
        val tips = resources.getStringArray(R.array.home_tips)
        if (tips.isEmpty()) return
        val counter = view?.findViewById<TextView>(R.id.tip_counter)
        var tip = 0
        var i = 0
        val step = object : Runnable {
            override fun run() {
                val full = tips[tip]
                if (i == 0) counter?.text = Ui.digits(requireContext(), tip + 1) + " / " +
                    Ui.digits(requireContext(), tips.size)
                if (i <= full.length) {
                    tv.text = full.substring(0, i)
                    i++
                    main.postDelayed(this, 24L)
                } else {
                    main.postDelayed({
                        i = 0
                        tip = (tip + 1) % tips.size
                        main.post(this)
                    }, 5_000L)
                }
            }
        }
        typing = step
        main.post(step)
    }

    override fun onDestroyView() {
        artAnim?.cancel()
        artAnim = null
        main.removeCallbacksAndMessages(null)
        typing = null
        super.onDestroyView()
    }
}
