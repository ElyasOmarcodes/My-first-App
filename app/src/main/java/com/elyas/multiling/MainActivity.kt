package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * The app shell: four tabs under a floating bottom navigation bar.
 *
 * Tabs are fragments that are shown and hidden rather than replaced, so each
 * keeps its scroll position and state while you move between them, the way
 * native apps behave.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_HOME = "home"
        const val TAB_SHORTCUTS = "shortcuts"
        const val TAB_SETTINGS = "settings"
        const val TAB_ABOUT = "about"
        private const val EXTRA_TAB = "tab"

        fun intent(c: Context, tab: String): Intent =
            Intent(c, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var bar: FloatingTabBar
    private lateinit var updates: UpdateGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_main)

        Ui.marginForNavBar(findViewById(R.id.nav_card), Ui.dp(this, 14f))
        // the fade layers follow the system bars: status bar plus a soft
        // tail on top; under the tab bar down to the screen edge below
        val top = findViewById<View>(R.id.scrim_top)
        val bottom = findViewById<View>(R.id.scrim_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.layoutParams = v.layoutParams.apply { height = bars.top + Ui.dp(this@MainActivity, 28f) }
            bottom.layoutParams = bottom.layoutParams.apply {
                height = bars.bottom + Ui.dp(this@MainActivity, 14f + 70f + 34f)
            }
            insets
        }

        updates = UpdateGate(this)
        bar = findViewById(R.id.tab_bar)
        bar.setTabs(listOf(
            FloatingTabBar.Tab(TAB_HOME, R.drawable.ic_m_home, R.drawable.ic_m_home_filled, R.string.tab_home),
            FloatingTabBar.Tab(TAB_SHORTCUTS, R.drawable.ic_m_doc_outline, R.drawable.ic_m_snippet, R.string.tab_shortcuts),
            FloatingTabBar.Tab(TAB_SETTINGS, R.drawable.ic_m_tune, R.drawable.ic_m_tune, R.string.tab_settings),
            FloatingTabBar.Tab(TAB_ABOUT, R.drawable.ic_m_person_outline, R.drawable.ic_m_person, R.string.tab_about)
        ))
        bar.onSelect = { show(it) }
        bar.onReselect = { scrollToTop(it) }

        val first = valid(intent?.getStringExtra(EXTRA_TAB))
            ?: valid(savedInstanceState?.getString("tab"))
            ?: TAB_HOME
        bar.select(first, animate = false)
        show(first)

        if (!AppLocale.isChosen(this)) showLanguageSheet()
    }

    override fun onResume() {
        super.onResume()
        updates.check()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        valid(intent.getStringExtra(EXTRA_TAB))?.let {
            bar.select(it, animate = true)
            show(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", bar.selected)
    }

    private fun valid(name: String?): String? =
        name?.takeIf { it == TAB_HOME || it == TAB_SHORTCUTS || it == TAB_SETTINGS || it == TAB_ABOUT }

    private fun create(id: String): Fragment = when (id) {
        TAB_SHORTCUTS -> ShortcutsFragment()
        TAB_SETTINGS -> SettingsTabFragment()
        TAB_ABOUT -> AboutFragment()
        else -> HomeFragment()
    }

    private fun show(id: String) {
        val fm = supportFragmentManager
        val tag = "tab_$id"
        val existing = fm.findFragmentByTag(tag)
        if (existing != null && existing.isVisible) return
        val tx = fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.tab_in, R.anim.tab_out)
        for (f in fm.fragments) if (f.tag?.startsWith("tab_") == true && f !== existing) tx.hide(f)
        if (existing == null) tx.add(R.id.tab_host, create(id), tag) else tx.show(existing)
        // now, not on the next frame: a queued commit is the lag felt when
        // tapping tabs quickly one after another
        tx.commitNow()
    }

    private fun scrollToTop(id: String) {
        val f = supportFragmentManager.findFragmentByTag("tab_$id") ?: return
        f.view?.findViewById<NestedScrollView>(R.id.scroll)?.smoothScrollTo(0, 0)
    }

    /**
     * First run: choose the app language. A bottom sheet with the same tiles
     * as Settings, so the choice looks the same the second time around.
     */
    private fun showLanguageSheet() {
        val sheet = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_language, null)
        Ui.tone(v.findViewById(R.id.welcome_icon_box), v.findViewById(R.id.welcome_icon), Ui.Tone.BLUE)
        var picked = AppLocale.current(this)
        Rows.languageOptions(v.findViewById<LinearLayout>(R.id.sheet_options), picked) { picked = it }
        v.findViewById<android.view.View>(R.id.sheet_continue).setOnClickListener {
            AppLocale.setLanguage(this, picked)
            sheet.dismiss()
            recreate()
        }
        sheet.setContentView(v)
        sheet.setCancelable(false)
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }
}
