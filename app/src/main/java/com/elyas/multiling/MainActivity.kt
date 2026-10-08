package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Home / setup screen.
 *
 * Built in Kotlin from [Ui] like the rest of the app, so it shares one set
 * of surfaces and spacing. The three setup steps now report their own state
 * rather than being a static checklist: each shows whether it is done, and
 * the ones already finished stop demanding attention.
 */
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var content: LinearLayout
    private var introView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        build()
        if (!AppLocale.isChosen(this)) showLanguagePicker()
    }

    override fun onResume() {
        super.onResume()
        // coming back from system settings, the step state has likely changed
        if (::content.isInitialized) build()
    }

    // ------------------------------------------------------------- layout
    private fun build() {
        val c = this
        val root = FrameLayout(c)
        root.setBackgroundResource(R.drawable.ds_bg_app)

        fun orb(res: Int, size: Float, x: Float, y: Float, grav: Int) {
            val v = View(c)
            v.setBackgroundResource(res)
            val s = Ui.dp(c, size)
            root.addView(v, FrameLayout.LayoutParams(s, s, grav).also {
                it.leftMargin = Ui.dp(c, x); it.rightMargin = Ui.dp(c, x)
                it.topMargin = Ui.dp(c, y); it.bottomMargin = Ui.dp(c, y)
            })
        }
        orb(R.drawable.ds_glow_a, 440f, -150f, -190f, Gravity.TOP or Gravity.END)
        orb(R.drawable.ds_glow_b, 400f, -170f, 120f, Gravity.TOP or Gravity.START)
        orb(R.drawable.ds_glow_c, 420f, -140f, -150f, Gravity.BOTTOM or Gravity.END)

        val scroll = ScrollView(c)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false

        content = LinearLayout(c)
        content.orientation = LinearLayout.VERTICAL
        val g = resources.getDimensionPixelSize(R.dimen.gutter)
        content.setPadding(g, 0, g, Ui.dp(c, 30f))
        scroll.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)

        content.addView(hero())
        content.addView(steps(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 16f) })
        content.addView(testCard(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 14f) })
        Ui.addCard(content, introCard())

        startOrgTyping()
    }

    /** Masthead: the brand plate, the name, and the publisher line. */
    private fun hero(): View {
        val c = this
        val card = Ui.card(c)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(Ui.dp(c, 20f), Ui.dp(c, 26f), Ui.dp(c, 20f), Ui.dp(c, 22f))

        val plate = LinearLayout(c)
        plate.gravity = Gravity.CENTER
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = Ui.dp(c, 26f).toFloat()
        plate.background = bg
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_hindukush)
        logo.adjustViewBounds = true
        plate.addView(logo, LinearLayout.LayoutParams(
            Ui.dp(c, 178f), LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(plate, LinearLayout.LayoutParams(Ui.dp(c, 224f), Ui.dp(c, 112f)))

        val name = TextView(c)
        name.text = getString(R.string.app_name)
        name.textSize = 24f
        name.setTypeface(name.typeface, Typeface.BOLD)
        name.setTextColor(Ui.color(c, R.color.text_primary))
        name.gravity = Gravity.CENTER
        card.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 16f) })

        val pub = TextView(c)
        pub.text = getString(R.string.about_publisher)
        pub.textSize = 13.5f
        pub.setTextColor(Ui.color(c, R.color.c_amber))
        pub.gravity = Gravity.CENTER
        card.addView(pub, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 4f) })
        return card
    }

    /**
     * The three setup steps, each reporting whether it is already done. A
     * finished step shows a tick and drops its button; an unfinished one
     * keeps its action in the accent colour.
     */
    private fun steps(): View {
        val c = this
        val enabled = isImeEnabled()
        val selected = isImeSelected()

        val wrap = LinearLayout(c)
        wrap.orientation = LinearLayout.VERTICAL

        wrap.addView(Ui.sectionHeader(c, getString(R.string.setup_desc)))

        val card = Ui.card(c)
        card.addView(step(1, getString(R.string.setup_step1), enabled,
            if (enabled) null else getString(R.string.btn_enable)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        Ui.addDivider(card)
        card.addView(step(2, getString(R.string.setup_step2), selected,
            if (selected) null else getString(R.string.btn_pick)) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })
        Ui.addDivider(card)
        card.addView(step(3, getString(R.string.setup_step3), false, null, null))
        wrap.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        return wrap
    }

    private fun step(
        n: Int, title: String, done: Boolean, action: String?, onClick: (() -> Unit)?
    ): View {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(Ui.dp(c, 10f), Ui.dp(c, 14f), Ui.dp(c, 10f), Ui.dp(c, 14f))

        // marker: a tick once done, otherwise the step number
        val accent = if (done) Ui.color(c, R.color.ok) else Ui.color(c, R.color.brand_1)
        val marker = FrameLayout(c)
        val mbg = android.graphics.drawable.GradientDrawable()
        mbg.shape = android.graphics.drawable.GradientDrawable.OVAL
        mbg.setColor((accent and 0x00FFFFFF) or 0x33000000)
        mbg.setStroke(Ui.dp(c, 1f), (accent and 0x00FFFFFF) or 0x55000000)
        marker.background = mbg
        if (done) {
            val iv = ImageView(c)
            iv.setImageDrawable(Ui.icon(c, R.drawable.ds_ic_check, accent, 18f))
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            marker.addView(iv, FrameLayout.LayoutParams(
                Ui.dp(c, 18f), Ui.dp(c, 18f), Gravity.CENTER))
        } else {
            val num = TextView(c)
            num.text = n.toString()
            num.textSize = 15f
            num.setTypeface(num.typeface, Typeface.BOLD)
            num.setTextColor(accent)
            num.gravity = Gravity.CENTER
            marker.addView(num, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
        row.addView(marker, LinearLayout.LayoutParams(Ui.dp(c, 36f), Ui.dp(c, 36f))
            .also { it.marginEnd = Ui.dp(c, 12f) })

        val tv = TextView(c)
        tv.text = title
        tv.textSize = 14.5f
        tv.setTextColor(Ui.color(c,
            if (done) R.color.text_tertiary else R.color.text_primary))
        row.addView(tv, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (action != null && onClick != null) {
            val b = Ui.primaryButton(c, action, onClick)
            b.textSize = 13.5f
            b.setPadding(Ui.dp(c, 16f), Ui.dp(c, 9f), Ui.dp(c, 16f), Ui.dp(c, 9f))
            row.addView(b, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = Ui.dp(c, 8f) })
        }
        return row
    }

    /** Somewhere to try the keyboard, plus the way into settings. */
    private fun testCard(): View {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 14f), Ui.dp(c, 14f), Ui.dp(c, 14f), Ui.dp(c, 14f))

        val field = EditText(c)
        field.hint = getString(R.string.test_hint)
        field.setTextColor(Ui.color(c, R.color.text_primary))
        field.setHintTextColor(Ui.color(c, R.color.text_tertiary))
        field.setBackgroundResource(R.drawable.ds_field)
        field.setPadding(Ui.dp(c, 14f), Ui.dp(c, 14f), Ui.dp(c, 14f), Ui.dp(c, 14f))
        field.minLines = 2
        field.gravity = Gravity.TOP or Gravity.START
        card.addView(field, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        card.addView(
            Ui.ghostButton(c, getString(R.string.btn_settings)) {
                startActivity(Intent(c, SettingsActivity::class.java))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = Ui.dp(c, 12f) })
        return card
    }

    /**
     * The organisation introduction, typed out. The paragraph is right
     * aligned and the publisher line sits under it, centred.
     */
    private fun introCard(): View {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18f), Ui.dp(c, 20f), Ui.dp(c, 18f), Ui.dp(c, 20f))

        val intro = TextView(c)
        intro.textSize = 14f
        intro.setTextColor(Ui.color(c, R.color.text_secondary))
        intro.setLineSpacing(Ui.dp(c, 6f).toFloat(), 1f)
        intro.minLines = 7
        intro.textDirection = View.TEXT_DIRECTION_RTL
        intro.gravity = Gravity.TOP or Gravity.END
        card.addView(intro, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        introView = intro

        val pub = TextView(c)
        pub.text = getString(R.string.about_publisher)
        pub.textSize = 15f
        pub.setTypeface(pub.typeface, Typeface.BOLD)
        pub.setTextColor(Ui.color(c, R.color.c_amber))
        pub.gravity = Gravity.CENTER
        card.addView(pub, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(c, 16f) })
        return card
    }

    // -------------------------------------------------------- step state
    private fun isImeEnabled(): Boolean = try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == packageName }
    } catch (_: Exception) { false }

    private fun isImeSelected(): Boolean = try {
        val cur = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        cur.startsWith("$packageName/")
    } catch (_: Exception) { false }

    // -------------------------------------------------- language picker
    private fun showLanguagePicker() {
        val codes = arrayOf(AppLocale.PS, AppLocale.FA, AppLocale.EN)
        val labels = arrayOf(
            getString(R.string.app_lang_ps),
            getString(R.string.app_lang_fa),
            getString(R.string.app_lang_en)
        )
        val current = codes.indexOf(AppLocale.current(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.app_lang_title)
            .setSingleChoiceItems(labels, current) { dlg, which ->
                AppLocale.setLanguage(this, codes[which])
                dlg.dismiss()
                recreate()
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------- typing animation
    private val typeHandler = Handler(Looper.getMainLooper())
    private var typeRunnable: Runnable? = null

    /**
     * Types the introduction a character at a time; once the whole text is
     * shown it waits a minute and starts over.
     */
    private fun startOrgTyping() {
        val tv = introView ?: return
        typeRunnable?.let { typeHandler.removeCallbacks(it) }
        typeHandler.removeCallbacksAndMessages(null)
        val full = getString(R.string.org_intro)
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (i <= full.length) {
                    tv.text = full.substring(0, i)
                    i++
                    typeHandler.postDelayed(this, 22L)
                } else {
                    typeHandler.postDelayed({ i = 0; typeHandler.post(this) }, 60_000L)
                }
            }
        }
        typeRunnable = step
        typeHandler.post(step)
    }

    override fun onDestroy() {
        typeRunnable?.let { typeHandler.removeCallbacks(it) }
        typeHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
