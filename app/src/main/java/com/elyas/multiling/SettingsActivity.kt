package com.elyas.multiling

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

/**
 * Settings: a main screen with one entry per category; each entry opens its
 * own sub-screen. Appearance/size screens show a live keyboard preview that
 * updates as options change. The backup screen imports/exports everything.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var preview: KeyboardView
    private lateinit var previewHolder: FrameLayout

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePresets.bootstrap(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val host = FrameLayout(this)
        host.id = R.id.settings_host
        root.addView(
            host,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        previewHolder = FrameLayout(this)
        preview = KeyboardView(this)
        previewHolder.addView(preview)
        previewHolder.visibility = android.view.View.GONE
        root.addView(
            previewHolder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)
        applyEdgePadding()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_host, SettingsFragment.create("main"))
                .commit()
        }
        supportActionBar?.setTitle(R.string.settings_title)
        supportFragmentManager.addOnBackStackChangedListener { updatePreviewVisibility() }

        // the keyboard's menu can deep-link straight to a settings page
        // (e.g. the active-languages screen)
        when (intent?.getStringExtra("open_screen")) {
            "langs" -> openScreen("langs", getString(R.string.pref_cat_langs))
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(prefListener)
        refreshPreview()
    }

    override fun onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    fun openScreen(screen: String, title: CharSequence) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_host, SettingsFragment.create(screen))
            .addToBackStack(screen)
            .commit()
        supportActionBar?.title = title
        updatePreviewVisibility(screen)
    }

    private fun currentScreen(): String {
        val i = supportFragmentManager.backStackEntryCount
        return if (i == 0) "main"
        else supportFragmentManager.getBackStackEntryAt(i - 1).name ?: "main"
    }

    private fun updatePreviewVisibility(screen: String = currentScreen()) {
        val show = screen == "look" || screen == "sizes" || screen == "colors" ||
            screen.startsWith("col_")
        previewHolder.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        if (screen == "main") supportActionBar?.setTitle(R.string.settings_title)
        if (screen == "colors") supportActionBar?.setTitle(R.string.pref_cat_colors)
        if (show) refreshPreview()
    }

    /** Live keyboard preview reflecting the current preferences. */
    private fun refreshPreview() {
        if (previewHolder.visibility != android.view.View.VISIBLE) return
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        preview.theme = KeyboardView.themeByName(p.getString("theme", "dark") ?: "dark")
        val custom = p.getBoolean("col_custom", false)
        preview.customColors = custom
        if (custom) {
            val t = preview.theme
            preview.colKeyFill = p.getInt("col_key", t.keyFill)
            preview.colKeyFill2 =
                if (KeyboardView.gradientOn(p, "col_key_mode", "col_key_grad_on"))
                    p.getInt("col_key_grad", 0) else 0
            preview.colKeyGradStyle = p.getString("col_key_grad_style", "linear") ?: "linear"
            preview.colKeyGradDir = p.getString("col_key_grad_dir", "v") ?: "v"
            preview.colSpecialFill = p.getInt("col_special", t.specialFill)
            preview.colSpecialFill2 =
                if (KeyboardView.gradientOn(p, "col_special_mode", "col_special_grad_on"))
                    p.getInt("col_special_grad", 0) else 0
            preview.colSpecialGradStyle = p.getString("col_special_grad_style", "linear") ?: "linear"
            preview.colSpecialGradDir = p.getString("col_special_grad_dir", "v") ?: "v"
            preview.colTextColor = p.getInt("col_text", t.text)
            preview.colHintColor = p.getInt("col_hint", t.hint)
            preview.colBg = p.getInt("col_bg", t.background)
        }
        preview.keyHeightDp = p.getInt("key_height", 72)
        preview.fontScale = p.getInt("font_scale", 70) / 100f
        preview.hintScale = p.getInt("hint_scale", 96) / 100f
        preview.cornerRadiusDp = p.getInt("corner_radius", 6)
        preview.keyGapDp = p.getInt("key_gap", 2) / 1.33f
        preview.showHints = p.getBoolean("hints", true)
        preview.keyBorder = p.getBoolean("key_border", false)
        val density = resources.displayMetrics.density
        preview.setPadding(0, 0, 0, (p.getInt("bottom_gap", 10) * density).toInt())
        val rows = ArrayList<List<KeyDef>>(Layouts.PASHTO.rows)
        rows.add(
            listOf(
                KeyDef("۱۲۳", code = Keys.SYM, width = 1.5f, hintIcon = Keys.ICON_MIC),
                KeyDef("ـ"),
                KeyDef("پښتو", code = Keys.SPACE, width = 4f),
                KeyDef("."),
                KeyDef("↵", code = Keys.ENTER, width = 1.5f)
            )
        )
        preview.setKeyboard(rows, rows.map { row -> row.map { it.label } })
    }

    // ------------------------------------------------------------ fragment
    class SettingsFragment : PreferenceFragmentCompat() {

        private var pendingLang = "ps"

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val screen = arguments?.getString("screen") ?: "main"
            val res = when (screen) {
                "langs" -> R.xml.prefs_langs
                "look" -> R.xml.prefs_look
                "colors" -> R.xml.prefs_colors
                "col_keys" -> R.xml.prefs_col_keys
                "col_special" -> R.xml.prefs_col_special
                "col_text" -> R.xml.prefs_col_text
                "sizes" -> R.xml.prefs_sizes
                "typing" -> R.xml.prefs_typing
                "autotext" -> R.xml.prefs_autotext
                "control" -> R.xml.prefs_control
                "feedback" -> R.xml.prefs_feedback
                "backup" -> R.xml.prefs_backup
                else -> R.xml.prefs
            }
            setPreferencesFromResource(res, rootKey)
            if (screen == "main") wireMain() else if (screen == "autotext") wireAutoText()
            if (screen == "look") wireLook()
            if (screen == "backup") wireBackup()
            if (screen == "colors") wireColors()
            if (screen == "col_keys") wireColorGroup("col_key", "col_key_grad_on")
            if (screen == "col_special") wireColorGroup("col_special", "col_special_grad_on")
        }

        private fun wireMain() {
            val map = listOf(
                "screen_langs" to "langs", "screen_look" to "look",
                "screen_sizes" to "sizes", "screen_typing" to "typing",
                "screen_autotext" to "autotext", "screen_control" to "control",
                "screen_feedback" to "feedback", "screen_backup" to "backup"
            )
            for ((key, screen) in map) {
                findPreference<Preference>(key)?.setOnPreferenceClickListener { pref ->
                    (activity as? SettingsActivity)?.openScreen(screen, pref.title ?: "")
                    true
                }
            }
            findPreference<Preference>("screen_colors")?.setOnPreferenceClickListener { pref ->
                (activity as? SettingsActivity)?.openScreen("colors", pref.title ?: "")
                true
            }
            findPreference<Preference>("screen_about")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AboutActivity::class.java))
                true
            }
        }

        private fun wireLook() {
            // preset themes (p_*) apply their bundled theme.txt; picking a
            // plain theme switches custom colours off so it actually shows
            findPreference<Preference>("theme")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val v = newValue as? String
                    if (ThemePresets.isPreset(v)) {
                        ThemePresets.apply(requireContext(), v!!)
                    } else {
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit().putBoolean("col_custom", false).apply()
                    }
                    true
                }
        }

        private fun wireColors() {
            findPreference<Preference>("theme_export")?.setOnPreferenceClickListener {
                createDocument(REQ_THEME_EXPORT, "theme.txt")
                true
            }
            findPreference<Preference>("theme_import")?.setOnPreferenceClickListener {
                openDocument(REQ_THEME_IMPORT)
                true
            }
            // colour sub-pages: letter keys / special keys / text colours
            val subs = listOf(
                "screen_col_keys" to "col_keys",
                "screen_col_special" to "col_special",
                "screen_col_text" to "col_text"
            )
            for ((key, screen) in subs) {
                findPreference<Preference>(key)?.setOnPreferenceClickListener { pref ->
                    (activity as? SettingsActivity)?.openScreen(screen, pref.title ?: "")
                    true
                }
            }
        }

        /** One colour group page: gradient options only show in "two colours"
         *  mode; the direction only applies to the linear style. */
        private fun wireColorGroup(prefix: String, legacyKey: String) {
            val refresh = Preference.OnPreferenceChangeListener { _, _ ->
                view?.post { applyColorGroupVisibility(prefix, legacyKey) }
                true
            }
            findPreference<Preference>("${prefix}_mode")?.onPreferenceChangeListener = refresh
            findPreference<Preference>("${prefix}_grad_style")?.onPreferenceChangeListener = refresh
            applyColorGroupVisibility(prefix, legacyKey)
        }

        private fun applyColorGroupVisibility(prefix: String, legacyKey: String) {
            val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val two = KeyboardView.gradientOn(p, "${prefix}_mode", legacyKey)
            findPreference<Preference>("${prefix}_grad")?.isVisible = two
            findPreference<Preference>("${prefix}_grad_style")?.isVisible = two
            val linear = (p.getString("${prefix}_grad_style", "linear") ?: "linear") == "linear"
            findPreference<Preference>("${prefix}_grad_dir")?.isVisible = two && linear
        }

        private fun wireAutoText() {
            findPreference<Preference>("autotext_manage")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AutoTextActivity::class.java))
                true
            }
            findPreference<Preference>("autotext_import")?.setOnPreferenceClickListener {
                openDocument(REQ_AUTOTEXT_IMPORT)
                true
            }
            findPreference<Preference>("autotext_export")?.setOnPreferenceClickListener {
                createDocument(REQ_AUTOTEXT_EXPORT, "autotext.txt")
                true
            }
        }

        private fun wireBackup() {
            findPreference<Preference>("settings_export")?.setOnPreferenceClickListener {
                createDocument(REQ_SETTINGS_EXPORT, "keyboard_settings.txt")
                true
            }
            findPreference<Preference>("settings_import")?.setOnPreferenceClickListener {
                openDocument(REQ_SETTINGS_IMPORT)
                true
            }
            findPreference<Preference>("settings_reset")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pref_settings_reset)
                    .setMessage(R.string.settings_reset_q)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        resetToDefaults()
                        toast(getString(R.string.done))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            findPreference<Preference>("dict_import")?.setOnPreferenceClickListener {
                chooseLanguage { openDocument(REQ_DICT_IMPORT) }
                true
            }
            findPreference<Preference>("dict_export")?.setOnPreferenceClickListener {
                chooseLanguage { createDocument(REQ_DICT_EXPORT, "dict_$pendingLang.txt") }
                true
            }
            findPreference<Preference>("clear_learned")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pref_clear_learned)
                    .setMessage(R.string.clear_learned_q)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        for (l in Layouts.ALL) {
                            WordStore(requireContext(), l.code).clearLearned()
                        }
                        AutoTextStore.bumpDataVersion(requireContext())
                        toast(getString(R.string.done))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }

        // -------------------------------------------- settings serialization
        private fun exportSettings(): String {
            val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val sb = StringBuilder()
            for ((k, v) in p.all) {
                when (v) {
                    is Boolean -> sb.append(k).append("\tb\t").append(v).append('\n')
                    is Int -> sb.append(k).append("\ti\t").append(v).append('\n')
                    is String -> sb.append(k).append("\ts\t").append(v).append('\n')
                    is Set<*> -> sb.append(k).append("\tss\t")
                        .append(v.joinToString(",")).append('\n')
                }
            }
            return sb.toString()
        }

        /** Only the visual keys (theme, colours, sizes) — for theme.txt. */
        private fun exportTheme(): String {
            val visual = ThemePresets.VISUAL_KEYS + "theme"
            val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val sb = StringBuilder()
            for ((k, v) in p.all) {
                if (k !in visual) continue
                when (v) {
                    is Boolean -> sb.append(k).append("\tb\t").append(v).append('\n')
                    is Int -> sb.append(k).append("\ti\t").append(v).append('\n')
                    is String -> sb.append(k).append("\ts\t").append(v).append('\n')
                }
            }
            return sb.toString()
        }

        private fun importSettings(text: String): Int {
            val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val e = p.edit()
            var n = 0
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val (k, t, v) = parts
                try {
                    when (t) {
                        "b" -> e.putBoolean(k, v.toBoolean())
                        "i" -> e.putInt(k, v.toInt())
                        "s" -> e.putString(k, v)
                        "ss" -> e.putStringSet(
                            k, v.split(',').filter { it.isNotEmpty() }.toSet()
                        )
                        else -> continue
                    }
                    n++
                } catch (_: Exception) {
                }
            }
            e.apply()
            return n
        }

        private fun resetToDefaults() {
            val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
            p.edit().clear().apply()
            try {
                val text = requireContext().assets.open("default_settings.txt")
                    .bufferedReader().readText()
                importSettings(text)
            } catch (_: Exception) {
            }
        }

        // ------------------------------------------------------ SAF helpers
        private fun chooseLanguage(then: () -> Unit) {
            val names = Layouts.ALL.map { it.nativeName }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_languages)
                .setItems(names) { _, which ->
                    pendingLang = Layouts.ALL[which].code
                    then()
                }
                .show()
        }

        @Suppress("DEPRECATION")
        private fun openDocument(requestCode: Int) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "text/*"
                startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        @Suppress("DEPRECATION")
        private fun createDocument(requestCode: Int, name: String) {
            try {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TITLE, name)
                startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode != Activity.RESULT_OK) return
            val uri = data?.data ?: return
            val ctx = requireContext()
            try {
                when (requestCode) {
                    REQ_DICT_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = WordStore(ctx, pendingLang).importText(text)
                        AutoTextStore.bumpDataVersion(ctx)
                        toast(getString(R.string.imported_n_words, n))
                    }
                    REQ_DICT_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(WordStore(ctx, pendingLang).exportText())
                        }
                        toast(getString(R.string.done))
                    }
                    REQ_AUTOTEXT_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = AutoTextStore(ctx).importText(text)
                        AutoTextStore.bumpDataVersion(ctx)
                        toast(getString(R.string.imported_n_words, n))
                    }
                    REQ_AUTOTEXT_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(AutoTextStore(ctx).exportText())
                        }
                        toast(getString(R.string.done))
                    }
                    REQ_SETTINGS_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(exportSettings())
                        }
                        toast(getString(R.string.done))
                    }
                    REQ_SETTINGS_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = importSettings(text)
                        toast(getString(R.string.imported_n_words, n))
                    }
                    REQ_THEME_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(exportTheme())
                        }
                        toast(getString(R.string.done))
                    }
                    REQ_THEME_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = importSettings(text)
                        toast(getString(R.string.imported_n_words, n))
                    }
                }
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        private fun toast(msg: String) {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        companion object {
            private const val REQ_DICT_IMPORT = 11
            private const val REQ_DICT_EXPORT = 12
            private const val REQ_AUTOTEXT_IMPORT = 13
            private const val REQ_AUTOTEXT_EXPORT = 14
            private const val REQ_SETTINGS_EXPORT = 15
            private const val REQ_SETTINGS_IMPORT = 16
            private const val REQ_THEME_EXPORT = 17
            private const val REQ_THEME_IMPORT = 18

            fun create(screen: String): SettingsFragment {
                val f = SettingsFragment()
                val b = Bundle()
                b.putString("screen", screen)
                f.arguments = b
                return f
            }
        }
    }
}
