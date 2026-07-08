package com.elyas.multiling

import android.app.AlertDialog
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.os.Vibrator
import androidx.preference.PreferenceManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The input method service: builds rows for the current language/mode,
 * commits text, and implements shift, symbols, language switching,
 * suggestions, double-space period, arrow keys and vibration.
 */
class MultilingIME : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { LETTERS, SYM1, SYM2 }

    private var keyboardView: KeyboardView? = null
    private var rootView: LinearLayout? = null
    private var suggestionBar: LinearLayout? = null
    private var suggestionScroll: HorizontalScrollView? = null

    private var languages: List<Language> = Layouts.ALL
    private var langIndex = 0
    private var mode = Mode.LETTERS
    private var shift = 0 // 0 off, 1 once, 2 locked
    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L

    // settings snapshot
    private var vibrateOn = true
    private var soundOn = false
    private var suggestionsOn = true
    private var doubleSpacePeriod = true
    private var arrowsOn = true

    private val wordStores = HashMap<String, WordStore>()
    private val wordBuffer = StringBuilder()

    private val lang: Language get() = languages[langIndex]

    private fun store(): WordStore =
        wordStores.getOrPut(lang.code) { WordStore(this, lang.code) }

    // ------------------------------------------------------------ lifecycle
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
        rootView = root
        applySettings()
        rebuildKeyboard()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applySettings()
        mode = Mode.LETTERS
        shift = 0
        wordBuffer.setLength(0)
        updateAutoCaps()
        rebuildKeyboard()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        for (s in wordStores.values) s.save()
        keyboardView?.dismissPopups()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------- settings
    private fun applySettings() {
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        val kv = keyboardView ?: return
        kv.theme = KeyboardView.themeByName(p.getString("theme", "dark") ?: "dark")
        kv.keyHeightDp = p.getInt("key_height", 52)
        kv.fontScale = p.getInt("font_scale", 100) / 100f
        kv.showHints = p.getBoolean("hints", true)
        kv.showPreview = p.getBoolean("preview", true)
        kv.longPressTimeout = (p.getString("longpress", "350") ?: "350").toLong()
        vibrateOn = p.getBoolean("vibrate", true)
        soundOn = p.getBoolean("sound", false)
        suggestionsOn = p.getBoolean("suggestions", true)
        doubleSpacePeriod = p.getBoolean("double_space", true)
        arrowsOn = p.getBoolean("arrows", true)

        val enabled = p.getStringSet("languages", null)
        val list = if (enabled.isNullOrEmpty()) Layouts.ALL
        else Layouts.ALL.filter { enabled.contains(it.code) }
        languages = if (list.isEmpty()) Layouts.ALL else list
        if (langIndex >= languages.size) langIndex = 0

        rootView?.setBackgroundColor(kv.theme.background)
        suggestionScroll?.visibility = if (suggestionsOn) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------- keyboard build
    private fun buildBottomRow(): List<KeyDef> {
        val symLabel = if (lang.digits[0] == '0') "?123" else "۱۲۳"
        return listOf(
            KeyDef(symLabel, code = Keys.SYM, width = 1.5f, hint = "🎤"),
            lang.extraKey,
            KeyDef(lang.nativeName, code = Keys.SPACE, width = 4f),
            KeyDef(lang.period, null, lang.periodAlts),
            KeyDef("↵", code = Keys.ENTER, width = 1.5f)
        )
    }

    private fun buildSymBottomRow(): List<KeyDef> = listOf(
        KeyDef("ابت", code = Keys.ABC, width = 1.5f),
        KeyDef(",", null, listOf("،", "؛")),
        KeyDef(lang.nativeName, code = Keys.SPACE, width = 4f),
        KeyDef(lang.period, null, lang.periodAlts),
        KeyDef("↵", code = Keys.ENTER, width = 1.5f)
    )

    private fun arrowRow(): List<KeyDef> = listOf(
        KeyDef("▲", code = Keys.ARROW_UP, repeatable = true),
        KeyDef("▼", code = Keys.ARROW_DOWN, repeatable = true),
        KeyDef("◀", code = Keys.ARROW_LEFT, repeatable = true),
        KeyDef("▶", code = Keys.ARROW_RIGHT, repeatable = true)
    )

    private fun currentRows(): List<List<KeyDef>> {
        val body = when (mode) {
            Mode.LETTERS -> lang.rows
            Mode.SYM1 -> Layouts.symbols1(lang)
            Mode.SYM2 -> Layouts.symbols2()
        }
        val rows = ArrayList<List<KeyDef>>(body)
        rows.add(if (mode == Mode.LETTERS) buildBottomRow() else buildSymBottomRow())
        if (arrowsOn) rows.add(arrowRow())
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
                    shift > 0 && key.shifted != null -> key.shifted
                    else -> key.label
                }
            }
        }
    }

    private fun rebuildKeyboard() {
        val kv = keyboardView ?: return
        val rows = currentRows()
        kv.shiftState = shift
        kv.setKeyboard(rows, displayFor(rows))
    }

    // ------------------------------------------------------------ listener
    override fun onChar(text: String) {
        feedback()
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)

        val isLetter = text.length == 1 && Character.isLetter(text[0])
        if (isLetter) wordBuffer.append(text) else finishWord(text)

        if (shift == 1) {
            shift = 0
            rebuildKeyboard()
        }
        updateSuggestions()
    }

    override fun onSpecial(code: Int) {
        val ic = currentInputConnection
        when (code) {
            Keys.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == 0 && now - lastShiftTime < 350 -> 2
                    shift == 0 -> 1
                    shift == 1 && now - lastShiftTime < 350 -> 2
                    else -> 0
                }
                lastShiftTime = now
                feedback()
                rebuildKeyboard()
            }
            Keys.DELETE -> {
                feedback()
                if (wordBuffer.isNotEmpty()) wordBuffer.setLength(wordBuffer.length - 1)
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                updateSuggestions()
            }
            Keys.SYM -> { feedback(); mode = Mode.SYM1; shift = 0; rebuildKeyboard() }
            Keys.SYM2 -> { feedback(); mode = Mode.SYM2; rebuildKeyboard() }
            Keys.ABC -> { feedback(); mode = Mode.LETTERS; rebuildKeyboard() }
            Keys.ENTER -> {
                feedback()
                finishWord("\n")
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
                finishWord(" ")
                ic?.commitText(" ", 1)
                updateAutoCaps()
                updateSuggestions()
            }
            Keys.MIC -> startVoiceInput()
            Keys.ARROW_UP -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP) }
            Keys.ARROW_DOWN -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN) }
            Keys.ARROW_LEFT -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
            Keys.ARROW_RIGHT -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
        }
    }

    override fun onLangSwipe(forward: Boolean) {
        switchLanguage(if (forward) 1 else -1)
    }

    override fun onSpaceLongPress() {
        showLanguageMenu()
    }

    // ------------------------------------------------------------ languages
    private fun switchLanguage(delta: Int) {
        if (languages.size < 2) return
        langIndex = (langIndex + delta + languages.size) % languages.size
        mode = Mode.LETTERS
        shift = 0
        wordBuffer.setLength(0)
        feedback()
        rebuildKeyboard()
        updateSuggestions()
    }

    private fun showLanguageMenu() {
        val view = keyboardView ?: return
        val names = languages.map { it.nativeName }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_languages))
            .setItems(names) { _, which ->
                langIndex = which
                mode = Mode.LETTERS
                shift = 0
                rebuildKeyboard()
            }
            .create()
        val window = dialog.window ?: return
        val lp = window.attributes
        lp.token = view.windowToken
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.show()
    }

    // ------------------------------------------------------------ voice
    private fun startVoiceInput() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            val voice = imm.enabledInputMethodList.firstOrNull {
                it.id.contains("voice", ignoreCase = true)
            }
            if (voice != null) {
                @Suppress("DEPRECATION")
                switchInputMethod(voice.id)
            } else {
                Toast.makeText(this, "Voice IME نشته — ګوګل غږیز ټایپینګ فعال کړئ", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------- suggestions
    private fun finishWord(sep: String) {
        val word = wordBuffer.toString()
        wordBuffer.setLength(0)
        if (suggestionsOn && word.length >= 2 && sep != "") {
            store().learn(word)
        }
    }

    private fun updateSuggestions() {
        val bar = suggestionBar ?: return
        bar.removeAllViews()
        if (!suggestionsOn) return
        val kv = keyboardView ?: return
        val prefix = wordBuffer.toString()
        val items = store().suggest(prefix, 5)
        val density = resources.displayMetrics.density
        for (word in items) {
            val tv = TextView(this)
            tv.text = word
            tv.setTextColor(kv.theme.text)
            tv.textSize = 17f
            tv.setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            tv.gravity = android.view.Gravity.CENTER
            tv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            tv.setOnClickListener { commitSuggestion(word) }
            tv.setOnLongClickListener {
                store().forget(word)
                updateSuggestions()
                true
            }
            bar.addView(tv)
        }
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = wordBuffer.toString()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        store().learn(word)
        wordBuffer.setLength(0)
        feedback()
        updateSuggestions()
    }

    // ------------------------------------------------------------- helpers
    private fun updateAutoCaps() {
        if (lang.code != "en" || mode != Mode.LETTERS) return
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

    @Suppress("DEPRECATION")
    private fun feedback() {
        if (vibrateOn) {
            try {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(20)
            } catch (_: Exception) {
            }
        }
        if (soundOn) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
            } catch (_: Exception) {
            }
        }
    }
}
