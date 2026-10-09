package com.elyas.multiling

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

/**
 * One settings area (look, typing, colours…) under a large collapsing app
 * bar. The index of areas is the Settings tab of [MainActivity]; this screen
 * shows a single area and anything nested inside it. Look, size and colour
 * areas dock a live keyboard preview at the bottom.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        /** Open one settings area, e.g. "look", "typing", "colors". */
        fun intent(c: android.content.Context, screen: String): Intent =
            Intent(c, SettingsActivity::class.java).putExtra("open_screen", screen)

        /** Title for each screen, shown in the large collapsing app bar. */
        fun titleOf(screen: String): Int = when (screen) {
            "langs" -> R.string.pref_cat_langs
            "look" -> R.string.pref_cat_look
            "colors" -> R.string.pref_cat_colors
            "col_keys" -> R.string.pref_col_cat_key_group
            "col_special" -> R.string.pref_col_cat_special_group
            "col_text" -> R.string.pref_col_cat_text
            "sizes" -> R.string.pref_cat_sizes
            "typing" -> R.string.pref_cat_typing
            "autotext" -> R.string.pref_cat_autotext
            "control" -> R.string.pref_cat_control
            "feedback" -> R.string.pref_cat_feedback
            "backup" -> R.string.pref_cat_dict
            else -> R.string.settings_title
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var preview: KeyboardView
    private lateinit var previewCard: View
    private lateinit var collapsing: com.google.android.material.appbar.CollapsingToolbarLayout
    private var rootScreen = "look"

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePresets.bootstrap(this)

        // Without a screen (the system's "keyboard settings" gear, or the
        // keyboard's own settings key) the right destination is the
        // Settings tab of the main app, which lists every area.
        val screen = intent?.getStringExtra("open_screen")
        if (screen.isNullOrEmpty() || screen == "main") {
            startActivity(MainActivity.intent(this, MainActivity.TAB_SETTINGS))
            finish()
            return
        }
        rootScreen = screen

        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_settings)
        collapsing = findViewById(R.id.collapsing)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        previewCard = findViewById(R.id.preview_card)
        preview = KeyboardView(this)
        findViewById<FrameLayout>(R.id.preview_holder).addView(preview)
        // the preview panel is the lowest thing on screen: it clears the
        // navigation bar; without it the list does
        Ui.padForBars(findViewById(R.id.preview_holder), top = false, bottom = true)
        Ui.padForBars(findViewById(R.id.settings_host), top = false, bottom = true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_host, SettingsFragment.create(screen))
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener { onScreenChanged() }
        onScreenChanged()
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

    /** Go one level deeper inside this activity (colours → key colours). */
    fun openScreen(screen: String, @Suppress("UNUSED_PARAMETER") title: CharSequence) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.tab_in, R.anim.tab_out, R.anim.tab_in, R.anim.tab_out)
            .replace(R.id.settings_host, SettingsFragment.create(screen))
            .addToBackStack(screen)
            .commit()
    }

    private fun currentScreen(): String {
        val i = supportFragmentManager.backStackEntryCount
        return if (i == 0) rootScreen
        else supportFragmentManager.getBackStackEntryAt(i - 1).name ?: rootScreen
    }

    private fun onScreenChanged() {
        val screen = currentScreen()
        collapsing.title = getString(titleOf(screen))
        val show = screen == "look" || screen == "sizes" || screen == "colors" ||
            screen.startsWith("col_")
        previewCard.visibility = if (show) View.VISIBLE else View.GONE
        if (show) refreshPreview()
    }

    /** Live keyboard preview reflecting the current preferences. */
    private fun refreshPreview() {
        if (!::previewCard.isInitialized || previewCard.visibility != View.VISIBLE) return
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
                else -> R.xml.prefs_look
            }
            setPreferencesFromResource(res, rootKey)
            if (screen == "autotext") wireAutoText()
            if (screen == "look") wireLook()
            if (screen == "backup") wireBackup()
            if (screen == "colors") wireColors()
            if (screen == "col_keys") wireColorGroup("col_key", "col_key_grad_on")
            if (screen == "col_special") wireColorGroup("col_special", "col_special_grad_on")
            if (screen == "typing") wireTyping()
            PrefIcons.apply(preferenceScreen)
        }

        /**
         * The rows of each section sit in one rounded card, like a native
         * settings screen, with the section heading above it. The cards are
         * drawn by [PrefGroupCards] behind the rows rather than being real
         * views, which keeps the stock preference list and its recycling.
         */
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val c = requireContext()
            val list = listView ?: return
            val pad = Ui.dp(c, 16f)
            list.setPadding(pad, Ui.dp(c, 4f), pad, Ui.dp(c, 24f))
            list.clipToPadding = false
            list.isVerticalScrollBarEnabled = false
            list.overScrollMode = android.view.View.OVER_SCROLL_NEVER
            list.addItemDecoration(PrefGroupCards(c))
            // long-press any row: a tooltip with its name and what it does
            list.addOnChildAttachStateChangeListener(object :
                androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(v: android.view.View) {
                    val t = v.findViewById<android.widget.TextView>(android.R.id.title)?.text
                    val s = v.findViewById<android.widget.TextView>(android.R.id.summary)
                        ?.takeIf { it.visibility == android.view.View.VISIBLE }?.text
                    if (v.isClickable || v.isLongClickable) Ui.tip(v, Ui.tipText(t, s))
                }
                override fun onChildViewDetachedFromWindow(v: android.view.View) {}
            })
            // rows arrive one after another the first time the screen shows
            if (savedInstanceState == null) {
                list.layoutAnimation =
                    android.view.animation.AnimationUtils.loadLayoutAnimation(c, R.anim.list_rise)
            }
            setDivider(null)
            setDividerHeight(0)
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
                startActivity(MainActivity.intent(requireContext(), MainActivity.TAB_SHORTCUTS))
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

        /**
         * "Learned words": everything the keyboard picked up from typing, per
         * language, with a checkbox each. Removing blocks the word, so the
         * keyboard does not quietly learn it again.
         */
        private fun wireTyping() {
            findPreference<Preference>("learned_words")?.setOnPreferenceClickListener {
                showLearnedWords()
                true
            }
        }

        private fun showLearnedWords() {
            val ctx = requireContext()
            val items = ArrayList<Pair<String, String>>()   // code, word
            for (l in Layouts.ALL) {
                for ((w, _) in WordStore(ctx, l.code).learnedWords()) items.add(l.code to w)
            }
            if (items.isEmpty()) {
                toast(getString(R.string.learned_none))
                return
            }
            val labels = items.map { (code, w) -> "$w   ·  ${code.uppercase()}" }.toTypedArray()
            val checked = BooleanArray(items.size)
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.pref_learned_words)
                .setMultiChoiceItems(labels, checked) { _, which, on -> checked[which] = on }
                .setPositiveButton(R.string.learned_delete) { _, _ ->
                    val byLang = HashMap<String, MutableList<String>>()
                    for (i in items.indices) if (checked[i]) {
                        byLang.getOrPut(items[i].first) { ArrayList() }.add(items[i].second)
                    }
                    var n = 0
                    for ((code, words) in byLang) {
                        val ws = WordStore(ctx, code)
                        for (w in words) { ws.forget(w); n++ }
                        ws.save()
                    }
                    if (n > 0) {
                        AutoTextStore.bumpDataVersion(ctx)
                        toast(getString(R.string.learned_deleted, Ui.digits(ctx, n)))
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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
                MaterialAlertDialogBuilder(requireContext())
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
            findPreference<Preference>("clean_typos")?.setOnPreferenceClickListener {
                cleanLearnedTypos()
                true
            }
            findPreference<Preference>("clear_learned")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
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

        /**
         * Sweep the learned dictionary for words that are one slip away from
         * a real word. The old rule saved anything typed twice, so a month of
         * use leaves hundreds of habitual mistypings behind — and once saved,
         * a misspelling stops being correctable and starts being suggested.
         * Shows what would go before removing anything.
         */
        private fun cleanLearnedTypos() {
            val ctx = requireContext()
            val found = LinkedHashMap<String, MutableList<String>>()
            var total = 0
            for (l in Layouts.ALL) {
                val bad = WordStore(ctx, l.code).suspiciousLearned()
                if (bad.isNotEmpty()) {
                    found[l.code] = bad.toMutableList()
                    total += bad.size
                }
            }
            if (total == 0) {
                toast(getString(R.string.clean_typos_none))
                return
            }
            val preview = found.values.flatten().take(40).joinToString("، ")
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.pref_clean_typos)
                .setMessage(getString(R.string.clean_typos_q, total) + "\n\n" + preview)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    for ((code, words) in found) {
                        WordStore(ctx, code).forgetAll(words)
                    }
                    AutoTextStore.bumpDataVersion(ctx)
                    toast(getString(R.string.done))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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
            MaterialAlertDialogBuilder(requireContext())
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
