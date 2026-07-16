package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager

/**
 * The input method service: builds rows for the current language/mode,
 * commits text, and implements shift, symbols, edit/control panel, numpad,
 * emoji page, language switching, suggestions with next-word prediction,
 * AutoText expansion, double-space period, arrow keys and feedback.
 */
class MultilingIME : InputMethodService(), KeyboardView.Listener {

    companion object {
        /** long-press token on the enter key: commit a real newline */
        private const val SHIFT_ENTER = "⇧↵"

        /** separators that delete a space left before them ("word ،"→"word،") */
        private val TIGHT_PUNCT = setOf("،", ".", ":", "۔", "؛")
    }

    private enum class Mode {
        LETTERS, SYM1, SYM2, EDIT, NUMPAD, EMOJI, EMOJI_SEARCH, CLIPBOARD, KAOMOJI
    }

    private var keyboardView: KeyboardView? = null
    private var emojiPanel: EmojiPanel? = null
    private var emojiHolder: android.widget.FrameLayout? = null
    private val emojiQuery = StringBuilder()
    private var emojiKeywords: List<Pair<String, List<String>>>? = null
    private var baseKeyHeightDp = 72
    private var navGapAuto = true
    private var manualBottomGapDp = 10
    private var rootView: LinearLayout? = null
    private var suggestionBar: LinearLayout? = null
    private var suggestionScroll: HorizontalScrollView? = null

    private var languages: List<Language> = Layouts.ALL
    private var langIndex = 0
    private var mode = Mode.LETTERS
    private var shift = 0 // 0 off, 1 once, 2 locked
    private var selectMode = false // edit panel: arrows extend the selection
    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L

    // settings snapshot
    private var vibrateOn = true
    private var vibrateMs = 20L
    private var soundOn = false
    private var soundVol = 0.6f
    private var suggestionsOn = true
    private var learnWordsOn = true
    private var seedDictOn = true
    private var bigramsOn = true
    private var autotextOn = true
    private var doubleSpacePeriod = true
    private var autoCapsOn = true
    private var arrowsOn = true
    private var suggFontSp = 17f

    private var soundPool: android.media.SoundPool? = null
    private var popSoundId = 0
    private var soundType = "bubble"

    private var autocorrectOn = true
    private var isPasswordField = false
    private var revertOriginal: String? = null
    private var revertCorrected: String? = null
    private val rejectedWords = HashSet<String>()
    private var lastDataVersion = -1
    private var bestCandidate: WordStore.Cand? = null
    private val confusionMaps = HashMap<String, Map<Char, Set<Char>>>()

    private var clipStore: ClipboardStore? = null
    // clip chip: with clip_chip_repeat OFF the chip disappears after one use
    // — permanently, across every field and app (persisted as a fingerprint)
    private var clipChipRepeat = false
    private var consumedClipKey: String? = null

    private fun clipKey(t: String) = "${t.hashCode()}:${t.length}"

    private val wordStores = HashMap<String, WordStore>()
    private val preloadedLangs = HashSet<String>()
    private var autoText: AutoTextStore? = null
    private val wordBuffer = StringBuilder()
    private var lastWord = ""

    private val lang: Language get() = languages[langIndex]

    private fun store(): WordStore =
        wordStores.getOrPut(lang.code) { WordStore(this, lang.code) }

    private fun autoTextStore(): AutoTextStore =
        autoText ?: AutoTextStore(this).also { autoText = it }

    private fun confusion(): Map<Char, Set<Char>> =
        confusionMaps.getOrPut(lang.code) { Layouts.confusionMap(lang) }

    private fun clipboardStore(): ClipboardStore =
        clipStore ?: ClipboardStore(this).also { clipStore = it }

    // ------------------------------------------------------------ lifecycle
    override fun onCreate() {
        super.onCreate()
        ThemePresets.bootstrap(this)
        bootstrapAutoText()
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.addPrimaryClipChangedListener {
                captureClipboard(cm)
                // refresh the strip so a freshly copied text shows at once
                if (keyboardView?.visibility == View.VISIBLE) updateSuggestions()
            }
        } catch (_: Exception) {
        }
    }

    /** First run: load the bundled default AutoText shortcuts. */
    private fun bootstrapAutoText() {
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        if (p.getBoolean("autotext_init", false)) return
        p.edit().putBoolean("autotext_init", true).apply()
        try {
            val store = autoTextStore()
            if (store.all().isEmpty()) {
                val text = assets.open("default_autotext.txt")
                    .bufferedReader().readText()
                store.importText(text)
            }
        } catch (_: Exception) {
        }
    }

    /** Read the current system clipboard text into the history store. */
    private fun captureClipboard(cm: android.content.ClipboardManager) {
        val text = try {
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                ?.trim()?.ifEmpty { null }
        } catch (_: Exception) { null }
        if (text != null) {
            clipboardStore().add(text)
            // a fresh copy re-arms the one-time chip
            consumedClipKey = null
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().remove("consumed_clip").apply()
        }
    }

    override fun onCreateInputView(): View {
        val kv = KeyboardView(this)
        kv.listener = this
        keyboardView = kv

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        suggestionBar = bar
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        suggestionScroll = scroll

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val density = resources.displayMetrics.density
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (40 * density).toInt()
            )
        )
        root.addView(
            kv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val holder = android.widget.FrameLayout(this)
        holder.visibility = View.GONE
        emojiHolder = holder
        root.addView(
            holder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootView = root
        // re-apply the auto gap whenever the system insets change (rotation,
        // switching between gesture and 3-button navigation, etc.)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            if (navGapAuto) applyBottomGap()
            insets
        }
        applySettings()
        rebuildKeyboard()
        return root
    }

    /** Apply user custom key colours + gradients (or fall back to the theme). */
    private fun applyCustomColors(
        kv: KeyboardView,
        p: android.content.SharedPreferences
    ) {
        val custom = p.getBoolean("col_custom", false)
        kv.customColors = custom
        if (custom) {
            val t = kv.theme
            kv.colKeyFill = p.getInt("col_key", t.keyFill)
            kv.colKeyFill2 =
                if (KeyboardView.gradientOn(p, "col_key_mode", "col_key_grad_on"))
                    p.getInt("col_key_grad", 0) else 0
            kv.colKeyGradStyle = p.getString("col_key_grad_style", "linear") ?: "linear"
            kv.colKeyGradDir = p.getString("col_key_grad_dir", "v") ?: "v"
            kv.colSpecialFill = p.getInt("col_special", t.specialFill)
            kv.colSpecialFill2 =
                if (KeyboardView.gradientOn(p, "col_special_mode", "col_special_grad_on"))
                    p.getInt("col_special_grad", 0) else 0
            kv.colSpecialGradStyle = p.getString("col_special_grad_style", "linear") ?: "linear"
            kv.colSpecialGradDir = p.getString("col_special_grad_dir", "v") ?: "v"
            kv.colTextColor = p.getInt("col_text", t.text)
            kv.colHintColor = p.getInt("col_hint", t.hint)
            kv.colBg = p.getInt("col_bg", t.background)
        }
    }

    /** A round-fill vector drawable, tinted and sized (for panel chrome). */
    private fun tintedIcon(res: Int, color: Int, sizePx: Int): android.graphics.drawable.Drawable? {
        val d = try {
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this, res)?.mutate()
        } catch (_: Exception) { null } ?: return null
        androidx.core.graphics.drawable.DrawableCompat.setTint(d, color)
        d.setBounds(0, 0, sizePx, sizePx)
        return d
    }

    /**
     * How far the keyboard must lift so no key sits under the system's
     * bottom controls. In 3-button navigation the framework already reserves
     * that space (tappable area > 0) so no extra gap is needed; in gesture
     * navigation the home bar overlaps the keyboard, so we add the nav-bar
     * inset as the gap.
     */
    /**
     * Exact pixels the keyboard window overlaps the system navigation area
     * (3-button bar OR gesture pill — any size, any device). The keyboard
     * window is laid out edge-to-edge on modern Android, so whenever its
     * bottom reaches the bottom of the screen the navigation inset must be
     * padded away; when the system already keeps the window above the bar
     * (older devices) no extra gap is added.
     */
    private fun autoGapPx(): Int {
        return try {
            val decor = window?.window?.decorView ?: return 0
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                ?: return 0
            // gesture mode: while the keyboard is open the system draws its
            // own hide/switch buttons at the very bottom — that TAPPABLE
            // strip can be taller than the plain gesture pill, so take the
            // larger of the two insets
            val navOnly = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            ).bottom
            val tappable = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.tappableElement()
            ).bottom
            val nav = maxOf(navOnly, tappable)
            if (nav <= 0) return 0
            val loc = IntArray(2)
            decor.getLocationOnScreen(loc)
            val windowBottom = loc[1] + decor.height
            val screenBottom = realScreenHeight()
            // pad only when the window really extends under the nav area
            if (screenBottom <= 0 || windowBottom >= screenBottom - 4) {
                nav.coerceAtMost((64 * resources.displayMetrics.density).toInt())
            } else 0
        } catch (_: Exception) { 0 }
    }

    private fun realScreenHeight(): Int {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                wm.currentWindowMetrics.bounds.height()
            } else {
                val p = android.graphics.Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(p)
                p.y
            }
        } catch (_: Exception) { 0 }
    }

    /** Bottom padding under the keys: auto = nav-bar overlap, else manual.
     *  Applied to the emoji/clipboard holder too so those panels keep the
     *  same gap above the system bar. */
    private fun applyBottomGap() {
        val kv = keyboardView ?: return
        val density = resources.displayMetrics.density
        val pad = if (navGapAuto) autoGapPx() else (manualBottomGapDp * density).toInt()
        kv.setPadding(0, 0, 0, pad)
        kv.requestLayout()
        emojiHolder?.setPadding(0, 0, 0, pad)
        emojiHolder?.requestLayout()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // insets/positions are only final once the window is up — re-measure
        if (navGapAuto) keyboardView?.post { applyBottomGap() }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applySettings()
        // number/phone/date fields automatically get the number pad;
        // the edit/control panel survives switching fields and apps
        val inputType = info?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        isPasswordField = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        if (mode != Mode.EDIT) {
            mode = when (cls) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME -> Mode.NUMPAD
                else -> Mode.LETTERS
            }
        }
        shift = 0
        selectMode = false
        wordBuffer.setLength(0)
        lastWord = ""
        // pick up whatever was copied before the keyboard opened
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            captureClipboard(cm)
        } catch (_: Exception) {
        }
        applyBottomGap()
        updateAutoCaps()
        rebuildKeyboard()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        for (s in wordStores.values) s.save()
        autoText?.save()
        keyboardView?.dismissPopups()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (newSelStart != newSelEnd) {
            // an active selection has no "current word"
            wordBuffer.setLength(0)
            updateSuggestions()
        } else {
            // re-derive the current word from around the cursor, so moving
            // the cursor into a word brings back its suggestions
            syncBufferFromCursor()
        }
    }

    private fun syncBufferFromCursor() {
        val ic = currentInputConnection ?: return
        val before = try { ic.getTextBeforeCursor(32, 0) } catch (_: Exception) { null } ?: ""
        var start = before.length
        while (start > 0 && Character.isLetter(before[start - 1])) start--
        val word = before.substring(start)
        if (word != wordBuffer.toString()) {
            wordBuffer.setLength(0)
            wordBuffer.append(word)
        }
        // always refresh: the clipboard chip must re-evaluate on every cursor
        // move (e.g. entering a fresh empty line where the buffer was already
        // empty), so it never gets stuck showing or hidden
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------- settings
    private fun applySettings() {
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        val kv = keyboardView ?: return
        kv.theme = KeyboardView.themeByName(p.getString("theme", "dark") ?: "dark")
        applyCustomColors(kv, p)
        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        baseKeyHeightDp =
            if (landscape) p.getInt("key_height_land", 42) else p.getInt("key_height", 72)
        kv.keyHeightDp = baseKeyHeightDp
        kv.arrowRowScale = p.getInt("arrow_height", 69) / 100f
        kv.fontScale = p.getInt("font_scale", 70) / 100f
        kv.hintScale = p.getInt("hint_scale", 96) / 100f
        kv.cornerRadiusDp = p.getInt("corner_radius", 6)
        kv.keyGapDp = p.getInt("key_gap", 2) / 1.33f
        kv.showHints = p.getBoolean("hints", true)
        kv.showPreview = p.getBoolean("preview", true)
        kv.keyBorder = p.getBoolean("key_border", false)
        kv.spaceSwipeEnabled = p.getBoolean("space_swipe", true)
        kv.splitMode = p.getBoolean("split_kb", false)
        kv.longPressTimeout = (p.getString("longpress", "200") ?: "200").toLong()
        val density = resources.displayMetrics.density
        navGapAuto = p.getBoolean("nav_gap_auto", true)
        manualBottomGapDp = p.getInt("bottom_gap", 10)
        applyBottomGap()

        vibrateOn = p.getBoolean("vibrate", true)
        vibrateMs = p.getInt("vibrate_ms", 20).toLong()
        soundOn = p.getBoolean("sound", true)
        soundVol = p.getInt("sound_vol", 5) / 100f
        soundType = p.getString("sound_type", "bubble") ?: "bubble"
        if (soundOn && soundType != "system") initSoundPool()
        suggestionsOn = p.getBoolean("suggestions", true)
        learnWordsOn = p.getBoolean("learn_words", true)
        seedDictOn = p.getBoolean("seed_dict", true)
        bigramsOn = p.getBoolean("bigrams", true)
        autotextOn = p.getBoolean("autotext_on", true)
        autocorrectOn = p.getBoolean("autocorrect", true)
        doubleSpacePeriod = p.getBoolean("double_space", true)
        autoCapsOn = p.getBoolean("autocaps", true)
        suggFontSp = p.getInt("sugg_font", 17).toFloat()
        arrowsOn = p.getBoolean("arrows", true)
        clipChipRepeat = p.getBoolean("clip_chip_repeat", false)
        consumedClipKey = p.getString("consumed_clip", null)

        val dv = p.getInt("data_version", 0)
        if (dv != lastDataVersion) {
            lastDataVersion = dv
            wordStores.clear()
            preloadedLangs.clear()
            autoText = null
        }

        val enabled = p.getStringSet("languages", null)
            ?: setOf("ps", "fa", "ar", "en")
        val list = if (enabled.isEmpty()) Layouts.ALL
        else Layouts.ALL.filter { enabled.contains(it.code) }
        languages = if (list.isEmpty()) Layouts.ALL else list
        if (langIndex >= languages.size) langIndex = 0

        // decompress + parse the frequency dictionaries (and the clipboard
        // history) off the UI thread so the first keystroke doesn't stutter
        val toLoad = languages.filter { preloadedLangs.add(it.code) }
            .map { l -> wordStores.getOrPut(l.code) { WordStore(this, l.code) } }
        val clips = clipboardStore()
        if (toLoad.isNotEmpty()) {
            Thread {
                try { clips.preload() } catch (_: Exception) {}
                for (s in toLoad) try { s.preload() } catch (_: Exception) {}
            }.start()
        }

        rootView?.setBackgroundColor(kv.resolvedBackground)
        suggestionScroll?.visibility = if (suggestionsOn) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------- keyboard build
    private fun enterLabel(): String {
        val info = currentInputEditorInfo ?: return "↵"
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "لټون"
            EditorInfo.IME_ACTION_SEND -> "لېږل"
            EditorInfo.IME_ACTION_NEXT -> "بل ⇥"
            EditorInfo.IME_ACTION_GO -> "ورتګ"
            EditorInfo.IME_ACTION_DONE -> "بشپړ"
            else -> "↵"
        }
    }

    /** Control-panel bottom row: the space bar and its neighbours are
     *  replaced by Undo/Redo (restore wrongly deleted text). */
    private fun buildEditBottomRow(): List<KeyDef> {
        val symLabel = if (lang.digits[0] == '0') "?123" else "۱۲۳"
        return listOf(
            KeyDef(symLabel, code = Keys.SYM, width = 1.5f),
            KeyDef("Undo", code = Keys.UNDO, width = 3f),
            KeyDef("Redo", code = Keys.REDO, width = 3f),
            KeyDef(enterLabel(), null, listOf(SHIFT_ENTER), code = Keys.ENTER, width = 1.5f, hint = SHIFT_ENTER)
        )
    }

    /** Back-to-letters label: ZWNJ keeps the Arabic letters unjoined. */
    private fun abcLabel(): String = if (lang.rtl) "اب‌ت" else "Abc"

    private fun buildBottomRow(): List<KeyDef> {
        val symLabel = if (lang.digits[0] == '0') "?123" else "۱۲۳"
        return listOf(
            KeyDef(symLabel, code = Keys.SYM, width = 1.5f, hintIcon = Keys.ICON_MIC),
            lang.extraKey,
            KeyDef(lang.nativeName, code = Keys.SPACE, width = 4f),
            KeyDef(lang.period, null, lang.periodAlts),
            KeyDef(enterLabel(), null, listOf(SHIFT_ENTER), code = Keys.ENTER, width = 1.5f, hint = SHIFT_ENTER)
        )
    }

    private fun buildSymBottomRow(): List<KeyDef> = listOf(
        KeyDef(abcLabel(), code = Keys.ABC, width = 1.5f),
        // the letters layout has the comma here, so the 123 layout gets
        // the tatweel on the same key
        if (lang.rtl) KeyDef("ـ", null, listOf("،", "؛", ","))
        else KeyDef(",", null, listOf("،", ";")),
        KeyDef(lang.nativeName, code = Keys.SPACE, width = 4f),
        KeyDef(lang.period, null, lang.periodAlts),
        KeyDef(enterLabel(), null, listOf(SHIFT_ENTER), code = Keys.ENTER, width = 1.5f, hint = SHIFT_ENTER)
    )

    private fun arrowRow(): List<KeyDef> = listOf(
        KeyDef("▲", code = Keys.ARROW_UP, repeatable = true),
        KeyDef("▼", code = Keys.ARROW_DOWN, repeatable = true),
        KeyDef("◀", code = Keys.ARROW_LEFT, repeatable = true),
        KeyDef("▶", code = Keys.ARROW_RIGHT, repeatable = true)
    )

    private fun currentRows(): List<List<KeyDef>> {
        val rows = ArrayList<List<KeyDef>>()
        when (mode) {
            Mode.LETTERS -> {
                rows.addAll(lang.rows)
                rows.add(buildBottomRow())
                if (arrowsOn) rows.add(arrowRow())
            }
            Mode.SYM1 -> {
                rows.addAll(Layouts.symbols1(lang))
                rows.add(buildSymBottomRow())
                if (arrowsOn) rows.add(arrowRow())
            }
            Mode.SYM2 -> {
                rows.addAll(Layouts.symbols2())
                rows.add(buildSymBottomRow())
                if (arrowsOn) rows.add(arrowRow())
            }
            Mode.EDIT -> {
                rows.addAll(Layouts.editPanel())
                rows.add(buildEditBottomRow())
            }
            Mode.NUMPAD -> rows.addAll(Layouts.numPad(lang, enterLabel()))
            Mode.EMOJI -> {}
            Mode.KAOMOJI -> {}
            Mode.CLIPBOARD -> {}
            Mode.EMOJI_SEARCH -> {
                rows.addAll(Layouts.ENGLISH.rows)
                rows.add(
                    listOf(
                        KeyDef("😀", code = Keys.EMOJI, width = 1.5f),
                        KeyDef(",", null, listOf("'")),
                        KeyDef("لټون…", code = Keys.SPACE, width = 4f),
                        KeyDef("."),
                        KeyDef("↵", code = Keys.ENTER, width = 1.5f)
                    )
                )
            }
        }
        return rows
    }

    /** Compute what each key should display given shift state. */
    private fun displayFor(rows: List<List<KeyDef>>): List<List<String>> {
        return rows.map { row ->
            row.map { key ->
                when {
                    key.code != 0 -> key.label
                    lang.code == "en" && mode == Mode.LETTERS ->
                        if (shift > 0) key.label.uppercase() else key.label
                    shift > 0 && key.shifted != null && mode == Mode.LETTERS -> key.shifted
                    else -> key.label
                }
            }
        }
    }

    /** Total keyboard height stays the same in every layout/mode. */
    private fun heightUnits(rowsCount: Int, withArrows: Boolean): Float =
        rowsCount + (if (withArrows) keyboardView?.arrowRowScale ?: 1f else 0f)

    private fun rebuildKeyboard() {
        val kv = keyboardView ?: return
        if (mode == Mode.EMOJI) {
            showEmojiPanel()
            return
        }
        if (mode == Mode.KAOMOJI) {
            showKaomojiPanel()
            return
        }
        if (mode == Mode.CLIPBOARD) {
            showClipboardPanel()
            return
        }
        emojiHolder?.visibility = View.GONE
        kv.visibility = View.VISIBLE

        val rows = currentRows()
        // scale the key height so control/numbers/symbol layouts occupy the
        // exact same total height as the letters layout
        val lettersUnits = heightUnits(lang.rows.size + 1, arrowsOn)
        val arrowInMode = arrowsOn &&
            (mode == Mode.LETTERS || mode == Mode.SYM1 || mode == Mode.SYM2 ||
                mode == Mode.EMOJI_SEARCH)
        val bodyRows = rows.size - (if (arrowInMode) 1 else 0)
        val modeUnits = heightUnits(bodyRows, arrowInMode)
        kv.keyHeightDp = (baseKeyHeightDp * lettersUnits / modeUnits).toInt()

        kv.shiftState = if (mode == Mode.EDIT) (if (selectMode) 2 else 0) else shift
        kv.setKeyboard(rows, displayFor(rows))
    }

    private fun showClipboardPanel() {
        val kv = keyboardView ?: return
        val holder = emojiHolder ?: return
        kv.visibility = View.GONE
        val density = resources.displayMetrics.density
        val theme = kv.theme

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(kv.resolvedBackground)

        // header: title + clear-all
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        fun headerBtn(text: String, iconRes: Int, weight: Float, click: () -> Unit): TextView {
            val tv = TextView(this)
            tv.text = text
            tv.gravity = android.view.Gravity.CENTER
            tv.textSize = 15f
            tv.setTextColor(theme.text)
            if (iconRes != 0) {
                val d = tintedIcon(iconRes, theme.text, (18 * density).toInt())
                tv.setCompoundDrawables(d, null, null, null)
                tv.compoundDrawablePadding = (4 * density).toInt()
            }
            tv.setOnClickListener { click() }
            header.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight))
            return tv
        }
        headerBtn("بیرته", R.drawable.ic_key_back, 1f) {
            mode = Mode.LETTERS; rebuildKeyboard(); updateSuggestions()
        }
        val title = headerBtn("کلیپ بورډ", 0, 2f) {}
        title.setTextColor(theme.hint)
        headerBtn("پاکول", R.drawable.ic_key_trash, 1f) {
            clipboardStore().clear()
            mode = Mode.CLIPBOARD
            rebuildKeyboard()
        }
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (42 * density).toInt()))

        // items: 3-column grid; the pin in a cell's corner keeps that item
        val scroll = android.widget.ScrollView(this)
        val grid = android.widget.GridLayout(this)
        grid.columnCount = 3
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val items = clipboardStore().all()
        if (items.isEmpty()) {
            val tv = TextView(this)
            tv.text = "کلیپ بورډ تش دی — یو متن کاپي کړئ"
            tv.setTextColor(theme.hint)
            tv.textSize = 15f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding((10 * density).toInt(), (30 * density).toInt(),
                (10 * density).toInt(), 0)
            scroll.addView(tv)
        } else {
            val (keyCol, _) = panelColors(kv)
            val cellMargin = (4 * density).toInt()
            val cellW = (resources.displayMetrics.widthPixels -
                6 * cellMargin - (16 * density).toInt()) / 3
            for (item in items) {
                val pinned = clipboardStore().isPinned(item)
                val cell = android.widget.FrameLayout(this)
                val bg = android.graphics.drawable.GradientDrawable()
                bg.setColor(keyCol)
                bg.cornerRadius = 12 * density
                if (pinned) bg.setStroke((1.5f * density).toInt(), theme.accent)
                cell.background = bg

                val tv = TextView(this)
                tv.text = item.take(110)
                tv.maxLines = 3
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
                tv.setTextColor(theme.text)
                tv.textSize = 12.5f
                tv.setPadding((10 * density).toInt(), (22 * density).toInt(),
                    (10 * density).toInt(), (8 * density).toInt())
                cell.addView(tv, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

                val pin = android.widget.ImageView(this)
                pin.setImageDrawable(tintedIcon(
                    R.drawable.ic_key_pin,
                    if (pinned) theme.accent else (theme.hint and 0x88FFFFFF.toInt()),
                    (15 * density).toInt()))
                pin.setPadding((6 * density).toInt(), (6 * density).toInt(),
                    (6 * density).toInt(), (6 * density).toInt())
                pin.setOnClickListener {
                    feedback()
                    clipboardStore().togglePin(item)
                    mode = Mode.CLIPBOARD
                    rebuildKeyboard()
                }
                cell.addView(pin, android.widget.FrameLayout.LayoutParams(
                    (28 * density).toInt(), (28 * density).toInt(),
                    android.view.Gravity.TOP or android.view.Gravity.START))

                cell.setOnClickListener {
                    currentInputConnection?.commitText(item, 1)
                    feedback()
                    mode = Mode.LETTERS
                    rebuildKeyboard()
                    updateSuggestions()
                }
                cell.setOnLongClickListener {
                    clipboardStore().remove(item)
                    mode = Mode.CLIPBOARD
                    rebuildKeyboard()
                    true
                }
                val glp = android.widget.GridLayout.LayoutParams()
                glp.width = cellW
                glp.height = (78 * density).toInt()
                glp.setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                grid.addView(cell, glp)
            }
            scroll.addView(grid)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        holder.removeAllViews()
        val lettersUnits = heightUnits(lang.rows.size + 1, arrowsOn)
        val h = (baseKeyHeightDp * lettersUnits * density).toInt()
        holder.addView(root, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, h))
        holder.visibility = View.VISIBLE
    }

    /** Panel colours follow the custom key colours when they are on. */
    private fun panelColors(kv: KeyboardView): Pair<Int, Int> {
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val custom = p.getBoolean("col_custom", false)
        val key = if (custom) p.getInt("col_key", kv.theme.keyFill) else kv.theme.keyFill
        val special = if (custom) p.getInt("col_special", kv.theme.specialFill)
            else kv.theme.specialFill
        return key to special
    }

    private fun showPanel(panel: EmojiPanel) {
        val holder = emojiHolder ?: return
        emojiPanel = panel
        holder.removeAllViews()
        val density = resources.displayMetrics.density
        val lettersUnits = heightUnits(lang.rows.size + 1, arrowsOn)
        val h = (baseKeyHeightDp * lettersUnits * density).toInt()
        holder.addView(
            panel,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, h
            )
        )
        holder.visibility = View.VISIBLE
    }

    private fun showEmojiPanel() {
        val kv = keyboardView ?: return
        kv.visibility = View.GONE
        val (keyCol, specialCol) = panelColors(kv)
        showPanel(EmojiPanel(
            this, kv.theme,
            bgColor = kv.resolvedBackground,
            keyColor = keyCol,
            specialColor = specialCol,
            onEmoji = { e -> currentInputConnection?.commitText(e, 1); feedback() },
            onBack = { mode = Mode.LETTERS; rebuildKeyboard(); updateSuggestions() },
            onSearch = {
                emojiQuery.setLength(0)
                mode = Mode.EMOJI_SEARCH
                rebuildKeyboard()
                updateEmojiSearch()
            },
            onDelete = { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); feedback() }
        ))
    }

    private fun showKaomojiPanel() {
        val kv = keyboardView ?: return
        kv.visibility = View.GONE
        val (keyCol, specialCol) = panelColors(kv)
        showPanel(EmojiPanel(
            this, kv.theme,
            bgColor = kv.resolvedBackground,
            keyColor = keyCol,
            specialColor = specialCol,
            onEmoji = { e -> currentInputConnection?.commitText(e, 1); feedback() },
            onBack = { mode = Mode.LETTERS; rebuildKeyboard(); updateSuggestions() },
            onSearch = null,
            onDelete = { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); feedback() },
            categories = KaomojiData.CATEGORIES,
            recentsKey = "kaomoji_recents",
            recentsSep = '\u0001',
            columns = 3,
            itemTextSize = 13.5f,
            tabWidthDp = 58,
            tabTextSize = 13f
        ))
    }

    // ------------------------------------------------------------ listener
    override fun onChar(rawText: String) {
        feedback()
        revertOriginal = null
        revertCorrected = null

        if (mode == Mode.EMOJI_SEARCH) {
            // typing filters emojis instead of committing text
            if (rawText.length == 1 && Character.isLetter(rawText[0])) {
                emojiQuery.append(rawText.lowercase())
                updateEmojiSearch()
            }
            return
        }

        // popup tokens: shift+enter, zwnj joins, date/time insert values
        if (rawText == SHIFT_ENTER) {
            sendShiftEnter()
            return
        }
        val text = when (rawText) {
            "zwnj" -> "\u200C"
            "date" -> java.text.SimpleDateFormat(
                "yyyy/MM/dd", java.util.Locale.US
            ).format(java.util.Date())
            "time" -> java.text.SimpleDateFormat(
                "HH:mm", java.util.Locale.US
            ).format(java.util.Date())
            else -> rawText
        }
        val ic = currentInputConnection ?: return

        val isLetter = text.length == 1 &&
            (Character.isLetter(text[0]) || text[0] == '\u200C')
        if (isLetter) {
            ic.commitText(text, 1)
            wordBuffer.append(text)
        } else {
            handleSeparator(text)
        }

        if (shift == 1) {
            shift = 0
            rebuildKeyboard()
        }
        updateSuggestions()
    }

    /**
     * A separator (space, punctuation, emoji, newline) ends the current word:
     * autocorrect and learning happen here, then the separator itself is
     * committed. AutoText never expands automatically — the expansion is
     * offered on the suggestion strip and inserted only when tapped.
     */
    private fun handleSeparator(sep: String, commitSep: Boolean = true) {
        val ic = currentInputConnection
        val word = wordBuffer.toString()
        wordBuffer.setLength(0)

        if (word.isNotEmpty() && ic != null && !isPasswordField) {
            var committed = word
            // autocorrect: replace an unknown same-length typo with the
            // highlighted best suggestion (e.g. "چط" → "چې"). AutoText
            // shortcuts are legitimate words for the user — never
            // autocorrect them away.
            val best = bestCandidate
            if (autocorrectOn && suggestionsOn && sep == " " &&
                word.length >= 2 && best != null && !best.exact &&
                best.sameLen && !store().contains(word) &&
                !rejectedWords.contains(word) &&
                !(autotextOn && autoTextStore().expansionFor(word) != null)
            ) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(best.word, 1)
                committed = best.word
                // backspace right after this replacement restores the
                // original word (Samsung-style revert)
                revertOriginal = word
                revertCorrected = best.word
            }
            if (committed.length >= 2) {
                if (suggestionsOn && learnWordsOn) store().learn(committed)
                if (suggestionsOn && bigramsOn && lastWord.isNotEmpty()) {
                    store().learnBigram(lastWord, committed)
                }
            }
            lastWord = committed
        }
        bestCandidate = null
        if (commitSep && sep.isNotEmpty()) {
            // punctuation hugs the word before it: "کلمه ،" → "کلمه،"
            if (sep in TIGHT_PUNCT && ic != null) {
                try {
                    if (ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
                        ic.deleteSurroundingText(1, 0)
                    }
                } catch (_: Exception) {
                }
            }
            ic?.commitText(sep, 1)
        }
        updateAutoCaps()
    }

    override fun onSpecial(code: Int) {
        val ic = currentInputConnection
        if (mode == Mode.EMOJI_SEARCH) {
            when (code) {
                Keys.SPACE -> { feedback(); return }
                Keys.ENTER -> {
                    feedback()
                    mode = Mode.EMOJI
                    rebuildKeyboard()
                    updateSuggestions()
                    return
                }
                Keys.SHIFT -> return
            }
        }
        when (code) {
            Keys.SHIFT -> {
                feedback()
                if (mode == Mode.EDIT) {
                    selectMode = !selectMode
                    rebuildKeyboard()
                    return
                }
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == 0 && now - lastShiftTime < 350 -> 2
                    shift == 0 -> 1
                    shift == 1 && now - lastShiftTime < 350 -> 2
                    else -> 0
                }
                lastShiftTime = now
                rebuildKeyboard()
            }
            Keys.DELETE -> {
                if (mode == Mode.EMOJI_SEARCH) {
                    feedback()
                    if (emojiQuery.isNotEmpty()) {
                        emojiQuery.setLength(emojiQuery.length - 1)
                        updateEmojiSearch()
                    } else {
                        mode = Mode.EMOJI
                        rebuildKeyboard()
                    }
                    return
                }
                // an empty field: no delete, no sound, no vibration
                val hasSelection =
                    try { ic?.getSelectedText(0)?.isNotEmpty() == true } catch (_: Exception) { false }
                val hasText =
                    try { ic?.getTextBeforeCursor(1, 0)?.isNotEmpty() == true } catch (_: Exception) { false }
                if (!hasText && !hasSelection) return
                feedback()
                if (tryRevertAutocorrect()) return
                if (wordBuffer.isNotEmpty()) wordBuffer.setLength(wordBuffer.length - 1)
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                updateSuggestions()
            }
            Keys.SYM -> { feedback(); mode = Mode.SYM1; shift = 0; rebuildKeyboard() }
            Keys.SYM2 -> { feedback(); mode = Mode.SYM2; rebuildKeyboard() }
            Keys.ABC -> {
                feedback()
                mode = Mode.LETTERS
                selectMode = false
                rebuildKeyboard()
                updateSuggestions()
            }
            Keys.ENTER -> {
                feedback()
                handleSeparator("", commitSep = false)
                val info = currentInputEditorInfo
                val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                val noEnterAction =
                    info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                if (action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction
                ) {
                    ic?.performEditorAction(action)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                lastWord = ""
                updateAutoCaps()
                updateSuggestions()
            }
            Keys.SPACE -> {
                feedback()
                val now = SystemClock.uptimeMillis()
                if (doubleSpacePeriod && now - lastSpaceTime < 500 && ic != null) {
                    val before = ic.getTextBeforeCursor(2, 0)
                    if (before != null && before.length == 2 &&
                        before[1] == ' ' && Character.isLetter(before[0])
                    ) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(lang.period + " ", 1)
                        lastSpaceTime = 0
                        updateAutoCaps()
                        updateSuggestions()
                        return
                    }
                }
                lastSpaceTime = now
                handleSeparator(" ")
                updateSuggestions()
            }
            Keys.MIC -> startVoiceInput()
            Keys.ARROW_UP -> { feedback(); arrowVertical(up = true) }
            Keys.ARROW_DOWN -> { feedback(); arrowVertical(up = false) }
            Keys.ARROW_LEFT -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }
            Keys.ARROW_RIGHT -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }

            Keys.EDIT_PANEL -> { feedback(); mode = Mode.EDIT; rebuildKeyboard() }
            Keys.NUMPAD -> { feedback(); mode = Mode.NUMPAD; rebuildKeyboard() }
            Keys.EMOJI -> { feedback(); mode = Mode.EMOJI; rebuildKeyboard() }
            Keys.CLIPBOARD -> { feedback(); mode = Mode.CLIPBOARD; rebuildKeyboard() }
            // the menu's languages entry opens the settings page where the
            // ACTIVE languages are enabled/disabled (switching the current
            // language stays on the space bar: swipe or long-press)
            Keys.LANGS -> { feedback(); openSettings("langs") }
            Keys.SETTINGS -> { feedback(); openSettings(null) }

            Keys.ESC -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE) }
            Keys.TAB -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB) }
            Keys.COPY -> { feedback(); icActionAsync(android.R.id.copy) }
            Keys.CUT -> { feedback(); icActionAsync(android.R.id.cut) }
            Keys.PASTE -> { feedback(); icActionAsync(android.R.id.paste) }
            Keys.SELECT_ALL -> { feedback(); icActionAsync(android.R.id.selectAll) }
            Keys.FWD_DEL -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL) }
            Keys.HOME -> { feedback(); sendLineStart() }
            Keys.END -> { feedback(); sendLineEnd() }
            Keys.UNDO -> {
                feedback()
                wordBuffer.setLength(0)
                sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = false)
                updateSuggestions()
            }
            Keys.REDO -> {
                feedback()
                wordBuffer.setLength(0)
                sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = true)
                updateSuggestions()
            }
            Keys.KAOMOJI -> { feedback(); mode = Mode.KAOMOJI; rebuildKeyboard() }
            Keys.SPLIT -> {
                feedback()
                val p = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(this)
                val v = !p.getBoolean("split_kb", false)
                p.edit().putBoolean("split_kb", v).apply()
                keyboardView?.splitMode = v
                rebuildKeyboard()
            }
        }
    }

    /**
     * Undo = Ctrl+Z, Redo = Ctrl+Shift+Z — Android EditText fields have a
     * built-in undo manager driven by exactly these key events, so this is
     * crash-proof: fields without undo support simply ignore the events.
     */
    /**
     * Copy/cut/paste/select-all run INSIDE the target app during this call:
     * pasting or copying a large text blocks the caller for seconds while
     * the app re-lays-out. Running it on a background thread keeps the
     * keyboard responsive — no more frozen keys after copy/paste.
     */
    private fun icActionAsync(action: Int) {
        val ic = currentInputConnection ?: return
        Io.writer.execute {
            try {
                ic.performContextMenuAction(action)
            } catch (_: Exception) {
            }
        }
    }

    /** Shift+Enter — a newline even in fields whose Enter means Send/Go. */
    private fun sendShiftEnter() {
        val ic = currentInputConnection ?: return
        try {
            val now = SystemClock.uptimeMillis()
            val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, meta))
        } catch (_: Exception) {
        }
    }

    private fun sendCtrlKey(keyCode: Int, withShift: Boolean) {
        val ic = currentInputConnection ?: return
        try {
            val now = SystemClock.uptimeMillis()
            var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (withShift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        } catch (_: Exception) {
        }
    }

    /**
     * Backspace immediately after an autocorrection restores the word the
     * user actually typed, and stops correcting that word from then on
     * (a second revert makes it a learned dictionary word).
     */
    private fun tryRevertAutocorrect(): Boolean {
        val original = revertOriginal ?: return false
        val corrected = revertCorrected ?: return false
        revertOriginal = null
        revertCorrected = null
        val ic = currentInputConnection ?: return false
        val expect = "$corrected "
        val before = try { ic.getTextBeforeCursor(expect.length, 0) } catch (_: Exception) { null }
        if (before == null || before.toString() != expect) return false
        ic.deleteSurroundingText(expect.length, 0)
        ic.commitText(original, 1)
        rejectedWords.add(original)
        if (learnWordsOn) store().learn(original)
        wordBuffer.setLength(0)
        wordBuffer.append(original)
        updateSuggestions()
        return true
    }

    /**
     * Vertical arrow: send DPAD up/down, then observe whether the cursor
     * actually moved. When it didn't (first/last VISUAL line — including
     * wrapped lines in narrow fields, which contain no newline character),
     * jump to the logical start/end of the line instead. Falls back to the
     * newline heuristic in fields that don't support text extraction.
     */
    private fun arrowVertical(up: Boolean) {
        val posBefore = cursorPos()
        sendArrow(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
        if (posBefore == null) {
            if (up && !hasLineAbove()) sendLineStart()
            if (!up && !hasLineBelow()) sendLineEnd()
            return
        }
        keyboardView?.postDelayed({
            val now = cursorPos()
            if (now != null && now == posBefore) {
                if (up) sendLineStart() else sendLineEnd()
            }
        }, 70)
    }

    /** Absolute cursor position, or null when the field can't report it. */
    private fun cursorPos(): Int? {
        val ic = currentInputConnection ?: return null
        val et = try {
            ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        } catch (_: Exception) { null } ?: return null
        if (et.selectionEnd < 0) return null
        return et.startOffset + et.selectionEnd
    }

    /**
     * Jump to the logical start/end of the current line. MOVE_HOME/MOVE_END
     * cannot be used for this: they are VISUAL edge moves (left/right edge),
     * so on RTL lines most apps land the cursor on the wrong end. Instead
     * the target offset is computed from the text itself and the cursor is
     * placed there directly — direction-independent, same in every app.
     */
    private fun sendLineStart() = moveToLineEdge(end = false)

    private fun sendLineEnd() = moveToLineEdge(end = true)

    private fun moveToLineEdge(end: Boolean) {
        val ic = currentInputConnection ?: return
        val et = try {
            ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        } catch (_: Exception) { null }
        val text = et?.text
        if (text != null && et.selectionStart >= 0 && et.selectionEnd <= text.length) {
            var target = et.selectionEnd
            if (end) {
                while (target < text.length && text[target] != '\n') target++
            } else {
                while (target > 0 && text[target - 1] != '\n') target--
            }
            val abs = et.startOffset + target
            // in edit-panel select mode the jump extends the selection
            val anchor = if (mode == Mode.EDIT && selectMode) {
                et.startOffset + et.selectionStart
            } else abs
            try {
                ic.setSelection(anchor, abs)
                return
            } catch (_: Exception) {
            }
        }
        // fallback for fields that don't support text extraction: visual
        // edge keys, swapped on RTL lines so they reach the logical edge
        val rtl = isRtlLine()
        sendArrow(
            if (end != rtl) KeyEvent.KEYCODE_MOVE_END else KeyEvent.KEYCODE_MOVE_HOME
        )
    }

    /**
     * Direction of the line the cursor is on, resolved the way Android does:
     * the first strong directional character decides. Falls back to the
     * active layout's direction when the line has no strong character.
     */
    private fun isRtlLine(): Boolean {
        val ic = currentInputConnection ?: return lang.rtl
        val before = try { ic.getTextBeforeCursor(4000, 0)?.toString() } catch (_: Exception) { null }
        if (before != null) {
            for (ch in before.substringAfterLast('\n')) {
                strongDir(ch)?.let { return it }
            }
        }
        val after = try { ic.getTextAfterCursor(4000, 0)?.toString() } catch (_: Exception) { null }
        if (after != null) {
            for (ch in after) {
                if (ch == '\n') break
                strongDir(ch)?.let { return it }
            }
        }
        return lang.rtl
    }

    /** true = RTL, false = LTR, null = not a strong directional character. */
    private fun strongDir(ch: Char): Boolean? = when (Character.getDirectionality(ch)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> false
        else -> null
    }

    /** Arrows honour edit-panel select mode by holding Shift. */
    private fun hasLineAbove(): Boolean {
        val ic = currentInputConnection ?: return true
        val before = try { ic.getTextBeforeCursor(4000, 0) } catch (_: Exception) { null }
        return before?.contains('\n') == true
    }

    private fun hasLineBelow(): Boolean {
        val ic = currentInputConnection ?: return true
        val after = try { ic.getTextAfterCursor(4000, 0) } catch (_: Exception) { null }
        return after?.contains('\n') == true
    }

    private fun sendArrow(keyCode: Int) {
        val ic = currentInputConnection ?: return
        if (mode == Mode.EDIT && selectMode) {
            val now = SystemClock.uptimeMillis()
            val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        } else {
            sendDownUpKeyEvents(keyCode)
        }
    }

    override fun onLangSwipe(forward: Boolean) {
        switchLanguage(if (forward) 1 else -1)
    }

    override fun onSpaceLongPress() {
        showLanguageMenu()
    }

    private fun openSettings(screen: String?) {
        try {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (screen != null) intent.putExtra("open_screen", screen)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onShiftLongPress() {
        if (mode == Mode.EDIT) return
        feedback()
        shift = 2 // caps lock
        lastShiftTime = 0L
        rebuildKeyboard()
    }

    /** Round-fill icon for each panel-menu entry. */
    private fun menuIcon(code: Int): Int = when (code) {
        Keys.EDIT_PANEL -> R.drawable.ic_key_control
        Keys.NUMPAD -> R.drawable.ic_key_numpad
        Keys.EMOJI -> R.drawable.ic_key_emoji
        Keys.KAOMOJI -> R.drawable.ic_key_kaomoji
        Keys.CLIPBOARD -> R.drawable.ic_key_clipboard
        Keys.MIC -> R.drawable.ic_key_mic
        Keys.LANGS -> R.drawable.ic_key_lang
        Keys.SETTINGS -> R.drawable.ic_key_settings
        Keys.SPLIT -> R.drawable.ic_key_split
        else -> 0
    }

    override fun onSymLongPress() {
        feedback()
        val kv = keyboardView ?: return
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val splitOn = p.getBoolean("split_kb", false)
        val items = Layouts.menuItems() +
            ((if (splitOn) "وېشل ✓" else "وېشل") to Keys.SPLIT)
        kv.showGridMenu(
            items.map { it.first },
            // control pre-selected: releasing the long-press opens it
            initial = items.indexOfFirst { it.second == Keys.EDIT_PANEL },
            icons = items.map { menuIcon(it.second) }
        ) { which ->
            onSpecial(items[which].second)
        }
    }

    // ------------------------------------------------------------ languages
    private fun switchLanguage(delta: Int) {
        if (languages.size < 2) return
        langIndex = (langIndex + delta + languages.size) % languages.size
        mode = Mode.LETTERS
        shift = 0
        wordBuffer.setLength(0)
        lastWord = ""
        feedback()
        rebuildKeyboard()
        updateSuggestions()
        // visible confirmation that the swipe switched the language
        keyboardView?.flashLanguage(lang.nativeName, delta > 0)
    }

    private fun showLanguageMenu() {
        val kv = keyboardView ?: return
        kv.showGridMenu(languages.map { it.nativeName }, initial = langIndex, cols = 1) { which ->
            langIndex = which
            mode = Mode.LETTERS
            shift = 0
            wordBuffer.setLength(0)
            lastWord = ""
            rebuildKeyboard()
            updateSuggestions()
        }
    }

    // ------------------------------------------------------------ voice
    private fun startVoiceInput() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val voice = imm.enabledInputMethodList.firstOrNull {
                it.id.contains("voice", ignoreCase = true)
            }
            if (voice != null) {
                @Suppress("DEPRECATION")
                switchInputMethod(voice.id)
            } else {
                Toast.makeText(
                    this,
                    "Voice IME نشته — ګوګل غږیز ټایپینګ فعال کړئ",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------- emoji search
    private fun loadEmojiKeywords(): List<Pair<String, List<String>>> {
        emojiKeywords?.let { return it }
        val list = ArrayList<Pair<String, List<String>>>()
        try {
            assets.open("emoji_keywords.txt").bufferedReader().forEachLine { line ->
                val idx = line.indexOf('\t')
                if (idx > 0) {
                    val kw = line.substring(0, idx).trim()
                    val emojis = line.substring(idx + 1).trim()
                        .split(' ').filter { it.isNotEmpty() }
                    if (kw.isNotEmpty() && emojis.isNotEmpty()) list.add(kw to emojis)
                }
            }
        } catch (_: Exception) {
        }
        emojiKeywords = list
        return list
    }

    /** The suggestion strip becomes the emoji-search result row. */
    private fun updateEmojiSearch() {
        val bar = suggestionBar ?: return
        bar.removeAllViews()
        val kv = keyboardView ?: return
        val q = emojiQuery.toString()
        val density = resources.displayMetrics.density

        val label = TextView(this)
        label.text = if (q.isEmpty()) "🔍…" else "🔍 $q"
        label.setTextColor(kv.theme.accent)
        label.textSize = suggFontSp
        label.maxLines = 1
        label.gravity = android.view.Gravity.CENTER
        label.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
        bar.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (q.isEmpty()) return
        val out = LinkedHashSet<String>()
        for ((kw, emojis) in loadEmojiKeywords()) {
            if (kw.startsWith(q) || (q.length >= 3 && kw.contains(q))) {
                out.addAll(emojis)
                if (out.size >= 24) break
            }
        }
        for (e in out.take(24)) {
            val tv = TextView(this)
            tv.text = e
            tv.textSize = 24f
            tv.maxLines = 1
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            tv.setOnClickListener {
                currentInputConnection?.commitText(e, 1)
                feedback()
            }
            bar.addView(
                tv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    /** True when the line the cursor is on has no characters at all. */
    private fun isCurrentLineEmpty(): Boolean {
        val ic = currentInputConnection ?: return true
        val before = try {
            ic.getTextBeforeCursor(64, 0)?.toString()
        } catch (_: Exception) { null } ?: ""
        val after = try {
            ic.getTextAfterCursor(64, 0)?.toString()
        } catch (_: Exception) { null } ?: ""
        val lineBefore = before.substringAfterLast('\n')
        val lineAfter = after.substringBefore('\n')
        return lineBefore.isBlank() && lineAfter.isBlank()
    }

    // -------------------------------------------------------- suggestions
    private var suggPending = false

    /** Coalesce bursts (selection-update storms after a big paste) into a
     *  single rebuild per frame — the strip work itself queries the target
     *  app for text, which is expensive while that app is busy. */
    private fun updateSuggestions() {
        if (suggPending) return
        val kv = keyboardView ?: return
        suggPending = true
        kv.post {
            suggPending = false
            buildSuggestions()
        }
    }

    private fun buildSuggestions() {
        bestCandidate = null
        val bar = suggestionBar ?: return
        bar.removeAllViews()
        if (!suggestionsOn || isPasswordField) return
        val kv = keyboardView ?: return
        val prefix = wordBuffer.toString()

        class SuggItem(
            val text: String,
            val style: Int,
            val isClip: Boolean = false,
            val isAutoText: Boolean = false
        )

        val items = ArrayList<SuggItem>()
        val STYLE_NORMAL = 0
        val STYLE_ACCENT = 1
        val STYLE_TYPED = 2

        if (prefix.isEmpty()) {
            // newest clipboard item; with repeat OFF the chip is one-shot
            if (isCurrentLineEmpty()) {
                clipboardStore().newest()?.let {
                    if (clipChipRepeat || clipKey(it) != consumedClipKey) {
                        items.add(SuggItem(it, STYLE_ACCENT, isClip = true))
                    }
                }
            }
            if (bigramsOn && lastWord.isNotEmpty()) {
                for (w in store().suggestNext(lastWord, 4, seedDictOn)) {
                    items.add(SuggItem(w, STYLE_NORMAL))
                }
            }
        } else {
            val typedIsKnown = store().contains(prefix)
            val cands = store().suggestSmart(prefix, 6, seedDictOn, confusion())
            if (typedIsKnown) {
                // the typed word is itself a valid word: highlight IT and never
                // auto-replace it; similar words are only tappable extras
                bestCandidate = null
                items.add(SuggItem(prefix, STYLE_ACCENT))
                if (autotextOn) {
                    for (e in autoTextStore().matching(prefix, 3)) {
                        items.add(SuggItem(e, STYLE_NORMAL, isAutoText = true))
                    }
                }
                for (c in cands) items.add(SuggItem(c.word, STYLE_NORMAL))
            } else {
                // unknown word: the top match is the correction target (blue),
                // the typed word stays available (plain, long-press to save)
                items.add(SuggItem(prefix, STYLE_TYPED))
                if (autotextOn) {
                    for (e in autoTextStore().matching(prefix, 3)) {
                        items.add(SuggItem(e, STYLE_ACCENT, isAutoText = true))
                    }
                }
                bestCandidate = cands.firstOrNull()
                for ((i, c) in cands.withIndex()) {
                    items.add(SuggItem(c.word, if (i == 0) STYLE_ACCENT else STYLE_NORMAL))
                }
            }
        }

        val density = resources.displayMetrics.density

        // an empty strip becomes a quick-action row: settings, control,
        // clipboard, numpad, emoji
        if (items.isEmpty() && prefix.isEmpty()) {
            bar.minimumWidth = suggestionScroll?.width ?: 0
            bar.gravity = android.view.Gravity.CENTER
            val actions = listOf(
                R.drawable.ic_key_settings to Keys.SETTINGS,
                R.drawable.ic_key_control to Keys.EDIT_PANEL,
                R.drawable.ic_key_clipboard to Keys.CLIPBOARD,
                R.drawable.ic_key_numpad to Keys.NUMPAD,
                R.drawable.ic_key_emoji to Keys.EMOJI
            )
            // spread the actions evenly over the whole strip width
            val stripW = (suggestionScroll?.width ?: 0).let {
                if (it > 0) it else resources.displayMetrics.widthPixels
            }
            for ((iconRes, code) in actions) {
                val iv = android.widget.ImageView(this)
                iv.setImageDrawable(
                    tintedIcon(iconRes, kv.theme.hint, (21 * density).toInt())
                )
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER
                iv.setOnClickListener { onSpecial(code) }
                val lp = LinearLayout.LayoutParams(
                    stripW / actions.size, LinearLayout.LayoutParams.MATCH_PARENT
                )
                bar.addView(iv, lp)
            }
            return
        }

        // a lone clipboard chip is centered, Samsung-style
        val onlyChip = items.size == 1 && items[0].isClip
        bar.minimumWidth = if (onlyChip) (suggestionScroll?.width ?: 0) else 0
        bar.gravity = android.view.Gravity.CENTER

        for (item in items) {
            val text = item.text
            val style = item.style
            val isClip = item.isClip
            val tv = TextView(this)
            // multi-line AutoText phrases show as one line ("a ⏎ b" gaps fixed)
            tv.text = when {
                isClip -> text.take(60)
                item.isAutoText -> text.replace(Regex("\\s*\\n\\s*"), " ")
                else -> text
            }
            tv.setTextColor(if (style == STYLE_ACCENT) kv.theme.accent else kv.theme.text)
            if (isClip) {
                // rounded pill with an accent stroke and soft glow fill
                val pill = android.graphics.drawable.GradientDrawable()
                pill.setColor(kv.theme.accent and 0x22FFFFFF)
                pill.setStroke((1.5f * density).toInt(), kv.theme.accent)
                pill.cornerRadius = 14 * density
                tv.background = pill
                tv.setTextColor(kv.theme.text)
                tv.maxWidth = (150 * density).toInt()
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
            }
            if (item.isAutoText) {
                // long phrases: slightly smaller and cut with … at ~½ screen
                tv.maxWidth = (200 * density).toInt()
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
            }
            if (style == STYLE_ACCENT) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            tv.textSize = when {
                isClip -> 12.5f
                item.isAutoText -> suggFontSp * 0.78f
                else -> suggFontSp
            }
            tv.maxLines = 1
            tv.setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            tv.gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            if (isClip) {
                lp.setMargins(
                    (8 * density).toInt(), (5 * density).toInt(),
                    (8 * density).toInt(), (5 * density).toInt()
                )
            }
            tv.layoutParams = lp
            when {
                isClip -> {
                    tv.setOnClickListener {
                        currentInputConnection?.commitText(text, 1)
                        feedback()
                        // one-shot mode: spent forever (any field, any app)
                        if (!clipChipRepeat) {
                            consumedClipKey = clipKey(text)
                            PreferenceManager.getDefaultSharedPreferences(this)
                                .edit().putString("consumed_clip", consumedClipKey)
                                .apply()
                        }
                        updateSuggestions()
                    }
                }
                item.isAutoText -> {
                    // commit the ORIGINAL (possibly multi-line) phrase
                    tv.setOnClickListener { commitAutoText(text) }
                }
                style == STYLE_TYPED -> {
                    tv.setOnClickListener { commitSuggestion(text) }
                    tv.setOnLongClickListener {
                        store().learn(text)
                        store().learn(text) // count 2 → suggested from now on
                        Toast.makeText(this, R.string.word_saved, Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                else -> {
                    tv.setOnClickListener { commitSuggestion(text) }
                    tv.setOnLongClickListener {
                        store().forget(text)
                        updateSuggestions()
                        true
                    }
                }
            }
            bar.addView(tv)
        }
    }

    /** Replace the typed shortcut with its full AutoText phrase (which may
     *  span several lines) — without learning the phrase as a "word". */
    private fun commitAutoText(expansion: String) {
        val ic = currentInputConnection ?: return
        val prefix = wordBuffer.toString()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$expansion ", 1)
        wordBuffer.setLength(0)
        lastWord = ""
        bestCandidate = null
        feedback()
        updateSuggestions()
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = wordBuffer.toString()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        if (learnWordsOn) store().learn(word)
        if (bigramsOn && lastWord.isNotEmpty()) store().learnBigram(lastWord, word)
        lastWord = word
        wordBuffer.setLength(0)
        feedback()
        updateSuggestions()
    }

    // ------------------------------------------------------------- helpers
    private fun updateAutoCaps() {
        if (!autoCapsOn || lang.code != "en" || mode != Mode.LETTERS) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        if (info.inputType and InputType.TYPE_CLASS_TEXT == 0) return
        if (shift == 2) return
        val caps = ic.getCursorCapsMode(info.inputType)
        val newShift = if (caps != 0) 1 else 0
        if (newShift != shift) {
            shift = newShift
            rebuildKeyboard()
        }
    }

    private fun soundRes(type: String): Int = when (type) {
        "click" -> R.raw.keyclick
        "drop" -> R.raw.keydrop
        "wood" -> R.raw.keywood
        "bubble" -> R.raw.keybubble
        "soft" -> R.raw.keysoft
        else -> R.raw.keypop
    }

    private var loadedSoundType = ""

    private fun initSoundPool() {
        try {
            if (soundPool != null && loadedSoundType == soundType) return
            soundPool?.release()
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sp = android.media.SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build()
            popSoundId = sp.load(this, soundRes(soundType), 1)
            soundPool = sp
            loadedSoundType = soundType
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun feedback() {
        if (vibrateOn) {
            try {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(vibrateMs)
            } catch (_: Exception) {
            }
        }
        if (soundOn) {
            try {
                if (soundType != "system") {
                    if (soundPool == null) initSoundPool()
                    val sp = soundPool
                    if (sp != null && popSoundId != 0) {
                        sp.play(popSoundId, soundVol, soundVol, 1, 0, 1f)
                    }
                } else {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, soundVol)
                }
            } catch (_: Exception) {
            }
        }
    }
}
