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

    private enum class Mode { LETTERS, SYM1, SYM2, EDIT, NUMPAD, EMOJI }

    private var keyboardView: KeyboardView? = null
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
    private var soundType = "pop"

    private var autocorrectOn = true
    private var isPasswordField = false
    private var pendingClip: String? = null
    private var bestCandidate: WordStore.Cand? = null
    private val confusionMaps = HashMap<String, Map<Char, Set<Char>>>()

    private val wordStores = HashMap<String, WordStore>()
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

    // ------------------------------------------------------------ lifecycle
    override fun onCreate() {
        super.onCreate()
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.addPrimaryClipChangedListener {
                pendingClip = try {
                    cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                        ?.trim()?.take(500)?.ifEmpty { null }
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
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
        rootView = root
        applySettings()
        rebuildKeyboard()
        return root
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
            updateSuggestions()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------- settings
    private fun applySettings() {
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        val kv = keyboardView ?: return
        kv.theme = KeyboardView.themeByName(p.getString("theme", "dark") ?: "dark")
        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        kv.keyHeightDp =
            if (landscape) p.getInt("key_height_land", 42) else p.getInt("key_height", 52)
        kv.fontScale = p.getInt("font_scale", 100) / 100f
        kv.hintScale = p.getInt("hint_scale", 100) / 100f
        kv.cornerRadiusDp = p.getInt("corner_radius", 6)
        kv.keyGapDp = p.getInt("key_gap", 2) / 1.33f
        kv.showHints = p.getBoolean("hints", true)
        kv.showPreview = p.getBoolean("preview", true)
        kv.keyBorder = p.getBoolean("key_border", false)
        kv.spaceSwipeEnabled = p.getBoolean("space_swipe", true)
        kv.longPressTimeout = (p.getString("longpress", "350") ?: "350").toLong()
        val density = resources.displayMetrics.density
        kv.setPadding(0, 0, 0, (p.getInt("bottom_gap", 0) * density).toInt())

        vibrateOn = p.getBoolean("vibrate", true)
        vibrateMs = p.getInt("vibrate_ms", 20).toLong()
        soundOn = p.getBoolean("sound", false)
        soundVol = p.getInt("sound_vol", 60) / 100f
        soundType = p.getString("sound_type", "pop") ?: "pop"
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
            KeyDef(symLabel, code = Keys.SYM, width = 1.5f, hintIcon = Keys.ICON_MIC),
            lang.extraKey,
            KeyDef(lang.nativeName, code = Keys.SPACE, width = 4f),
            KeyDef(lang.period, null, lang.periodAlts),
            KeyDef("↵", code = Keys.ENTER, width = 1.5f)
        )
    }

    private fun buildSymBottomRow(): List<KeyDef> = listOf(
        KeyDef("ابت", code = Keys.ABC, width = 1.5f),
        if (lang.rtl) KeyDef("،", null, listOf(",", "؛"))
        else KeyDef(",", null, listOf("،", ";")),
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
                rows.add(buildBottomRow())
            }
            Mode.NUMPAD -> rows.addAll(Layouts.numPad(lang))
            Mode.EMOJI -> rows.addAll(Layouts.emojiPage())
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

    private fun rebuildKeyboard() {
        val kv = keyboardView ?: return
        val rows = currentRows()
        kv.shiftState = if (mode == Mode.EDIT) (if (selectMode) 2 else 0) else shift
        kv.setKeyboard(rows, displayFor(rows))
    }

    // ------------------------------------------------------------ listener
    override fun onChar(text: String) {
        feedback()
        val ic = currentInputConnection ?: return

        val isLetter = text.length == 1 && Character.isLetter(text[0])
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
     * AutoText expansion happens here, then learning, then the separator
     * itself is committed.
     */
    private fun handleSeparator(sep: String, commitSep: Boolean = true) {
        val ic = currentInputConnection
        val word = wordBuffer.toString()
        wordBuffer.setLength(0)

        if (word.isNotEmpty() && ic != null && !isPasswordField) {
            val expansion = if (autotextOn) autoTextStore().expansionFor(word) else null
            var committed = word
            if (expansion != null) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(expansion, 1)
                committed = expansion
            } else {
                // autocorrect: replace an unknown same-length typo with the
                // highlighted best suggestion (e.g. "چط" → "چې")
                val best = bestCandidate
                if (autocorrectOn && suggestionsOn && sep == " " &&
                    word.length >= 2 && best != null && !best.exact &&
                    best.sameLen && !store().contains(word)
                ) {
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText(best.word, 1)
                    committed = best.word
                }
                if (committed.length >= 2) {
                    if (suggestionsOn && learnWordsOn) store().learn(committed)
                    if (suggestionsOn && bigramsOn && lastWord.isNotEmpty()) {
                        store().learnBigram(lastWord, committed)
                    }
                }
            }
            lastWord = committed
        }
        bestCandidate = null
        if (commitSep && sep.isNotEmpty()) ic?.commitText(sep, 1)
        updateAutoCaps()
    }

    override fun onSpecial(code: Int) {
        val ic = currentInputConnection
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
                feedback()
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
            Keys.ARROW_UP -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_UP) }
            Keys.ARROW_DOWN -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_DOWN) }
            Keys.ARROW_LEFT -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }
            Keys.ARROW_RIGHT -> { feedback(); sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }

            Keys.EDIT_PANEL -> { feedback(); mode = Mode.EDIT; rebuildKeyboard() }
            Keys.NUMPAD -> { feedback(); mode = Mode.NUMPAD; rebuildKeyboard() }
            Keys.EMOJI -> { feedback(); mode = Mode.EMOJI; rebuildKeyboard() }
            Keys.LANGS -> { feedback(); showLanguageMenu() }
            Keys.SETTINGS -> {
                feedback()
                try {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }

            Keys.ESC -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE) }
            Keys.TAB -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB) }
            Keys.COPY -> { feedback(); ic?.performContextMenuAction(android.R.id.copy) }
            Keys.CUT -> { feedback(); ic?.performContextMenuAction(android.R.id.cut) }
            Keys.PASTE -> { feedback(); ic?.performContextMenuAction(android.R.id.paste) }
            Keys.SELECT_ALL -> { feedback(); ic?.performContextMenuAction(android.R.id.selectAll) }
            Keys.FWD_DEL -> { feedback(); sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL) }
            Keys.HOME -> { feedback(); sendArrow(KeyEvent.KEYCODE_MOVE_HOME) }
            Keys.END -> { feedback(); sendArrow(KeyEvent.KEYCODE_MOVE_END) }
        }
    }

    /** Arrows honour edit-panel select mode by holding Shift. */
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

    override fun onSymLongPress() {
        feedback()
        val kv = keyboardView ?: return
        val items = Layouts.menuItems()
        kv.showGridMenu(items.map { it.first }, initial = 0) { which ->
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
    }

    private fun showLanguageMenu() {
        val kv = keyboardView ?: return
        kv.showGridMenu(languages.map { it.nativeName }, initial = langIndex) { which ->
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

    // -------------------------------------------------------- suggestions
    private fun updateSuggestions() {
        bestCandidate = null
        val bar = suggestionBar ?: return
        bar.removeAllViews()
        if (!suggestionsOn || isPasswordField) return
        val kv = keyboardView ?: return
        val prefix = wordBuffer.toString()

        // item text to (isAccent, isTypedWord, isClip)
        val items = ArrayList<Triple<String, Int, Boolean>>() // text, style, isClip
        val STYLE_NORMAL = 0
        val STYLE_ACCENT = 1
        val STYLE_TYPED = 2

        if (prefix.isEmpty()) {
            pendingClip?.let { items.add(Triple(it, STYLE_ACCENT, true)) }
            if (bigramsOn && lastWord.isNotEmpty()) {
                for (w in store().suggestNext(lastWord, 4)) {
                    items.add(Triple(w, STYLE_NORMAL, false))
                }
            }
        } else {
            if (autotextOn) {
                for (e in autoTextStore().matching(prefix, 2)) {
                    items.add(Triple(e, STYLE_ACCENT, false))
                }
            }
            val cands = store().suggestSmart(prefix, 6, seedDictOn, confusion())
            bestCandidate = cands.firstOrNull()
            if (cands.isEmpty() && items.isEmpty()) {
                // unknown word: show it live; long-press saves it
                items.add(Triple(prefix, STYLE_TYPED, false))
            } else {
                for ((i, c) in cands.withIndex()) {
                    items.add(Triple(c.word, if (i == 0) STYLE_ACCENT else STYLE_NORMAL, false))
                }
            }
        }

        val density = resources.displayMetrics.density
        for ((text, style, isClip) in items) {
            val tv = TextView(this)
            tv.text = if (isClip) getString(R.string.clip_prefix) + " " + text.take(40) else text
            tv.setTextColor(if (style == STYLE_ACCENT) kv.theme.accent else kv.theme.text)
            if (style == STYLE_ACCENT) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            tv.textSize = suggFontSp
            tv.maxLines = 1
            tv.setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            tv.gravity = android.view.Gravity.CENTER
            tv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            when {
                isClip -> {
                    tv.setOnClickListener {
                        currentInputConnection?.commitText(text, 1)
                        pendingClip = null
                        feedback()
                        updateSuggestions()
                    }
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
