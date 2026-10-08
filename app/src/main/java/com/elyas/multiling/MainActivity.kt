package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    private lateinit var nav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_main)

        Ui.marginForNavBar(findViewById(R.id.nav_card), Ui.dp(this, 12f))
        nav = findViewById(R.id.bottom_nav)
        nav.setOnItemSelectedListener { show(it.itemId); true }
        nav.setOnItemReselectedListener { scrollToTop(it.itemId) }

        val first = tabId(intent?.getStringExtra(EXTRA_TAB))
            ?: savedInstanceState?.getInt("tab")?.takeIf { it != 0 }
            ?: R.id.tab_home
        nav.selectedItemId = first
        show(first)

        if (!AppLocale.isChosen(this)) showLanguageSheet()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabId(intent.getStringExtra(EXTRA_TAB))?.let {
            nav.selectedItemId = it
            show(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", nav.selectedItemId)
    }

    private fun tabId(name: String?): Int? = when (name) {
        TAB_HOME -> R.id.tab_home
        TAB_SHORTCUTS -> R.id.tab_shortcuts
        TAB_SETTINGS -> R.id.tab_settings
        TAB_ABOUT -> R.id.tab_about
        else -> null
    }

    private fun create(id: Int): Fragment = when (id) {
        R.id.tab_shortcuts -> ShortcutsFragment()
        R.id.tab_settings -> SettingsTabFragment()
        R.id.tab_about -> AboutFragment()
        else -> HomeFragment()
    }

    private fun show(id: Int) {
        val fm = supportFragmentManager
        val tag = "tab_$id"
        val existing = fm.findFragmentByTag(tag)
        if (existing != null && existing.isVisible) return
        val tx = fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.tab_in, R.anim.tab_out)
        for (f in fm.fragments) if (f.tag?.startsWith("tab_") == true && f !== existing) tx.hide(f)
        if (existing == null) tx.add(R.id.tab_host, create(id), tag) else tx.show(existing)
        tx.commit()
    }

    private fun scrollToTop(id: Int) {
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
