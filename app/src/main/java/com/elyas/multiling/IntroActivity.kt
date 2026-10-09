package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

/**
 * First-run introduction: welcome, app language, what the keyboard does,
 * and the two steps that turn it on. Pages slide in the reading direction
 * (swipe or Next), with growing dots underneath. Finishing — or skipping —
 * marks the intro done and opens the app.
 */
class IntroActivity : AppCompatActivity() {

    companion object {
        const val PREF_DONE = "intro_done"

        fun needed(c: Context): Boolean =
            !PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF_DONE, false)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var pages: List<View>
    private lateinit var dots: LinearLayout
    private lateinit var next: MaterialButton
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_intro)
        Ui.padForBars(findViewById(R.id.intro_content), top = true, bottom = true)

        pages = listOf(R.id.page_welcome, R.id.page_lang, R.id.page_features, R.id.page_setup)
            .map { findViewById(it) }
        dots = findViewById(R.id.intro_dots)
        next = findViewById(R.id.intro_next)

        buildWelcome()
        buildLanguage()
        buildFeatures()
        bindSetup()
        buildDots()

        next.setOnClickListener { if (page == pages.size - 1) finishIntro() else go(page + 1) }
        findViewById<View>(R.id.intro_skip).setOnClickListener { finishIntro() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page > 0) go(page - 1) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        // horizontal swipes turn the page, in the reading direction
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < abs(vy) * 1.5f || abs(vx) < 600) return false
                val forward = if (rtl) vx > 0 else vx < 0
                if (forward && page < pages.size - 1) go(page + 1)
                else if (!forward && page > 0) go(page - 1)
                return true
            }
        })
        findViewById<View>(R.id.page_host).setOnTouchListener { _, ev -> gd.onTouchEvent(ev); true }

        page = savedInstanceState?.getInt("page") ?: 0
        for ((i, p) in pages.withIndex()) p.visibility = if (i == page) View.VISIBLE else View.GONE
        refreshChrome(animate = false)
        enter(pages[page])
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    override fun onResume() {
        super.onResume()
        bindSetup()   // back from system settings: the steps may be done now
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) bindSetup()   // the keyboard picker is a dialog, not a pause
    }

    // ------------------------------------------------------------ paging
    private fun go(to: Int) {
        if (to == page || to !in pages.indices) return
        val out = pages[page]
        val inn = pages[to]
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val dir = (if (to > page) 1 else -1) * (if (rtl) -1 else 1)
        val shift = Ui.dp(this, 48f).toFloat()
        out.animate().alpha(0f).translationX(-dir * shift).setDuration(180)
            .withEndAction { out.visibility = View.GONE; out.translationX = 0f; out.alpha = 1f }.start()
        inn.visibility = View.VISIBLE
        inn.alpha = 0f
        inn.translationX = dir * shift
        inn.animate().alpha(1f).translationX(0f).setStartDelay(90).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f)).start()
        page = to
        refreshChrome(animate = true)
        enter(inn)
    }

    /** The children of a page rise in one after another. */
    private fun enter(p: View) {
        val group = (p as? android.view.ViewGroup)?.let {
            if (it is androidx.core.widget.NestedScrollView) it.getChildAt(0) as? android.view.ViewGroup else it
        } ?: return
        for (i in 0 until group.childCount) Ui.rise(group.getChildAt(i), i + 1)
    }

    private fun buildDots() {
        dots.removeAllViews()
        for (i in pages.indices) {
            val d = View(this)
            d.setBackgroundResource(R.drawable.bg_dot)
            val lp = LinearLayout.LayoutParams(Ui.dp(this, 8f), Ui.dp(this, 8f))
            lp.marginEnd = Ui.dp(this, 6f)
            dots.addView(d, lp)
        }
    }

    private fun refreshChrome(animate: Boolean) {
        val on = Ui.attr(this, androidx.appcompat.R.attr.colorPrimary)
        val off = Ui.attr(this, com.google.android.material.R.attr.colorOutlineVariant)
        for (i in 0 until dots.childCount) {
            val d = dots.getChildAt(i)
            val w = Ui.dp(this, if (i == page) 26f else 8f)
            ViewCompat.setBackgroundTintList(d, ColorStateList.valueOf(if (i == page) on else off))
            val lp = d.layoutParams
            if (!animate) { lp.width = w; d.layoutParams = lp; continue }
            android.animation.ValueAnimator.ofInt(lp.width, w).apply {
                duration = 260
                addUpdateListener { lp.width = it.animatedValue as Int; d.layoutParams = lp }
                start()
            }
        }
        val last = page == pages.size - 1
        next.setText(if (last) R.string.intro_start else R.string.intro_next)
        next.setIconResource(if (last) R.drawable.ic_m_check else R.drawable.ic_m_next)
        findViewById<View>(R.id.intro_skip).visibility = if (last) View.INVISIBLE else View.VISIBLE
    }

    // ------------------------------------------------------------- pages
    private fun buildWelcome() {
        val logo = findViewById<View>(R.id.intro_logo)
        android.animation.ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -Ui.dp(this, 8f).toFloat()).apply {
            duration = 2400
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
        val row = findViewById<LinearLayout>(R.id.intro_langs_row)
        for (name in listOf("پښتو", "دري", "عربي", "اردو", "English")) {
            val tv = TextView(this)
            tv.text = name
            tv.setBackgroundResource(R.drawable.bg_pill)
            tv.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface))
            tv.textSize = 13f
            val p = Ui.dp(this, 12f)
            tv.setPadding(p, Ui.dp(this, 6f), p, Ui.dp(this, 6f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = Ui.dp(this, 4f)
            lp.marginEnd = Ui.dp(this, 4f)
            row.addView(tv, lp)
        }
    }

    private fun buildLanguage() {
        Ui.tone(findViewById(R.id.welcome_icon_box), findViewById(R.id.welcome_icon), Ui.Tone.BLUE)
        Rows.languageOptions(findViewById(R.id.intro_lang_options), AppLocale.current(this)) { code ->
            if (code == AppLocale.current(this)) return@languageOptions
            AppLocale.setLanguage(this, code)
            recreate()   // the whole intro switches language in place
        }
    }

    private fun buildFeatures() {
        val box = findViewById<LinearLayout>(R.id.intro_features)
        val list = listOf(
            arrayOf(R.drawable.ic_m_bulb, Ui.Tone.AMBER, R.string.feat_smart_title, R.string.feat_smart_sub),
            arrayOf(R.drawable.ic_m_snippet, Ui.Tone.TEAL, R.string.feat_short_title, R.string.feat_short_sub),
            arrayOf(R.drawable.ic_m_palette, Ui.Tone.PINK, R.string.feat_look_title, R.string.feat_look_sub),
            arrayOf(R.drawable.ic_m_shield, Ui.Tone.INDIGO, R.string.feat_private_title, R.string.feat_private_sub)
        )
        for (f in list) {
            val row = Rows.addNav(box, f[0] as Int, f[1] as Ui.Tone, getString(f[2] as Int),
                getString(f[3] as Int), 0) {}
            row.isClickable = false
        }
    }

    private fun bindSetup() {
        val enabled = try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.any { it.packageName == packageName }
        } catch (_: Exception) { false }
        val selected = enabled && try {
            (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
                .startsWith("$packageName/")
        } catch (_: Exception) { false }
        step(findViewById(R.id.setup_enable), 1, R.string.setup_enable_title, R.string.setup_enable_sub,
            R.string.setup_open, enabled, true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step(findViewById(R.id.setup_select), 2, R.string.setup_select_title, R.string.setup_select_sub,
            R.string.setup_pick, selected, enabled) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    private fun step(v: View, n: Int, title: Int, sub: Int, action: Int,
                     done: Boolean, available: Boolean, onClick: () -> Unit) {
        val num = v.findViewById<TextView>(R.id.st_num)
        val tone = if (done) Ui.Tone.GREEN else Ui.Tone.VIOLET
        ViewCompat.setBackgroundTintList(num, ColorStateList.valueOf(Ui.color(this, tone.bg)))
        num.setTextColor(Ui.color(this, tone.fg))
        num.text = if (done) "✓" else Ui.digits(this, n)
        v.findViewById<TextView>(R.id.st_title).setText(title)
        v.findViewById<TextView>(R.id.st_sub).setText(sub)
        val btn = v.findViewById<MaterialButton>(R.id.st_btn)
        btn.setText(if (done) R.string.setup_done else action)
        btn.isEnabled = !done && available
        btn.setOnClickListener { onClick() }
    }

    private fun finishIntro() {
        AppLocale.setLanguage(this, AppLocale.current(this))   // marks the language chosen
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_DONE, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
