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
        private const val SOUND_STANDARD = 0
        private const val SOUND_MODIFIER = 1
        private const val SOUND_DELETE = 2
        /**
         * Landscape height as a percentage of the standard size. A new key:
         * the old key_height_land held dp values from earlier builds, which
         * kept shrinking the keyboard after the sizing rule changed.
         */
        private const val LAND_KEY = "land_height_pct"
        private const val LAND_STANDARD = 100

        /** long-press token on the enter key: commit a real newline */
        private const val SHIFT_ENTER = "⇧↵"

        /** separators that delete a space left before them ("word ،"→"word،") */
        private val TIGHT_PUNCT = setOf("،", ".", ":", "۔", "؛")
        /** Keys that end a word and may auto-replace it (Samsung: space,
         *  . , ! ? — plus their Arabic-script forms). */
        private val AUTO_TRIGGERS = setOf(" ", ".", ",", "!", "?", "،", "؟", "۔", "؛")
        /** Marks that end a sentence: the next word starts a new context. */
        private val SENTENCE_END = setOf(".", "!", "?", "؟", "۔", "\n")
        /** Marks inside a sentence that keep the word sequence going. */
        private val SOFT_PUNCT = setOf(",", "،", "؛", ";", ":")
        /** Log-frequency points one unit of slip costs when weighing a
         *  replacement (70 ≈ a factor of 5.7 per everyday slip). */
        private const val LIKELIHOOD_PER_EDIT = 70f
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
    private var soundType = "keyflux"
    /** Which click the next [feedback] plays (Keyflux sound set). */
    private var soundKind = SOUND_STANDARD
    private var modSoundId = 0
    private var delSoundId = 0

    private var autocorrectOn = true
    private var isPasswordField = false
    private var revertOriginal: String? = null
    private var revertCorrected: String? = null
    private val rejectedWords = HashSet<String>()

    /**
     * Words committed but not learned yet. A word is only evidence once the
     * user has moved on and it is still there (see [WordStore]): backspacing
     * into it, or an edit that removes it, cancels it before it counts.
     */
    private class PendingWord(
        val word: String, val code: String, val signal: WordStore.Signal,
        val suspect: Boolean, val prev: List<String>
    )
    private val pendingWords = ArrayDeque<PendingWord>()
    /** Fields whose text must never teach the dictionary (passwords, URLs,
     *  e-mail, "no suggestions", incognito). */
    private var noLearnField = false
    /** Fields where a word must never be replaced on its own. */
    private var noAutoReplaceField = false
    /** The last key was delete: an emptied field was cleared by the user,
     *  not sent. */
    private var lastWasDelete = false
    private var lastDataVersion = -1
    private var bestCandidate: WordStore.Cand? = null
    private val confusionMaps = HashMap<String, Map<Char, Set<Char>>>()

    private var clipStore: ClipboardStore? = null
    // clip chip: with clip_chip_repeat OFF the chip disappears after one use
    // — permanently, across every field and app (persisted as a fingerprint)
    /** Clipboard chip: "never", "once" (spent when used) or "always". */
    private var clipMode = "once"
    private var consumedClipKey: String? = null

    private fun clipKey(t: String) = "${t.hashCode()}:${t.length}"

    private val wordStores = HashMap<String, WordStore>()
    private val preloadedLangs = HashSet<String>()
    /** One shortcut list per keyboard language. */
    private val autoTexts = HashMap<String, AutoTextStore>()
    private val wordBuffer = StringBuilder()
    private var lastWord = ""

    private val lang: Language get() = languages[langIndex]

    private fun store(): WordStore =
        wordStores.getOrPut(lang.code) { WordStore(this, lang.code) }

    private fun autoTextStore(): AutoTextStore =
        autoTexts.getOrPut(lang.code) { AutoTextStore(this, lang.code) }

    private fun confusion(): Map<Char, Set<Char>> =
        confusionMaps.getOrPut(lang.code) { Layouts.confusionMap(lang) }

    // ------------------------------------------------------- spatial model
    /**
     * Where the finger landed for each character of the word being typed.
     * Keeping the touch POINT — not just the letter it resolved to — is what
     * lets the decoder tell a near-miss on the neighbouring key from a
     * genuinely different letter, the way Gboard's spatial model does.
     * Parallel to [wordBuffer]; cleared and trimmed with it.
     */
    private val wordTaps = ArrayList<SpatialModel.Tap>()
    private var pendingTap: SpatialModel.Tap? = null
    private var touchCal: TouchCalibration? = null

    private fun calibration(): TouchCalibration =
        touchCal ?: TouchCalibration(this).also { touchCal = it }

    override fun onCharTap(text: String, x: Float, y: Float) {
        val kv = keyboardView
        pendingTap = if (text.length == 1)
            SpatialModel.Tap(text[0], x, y, kv?.lastTapOffX ?: 0f, kv?.lastTapOffY ?: 0f) else null
    }

    /**
     * Per-typed-character alternatives for the current word, built once per
     * character as it is typed. Recomputing the whole word on every keystroke
     * meant re-measuring every key against every tap for each new letter.
     */
    private val wordTapAlts = ArrayList<List<Pair<Char, Float>>>()

    private fun tapAlternatives(): List<List<Pair<Char, Float>>> =
        if (wordTapAlts.size == wordBuffer.length) wordTapAlts else emptyList()

    private fun recordTapAlternatives(tap: SpatialModel.Tap) {
        val kv = keyboardView ?: return
        val boxes = kv.letterKeyBoxes()
        if (boxes.isEmpty()) return
        wordTapAlts.add(SpatialModel.alternativesFor(tap, boxes))
    }

    private fun clipboardStore(): ClipboardStore =
        clipStore ?: ClipboardStore(this).also { clipStore = it }

    // ------------------------------------------------------------ lifecycle
    override fun attachBaseContext(newBase: android.content.Context) {
        // the keyboard's own labels follow the chosen app language too
        // the keyboard has its own themes; only the language is applied
        super.attachBaseContext(AppLocale.wrap(newBase, withLook = false))
    }

    /**
     * The service outlives a language change made in the settings app, so
     * re-apply the chosen locale to its resources whenever settings reload.
     */
    @Suppress("DEPRECATION")
    private fun syncAppLocale() {
        try {
            val code = AppLocale.current(this)
            val cfg = resources.configuration
            val currentLang =
                if (android.os.Build.VERSION.SDK_INT >= 24) cfg.locales[0].language
                else cfg.locale.language
            if (currentLang != code) {
                val loc = java.util.Locale(code)
                java.util.Locale.setDefault(loc)
                cfg.setLocale(loc)
                resources.updateConfiguration(cfg, resources.displayMetrics)
            }
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        // Vazirmatn for every text the keyboard shows (must precede super)
        setTheme(R.style.Theme_Hk_Ime)
        super.onCreate()
        ThemePresets.bootstrap(this)
        AutoTextStore.bootstrapDefaults(this)
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.addPrimaryClipChangedListener {
                // a REAL new copy — this is the only place that re-arms the
                // one-shot chip
                captureClipboard(cm, freshCopy = true)
                // refresh the strip so a freshly copied text shows at once
                if (keyboardView?.visibility == View.VISIBLE) updateSuggestions()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Read the current system clipboard text into the history store.
     * [freshCopy] is true only from the clipboard-changed listener: ONLY a
     * genuinely new copy re-arms the one-shot chip. The keyboard-open path
     * (onStartInputView) re-reads the same clipboard, and clearing the flag
     * there brought a consumed chip back on every keyboard show / field
     * switch / app switch.
     */
    private fun captureClipboard(
        cm: android.content.ClipboardManager,
        freshCopy: Boolean = false
    ) {
        val text = try {
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                ?.trim()?.ifEmpty { null }
        } catch (_: Exception) { null }
        if (text != null) {
            clipboardStore().add(text)
            if (freshCopy) {
                consumedClipKey = null
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().remove("consumed_clip").apply()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // rotation: re-derive height and split for the new orientation
        keyboardView?.post {
            applySettings()
            rebuildKeyboard()
        }
    }

    override fun onCreateInputView(): View {
        val kv = KeyboardView(this)
        kv.listener = this
        kv.calibration = calibration()
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

        // The suggestion strip and the resize bar share one frame: the thin
        // blue line sits ON the strip's top edge (like a stroke) and the
        // draggable pill floats above it — no extra bar is inserted, so the
        // keyboard never grows a black band in resize mode.
        val stripFrame = android.widget.FrameLayout(this)
        stripFrame.addView(
            scroll,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        buildResizeOverlay(stripFrame, density)
        root.addView(
            stripFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (40 * density).toInt()
            )
        )
        // The panel holder sits ABOVE the keys. For the emoji/kaomoji/clipboard
        // panels the keyboard is hidden so the order does not show, but emoji
        // SEARCH keeps both on screen — results on top, letters underneath,
        // the way Gboard and Samsung lay it out.
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
        root.addView(
            kv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootView = root
        // re-measure the gap whenever the system insets change (rotation,
        // switching between gesture and 3-button navigation, etc.) or the
        // keyboard moves on screen
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            gapCheck()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> gapCheck() }
        applySettings()
        rebuildKeyboard()
        applyWindowDirection()
        registerBackCallback()
        return root
    }

    // ------------------------------------------------------- resize mode
    private var resizeMode = false
    private var resizeLine: View? = null
    private var resizePill: android.widget.ImageView? = null

    /** exact (fractional) key height, so dragging feels perfectly smooth */
    private var keyHeightExact = 72f

    /**
     * Thin blue stroke pinned to the TOP EDGE of the suggestion strip plus a
     * draggable pill — both overlaid on the strip frame, so resize mode adds
     * no extra bar and the keyboard layout never shifts.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun buildResizeOverlay(frame: android.widget.FrameLayout, density: Float) {
        val line = View(this)
        line.visibility = View.GONE
        frame.addView(
            line,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                maxOf(1, (1.4f * density).toInt()),   // thin stroke
                android.view.Gravity.TOP
            )
        )
        resizeLine = line

        val pill = android.widget.ImageView(this)
        pill.visibility = View.GONE
        pill.scaleType = android.widget.ImageView.ScaleType.CENTER
        frame.addView(
            pill,
            android.widget.FrameLayout.LayoutParams(
                (66 * density).toInt(), (26 * density).toInt(),
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            )
        )
        resizePill = pill

        // Drag the pill to resize the keyboard live.
        //
        // The height must follow the finger EXACTLY 1:1, or the drag feels
        // like it is stepping: the keyboard is several rows tall, so 1 dp of
        // key height moves the top edge by `units` dp. Dividing the finger
        // delta by `units` makes the edge travel exactly as far as the
        // finger, which is what reads as smooth.
        var startY = 0f
        var startHeight = 0f
        var startUnits = 5f
        pill.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY
                    startHeight = keyHeightExact
                    startUnits = currentHeightUnits()
                    // keep receiving moves even when the finger leaves the pill
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    feedback()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // dragging UP makes the keyboard taller
                    val dDp = (startY - ev.rawY) / density / startUnits
                    val newH = (startHeight + dDp).coerceIn(38f, 110f)
                    // re-lay out whenever the total height moves by ≥1 px,
                    // so every frame the finger produces is drawn
                    if (kotlin.math.abs(newH - keyHeightExact) * startUnits * density >= 1f) {
                        keyHeightExact = newH
                        applyLiveKeyHeight()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    saveKeyHeight()
                    true
                }
                else -> false
            }
        }
    }

    /** How many key-height units tall the keyboard currently is. */
    private fun currentHeightUnits(): Float =
        heightUnits(lang.rows.size + 1, arrowsOn).coerceAtLeast(1f)

    /**
     * Push the current (fractional) height into the view without rebuilding
     * the whole keyboard — just a re-measure, so every frame is smooth.
     */
    private fun applyLiveKeyHeight() {
        val kv = keyboardView ?: return
        baseKeyHeightDp = kotlin.math.round(keyHeightExact).toInt()
        val lettersUnits = heightUnits(lang.rows.size + 1, arrowsOn)
        val arrowInMode = arrowsOn &&
            (mode == Mode.LETTERS || mode == Mode.SYM1 || mode == Mode.SYM2 ||
                mode == Mode.EMOJI_SEARCH)
        val rows = currentRows()
        val bodyRows = rows.size - (if (arrowInMode) 1 else 0)
        val modeUnits = heightUnits(bodyRows, arrowInMode)
        kv.keyHeightDpF = keyHeightExact * lettersUnits / modeUnits
        kv.requestLayout()
        kv.invalidate()
    }

    private fun saveKeyHeight() {
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        if (isLandscapeNow()) {
            // landscape stores a scale of the automatic height, not dp
            val v = (baseKeyHeightDp / landscapeAutoKeyDp() * LAND_STANDARD).toInt().coerceIn(70, 130)
            p.edit().putInt(LAND_KEY, v).apply()
        } else {
            p.edit().putInt("key_height", baseKeyHeightDp).apply()
        }
    }

    /**
     * The screen's short side in landscape, from the live system metrics:
     * the service's own resources and window manager can still hold the
     * portrait size captured at start-up, which made the rows max out.
     */
    private fun screenHeightDp(): Float {
        val dm = android.content.res.Resources.getSystem().displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels) / dm.density
    }

    /**
     * Standard landscape row height. AOSP gives the key rows 45-46% of the
     * screen height; Samsung's keyboard sits a little taller, so the rows
     * take 48% and, with the suggestion bar, the keyboard covers ~57%.
     */
    private fun landscapeAutoKeyDp(): Float =
        screenHeightDp() * 0.48f / currentHeightUnits()

    /** Toggle the live keyboard-resize mode. */
    private fun toggleResizeMode() {
        resizeMode = !resizeMode
        val kv = keyboardView
        val density = resources.displayMetrics.density
        val accent = kv?.theme?.accent ?: 0xFF4FA3FF.toInt()

        if (resizeMode) {
            keyHeightExact = baseKeyHeightDp.toFloat()
            resizeLine?.setBackgroundColor(accent)
            val pillBg = android.graphics.drawable.GradientDrawable()
            pillBg.setColor(accent)
            pillBg.cornerRadius = 13 * density
            resizePill?.background = pillBg
            resizePill?.setImageDrawable(
                tintedIcon(R.drawable.ic_drag_handle, 0xFFFFFFFF.toInt(),
                    (18 * density).toInt())
            )
            resizeLine?.visibility = View.VISIBLE
            resizePill?.visibility = View.VISIBLE
            // dim the keyboard and block all key input while resizing
            kv?.alpha = 0.45f
            kv?.inputBlocked = true
            emojiHolder?.alpha = 0.45f
        } else {
            resizeLine?.visibility = View.GONE
            resizePill?.visibility = View.GONE
            kv?.alpha = 1f
            kv?.inputBlocked = false
            emojiHolder?.alpha = 1f
            saveKeyHeight()
            rebuildKeyboard()
        }
        updateSuggestions()
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

    /** The navigation-bar overlap currently padded under the keys. */
    private var appliedNavGap = 0
    /** Re-measurements that changed the gap since the window was shown. */
    private var gapChanges = 0

    private fun gapCheck() {
        val root = rootView ?: return
        root.post {
            val g = navOverlapPx()
            // a window that is not anchored to the bottom would chase its own
            // padding: a few corrections per showing are plenty
            if (g != appliedNavGap && gapChanges < 4) {
                gapChanges++
                appliedNavGap = g
                applyBottomGap()
            }
        }
    }

    /**
     * How many pixels of the keyboard sit on top of the system navigation
     * area (3-button bar, gesture pill or the IME's own back / switch
     * strip), measured on screen.
     *
     * Earlier versions trusted the window's insets, but those report the
     * bar whether or not the keyboard window actually reaches under it, and
     * on many phones the system already keeps the window above the bar: the
     * bar got counted twice, leaving an empty band above the buttons. Where
     * the keyboard really ends on screen does not depend on any of that. The
     * keyboard view is anchored to the bottom of its window, so this does
     * not change when the padding does.
     */
    private fun navOverlapPx(): Int {
        val root = rootView ?: return 0
        if (!root.isAttachedToWindow || root.height == 0) return 0
        return try {
            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            val bottom = loc[1] + root.height
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            var barH = 0
            val screenH: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val m = wm.maximumWindowMetrics
                screenH = m.bounds.height()
                barH = m.windowInsets.getInsets(
                    android.view.WindowInsets.Type.navigationBars() or
                        android.view.WindowInsets.Type.tappableElement()
                ).bottom
            } else {
                val p = android.graphics.Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(p)
                screenH = p.y
            }
            androidx.core.view.ViewCompat.getRootWindowInsets(root)?.let {
                val t = it.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars() or
                        androidx.core.view.WindowInsetsCompat.Type.tappableElement()
                ).bottom
                if (t > barH) barH = t
            }
            if (screenH <= 0 || barH <= 0) return 0
            val cap = (72 * resources.displayMetrics.density).toInt()
            (bottom - (screenH - barH)).coerceIn(0, cap)
        } catch (_: Exception) { 0 }
    }

    /** Bottom padding under the keys: the nav-bar overlap, plus the manual
     *  extra space when automatic mode is off. Applied to the emoji/clipboard
     *  holder too so those panels keep the same gap above the system bar. */
    private fun applyBottomGap() {
        val kv = keyboardView ?: return
        val density = resources.displayMetrics.density
        val pad = appliedNavGap + if (navGapAuto) 0 else (manualBottomGapDp * density).toInt()
        kv.setPadding(0, 0, 0, pad)
        kv.requestLayout()
        // the gap belongs to whichever view is bottom-most: in emoji SEARCH
        // the keys sit below the panel, so the panel must not add it too
        emojiHolder?.setPadding(0, 0, 0, if (kv.visibility == View.GONE) pad else 0)
        emojiHolder?.requestLayout()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // positions are only final once the window is up: measure afresh
        gapChanges = 0
        gapCheck()
        // the decor is recreated with the window, so re-assert both each time
        applyWindowDirection()
        registerBackCallback()
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        ownSels.clear()
        knownSel = info?.initialSelEnd?.takeIf { it >= 0 && it == info.initialSelStart } ?: -1
        // the back dispatcher is rebuilt with the input connection, so the
        // callback has to be re-seated here, not just once at startup
        registerBackCallback()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // tell the system the back/hide button really does dismiss us — some
        // builds consult this before routing the press to the IME at all
        try {
            setBackDisposition(BACK_DISPOSITION_WILL_DISMISS)
        } catch (_: Throwable) {
        }
        registerBackCallback()
        // self-heal: a stuck pending flag must never survive a keyboard open
        suggHandler.removeCallbacks(suggRunnable)
        suggPending = false
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
        val noSugg = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val incognito = (info?.imeOptions ?: 0) and 0x1000000 != 0  // IME_FLAG_NO_PERSONALIZED_LEARNING
        val privateOpts = info?.privateImeOptions ?: ""
        val webOrMail = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        // Samsung's rules: no learning in password, incognito, e-mail / URL
        // fields or where the app asks for none ...
        noLearnField = isPasswordField || noSugg || incognito || webOrMail ||
            (cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_FILTER) ||
            privateOpts.contains("disableLearning")
        // ... and no auto-replacement there, nor in names and addresses
        noAutoReplaceField = isPasswordField || noSugg || webOrMail ||
            (cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME ||
                variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)) ||
            privateOpts.contains("disableAutoReplacement")
        // a new field is a new session for the temporary model
        for (st in wordStores.values) st.clearSession()
        ctxWords.clear()
        lastWasDelete = false
        pendingWords.clear()
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
        wordTaps.clear()
        wordTapAlts.clear()
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

    override fun onFinishInput() {
        settleAllWords(verify = false)
        for (s in wordStores.values) s.save()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        settleAllWords(verify = true)
        // what survived is learned for good; the temporary model starts over
        for (st in wordStores.values) st.clearSession()
        for (s in wordStores.values) s.save()
        for (a in autoTexts.values) a.save()
        keyboardView?.dismissPopups()
        wordMenu?.dismiss()
        wordMenu = null
        // never leave the keyboard dimmed/blocked behind a hidden resize bar
        if (resizeMode) toggleResizeMode()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (newSelStart == newSelEnd && ownSels.isNotEmpty() && ownSels.contains(newSelEnd)) {
            // the cursor landed where one of our own letters put it: the word
            // buffer is already right, so skip the round trip to the app
            while (ownSels.removeFirst() != newSelEnd) { /* drop the ones it overtook */ }
            if (ownSels.isEmpty()) knownSel = newSelEnd
            return
        }
        ownSels.clear()
        knownSel = if (newSelStart == newSelEnd) newSelEnd else -1
        if (newSelStart != newSelEnd) {
            // an active selection has no "current word"
            wordBuffer.setLength(0)
            wordTaps.clear()
            wordTapAlts.clear()
            updateSuggestions()
        } else {
            // re-derive the current word from around the cursor, so moving
            // the cursor into a word brings back its suggestions
            syncBufferFromCursor()
        }
    }

    /**
     * Cursor positions our own letter commits will produce, in order. AOSP's
     * keyboard keeps the same book: a selection update it caused itself
     * needs no re-reading of the text, and reading it on every keystroke
     * (a blocking call into the app, slower the busier the app is) was the
     * other half of the typing lag in long messages.
     */
    private val ownSels = ArrayDeque<Int>()
    /** The cursor per the latest update plus our pending commits; -1 = unknown. */
    private var knownSel = -1

    private fun syncBufferFromCursor() {
        val ic = currentInputConnection ?: return
        // 64 characters: the current word and the context for the next one,
        // as much as Samsung's keyboard reads
        val before = try { ic.getTextBeforeCursor(64, 0) } catch (_: Exception) { null } ?: ""
        if (before.isEmpty() && pendingWords.isNotEmpty() && !lastWasDelete) {
            // the app emptied the field without our delete key: the message
            // was sent, so the words waiting to be confirmed were meant
            val after = try { ic.getTextAfterCursor(1, 0) } catch (_: Exception) { null }
            if (after != null && after.isEmpty()) {
                settleAllWords(verify = false)
                for (st in wordStores.values) st.clearSession()
            }
        }
        var start = before.length
        while (start > 0 && Character.isLetter(before[start - 1])) start--
        val word = before.substring(start)
        contextFrom(before.subSequence(0, start))
        if (word != wordBuffer.toString()) {
            wordBuffer.setLength(0)
            wordTaps.clear()
            wordTapAlts.clear()
            wordBuffer.append(word)
        }
        // always refresh: the clipboard chip must re-evaluate on every cursor
        // move (e.g. entering a fresh empty line where the buffer was already
        // empty), so it never gets stuck showing or hidden
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * The framework's hide-keyboard button only works if the view it lives in
     * was inflated with THIS SERVICE as its context. From AOSP's
     * `KeyButtonView.sendEvent()`:
     *
     *     if (mContext instanceof InputMethodService) {
     *         final InputMethodService ims = (InputMethodService) mContext;
     *         handled = ims.onKeyDown(ev.getKeyCode(), ev);
     *
     * The whole body sits inside that check — if it fails the press does
     * literally nothing. And it was failing: to show the UI in the chosen app
     * language we wrap the base context with createConfigurationContext(), so
     * `LayoutInflater.from(service)` came back holding that configuration
     * context rather than the service, and the `instanceof` was false.
     *
     * The IME-switch button never went through this path — it calls
     * `mService.onImeSwitchButtonClickFromClient()` on a stored reference —
     * which is exactly why only the hide button was dead.
     *
     * Handing out an inflater cloned into this service fixes the check while
     * leaving the wrapped configuration (and so the app language) intact.
     */
    private var imeInflater: android.view.LayoutInflater? = null

    override fun getSystemService(name: String): Any? {
        if (LAYOUT_INFLATER_SERVICE == name) {
            imeInflater?.let { return it }
            val base = super.getSystemService(name) as? android.view.LayoutInflater
                ?: return null
            val cloned = base.cloneInContext(this)
            imeInflater = cloned
            return cloned
        }
        return super.getSystemService(name)
    }

    // ------------------------------------------- system navigation buttons
    //
    // While the keyboard is up the system shows two buttons under it: switch
    // IME and hide keyboard. Because our window is laid out edge to edge, it
    // is InputMethodService — i.e. our own process — that draws that little
    // bar inside the IME window, so both of its problems are ours to fix:
    //
    //   * the hide (down-arrow) button did nothing. Apps targeting SDK 35+
    //     get predictive back by default, which retires the old KEYCODE_BACK
    //     path; nothing was registered on the new dispatcher in its place, so
    //     the press was simply dropped. We now register an explicit callback
    //     and keep the legacy key handling for older releases.
    //
    //   * the two buttons sat in the opposite order to Samsung/Gboard. That
    //     bar is a child of the IME window's decor view, so it mirrors with
    //     the decor's layout direction — which stayed LTR. Following the UI
    //     language puts the buttons where every other keyboard puts them.

    private var backCallback: Any? = null

    /**
     * The dispatcher the callback is currently registered on. With predictive
     * back the IME's dispatcher is a PROXY the framework re-creates whenever
     * the input connection is rebuilt — a callback left on the previous one
     * is simply never invoked again. Tracking the instance lets us move the
     * callback across instead of registering once and going deaf.
     */
    private var backDispatcher: Any? = null

    /**
     * Hide the keyboard, leaving no panel or resize state behind.
     *
     * Two mechanisms, because they fail independently: requestHideSelf() asks
     * the input-method manager to dismiss us, while hideSoftInputFromWindow()
     * goes through the token of the window we are attached to. Whichever one
     * the device honours, the keyboard closes.
     */
    private fun hideKeyboardFromNavBar() {
        if (resizeMode) toggleResizeMode()
        if (mode != Mode.LETTERS) {
            mode = Mode.LETTERS
            rebuildKeyboard()
            updateSuggestions()
        }
        try {
            requestHideSelf(0)
        } catch (_: Throwable) {
        }
        try {
            val token = window?.window?.attributes?.token
            if (token != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(token, 0)
            }
        } catch (_: Throwable) {
        }
    }

    @android.annotation.TargetApi(33)
    private fun registerBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        try {
            val dispatcher = window?.onBackInvokedDispatcher ?: return
            // already on THIS dispatcher — nothing to do
            if (backCallback != null && backDispatcher === dispatcher) return
            // the dispatcher was swapped underneath us: drop the stale
            // registration before taking out a new one
            unregisterBackCallback()
            val cb = android.window.OnBackInvokedCallback { hideKeyboardFromNavBar() }
            dispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb
            )
            backCallback = cb
            backDispatcher = dispatcher
        } catch (_: Throwable) {
        }
    }

    @android.annotation.TargetApi(33)
    private fun unregisterBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        try {
            val cb = backCallback as? android.window.OnBackInvokedCallback
            val disp = backDispatcher as? android.window.OnBackInvokedDispatcher
            if (cb != null && disp != null) disp.unregisterOnBackInvokedCallback(cb)
        } catch (_: Throwable) {
        }
        backCallback = null
        backDispatcher = null
    }

    /**
     * Mirror the IME window's decor with the DEVICE language, so the hide and
     * switch-keyboard buttons the service draws under the keyboard sit on the
     * same sides as they do for every other IME. It deliberately follows the
     * system locale rather than our in-app language: those two buttons belong
     * to the system, and Samsung/Gboard place them by the system locale too.
     * The input view keeps its own explicit LTR, so nothing inside the
     * keyboard moves.
     */
    private fun applyWindowDirection() {
        try {
            val decor = window?.window?.decorView as? android.view.ViewGroup ?: return
            // The device direction. NOT Resources.getSystem(), which returns
            // the framework's own defaults and "is not configured for the
            // current screen" — it reported LTR no matter the device locale,
            // so the previous attempt at this silently did nothing. The
            // application context is the right source: attachBaseContext
            // wraps only this service, so the Application still carries the
            // real device configuration.
            val dir = applicationContext.resources.configuration.layoutDirection
            decor.layoutDirection = dir
            // The framework's nav bar orders its buttons with a start group
            // and an end group inside a LinearLayout, so mirroring it is what
            // swaps hide and switch-keyboard onto the sides every other
            // keyboard puts them. It is added straight to the decor, so set
            // the direction on it rather than relying on inheritance.
            for (i in 0 until decor.childCount) {
                val child = decor.getChildAt(i)
                if (child.javaClass.simpleName == "NavigationBarFrame") {
                    if (child.layoutDirection != dir) {
                        child.layoutDirection = dir
                        child.requestLayout()
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    // Legacy path. Hide on the DOWN edge rather than tracking to the UP:
    // some devices deliver only the DOWN for this button, and a tracked
    // event that never gets its UP simply does nothing.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown && event.repeatCount == 0) {
            hideKeyboardFromNavBar()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown) return true
        return super.onKeyUp(keyCode, event)
    }

    // ------------------------------------------------------------- settings
    /**
     * Landscape right now. Read from the live display, not from this
     * service's resources: those come from the language-wrapped context
     * captured at start-up and keep reporting the orientation of that moment.
     */
    private fun isLandscapeNow(): Boolean {
        val dm = android.content.res.Resources.getSystem().displayMetrics
        return dm.widthPixels > dm.heightPixels
    }

    private fun applySettings() {
        syncAppLocale()
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        val kv = keyboardView ?: return
        kv.theme = KeyboardView.themeByName(p.getString("theme", "dark") ?: "dark")
        applyCustomColors(kv, p)
        val landscape =
            isLandscapeNow()
        baseKeyHeightDp = if (landscape) {
            // AOSP's rule (the keyboard Gboard grew from): in landscape the
            // key area takes 45-46% of the screen height. The landscape
            // height setting scales that (100% = standard), so the keyboard
            // fits every phone instead of using one fixed dp value.
            val scale = p.getInt(LAND_KEY, LAND_STANDARD) / LAND_STANDARD.toFloat()
            (landscapeAutoKeyDp() * scale).toInt().coerceIn(26, 64)
        } else p.getInt("key_height", 72)
        // keep the exact height in sync unless a live resize drag is running
        if (!resizeMode) keyHeightExact = baseKeyHeightDp.toFloat()
        kv.keyHeightDp = baseKeyHeightDp
        // short landscape rows would shrink the letters; keep them readable
        kv.minLabelRowDp = if (landscape) 50f else 0f
        kv.arrowRowScale = p.getInt("arrow_height", 69) / 100f
        kv.fontScale = p.getInt("font_scale", 64) / 100f
        kv.hintScale = p.getInt("hint_scale", 96) / 100f
        kv.cornerRadiusDp = p.getInt("corner_radius", 6)
        kv.keyGapDp = p.getInt("key_gap", 2) / 1.33f
        kv.showHints = p.getBoolean("hints", true)
        kv.showPreview = p.getBoolean("preview", true)
        kv.keyBorder = p.getBoolean("key_border", false)
        kv.labelTypeface = if (p.getBoolean("key_font_vazir", false)) Fonts.get(this)
            else android.graphics.Typeface.DEFAULT
        kv.spaceSwipeEnabled = p.getBoolean("space_swipe", true)
        // landscape splits automatically: thumbs reach both halves and the
        // middle of the screen stays visible
        kv.splitMode = p.getBoolean("split_kb", false) ||
            (landscape && p.getBoolean("split_landscape", true))
        kv.longPressTimeout = (p.getString("longpress", "200") ?: "200").toLong()
        val density = resources.displayMetrics.density
        navGapAuto = p.getBoolean("nav_gap_auto", true)
        manualBottomGapDp = p.getInt("bottom_gap", 10)
        applyBottomGap()

        vibrateOn = p.getBoolean("vibrate", true)
        vibrateMs = p.getInt("vibrate_ms", 20).toLong()
        soundOn = p.getBoolean("sound", true)
        soundVol = p.getInt("sound_vol", 5) / 100f
        soundType = p.getString("sound_type", "keyflux") ?: "keyflux"
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
        arrowsOn = p.getBoolean("arrows", false)
        clipMode = p.getString("clip_suggest", null)
            ?: if (p.getBoolean("clip_chip_repeat", false)) "always" else "once"
        consumedClipKey = p.getString("consumed_clip", null)

        val dv = p.getInt("data_version", 0)
        if (dv != lastDataVersion) {
            lastDataVersion = dv
            wordStores.clear()
            preloadedLangs.clear()
            autoTexts.clear()
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
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.key_search)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.key_send)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.key_next) + " ⇥"
            EditorInfo.IME_ACTION_GO -> getString(R.string.key_go)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.key_done)
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
                rows.addAll(Layouts.symbols2(lang))
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
                // the CURRENT language, not English — people search for
                // emoji in Pashto and Farsi too
                rows.addAll(lang.rows)
                rows.add(
                    listOf(
                        KeyDef("😀", code = Keys.EMOJI, width = 1.5f),
                        KeyDef("", code = Keys.LANG_CYCLE, width = 1.5f),
                        KeyDef(getString(R.string.key_search_hint), code = Keys.SPACE, width = 4f),
                        KeyDef("⌫", code = Keys.DELETE, width = 1.5f),
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
        kv.visibility = View.VISIBLE
        if (mode == Mode.EMOJI_SEARCH) {
            // results panel on top, keys below — both visible at once
            showEmojiSearchPanel()
        } else {
            emojiHolder?.visibility = View.GONE
            emojiSearchGrid = null
            emojiSearchField = null
        }

        val rows = currentRows()
        // scale the key height so control/numbers/symbol layouts occupy the
        // exact same total height as the letters layout
        val lettersUnits = heightUnits(lang.rows.size + 1, arrowsOn)
        val arrowInMode = arrowsOn &&
            (mode == Mode.LETTERS || mode == Mode.SYM1 || mode == Mode.SYM2 ||
                mode == Mode.EMOJI_SEARCH)
        val bodyRows = rows.size - (if (arrowInMode) 1 else 0)
        val modeUnits = heightUnits(bodyRows, arrowInMode)
        // fractional so a live resize keeps sub-dp precision
        kv.keyHeightDpF = keyHeightExact * lettersUnits / modeUnits

        kv.shiftState = if (mode == Mode.EDIT) (if (selectMode) 2 else 0) else shift
        kv.setKeyboard(rows, displayFor(rows))
        // the nav gap belongs to whichever view is now bottom-most
        applyBottomGap()
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
            tv.typeface = Fonts.get(tv.context)
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
        headerBtn(getString(R.string.clip_back), R.drawable.ic_key_back, 1f) {
            mode = Mode.LETTERS; rebuildKeyboard(); updateSuggestions()
        }
        val title = headerBtn(getString(R.string.clip_title), 0, 2f) {}
        title.setTextColor(theme.hint)
        headerBtn(getString(R.string.clip_clear), R.drawable.ic_key_trash, 1f) {
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
            tv.text = getString(R.string.clip_empty)
            tv.setTextColor(theme.hint)
            tv.textSize = 15f
            tv.typeface = Fonts.get(tv.context)
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
                tv.typeface = Fonts.get(tv.context)
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
        applyBottomGap()
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
        applyBottomGap()
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
            tabTextSize = 13f,
            skinTones = false
        ))
    }

    // ------------------------------------------------------------ listener
    override fun onChar(rawText: String) {
        soundKind = SOUND_STANDARD
        lastWasDelete = false
        feedback()
        revertOriginal = null
        revertCorrected = null

        if (mode == Mode.EMOJI_SEARCH) {
            // typing filters emoji instead of committing text. Any script
            // counts — isLetter() is true for Pashto/Farsi/Arabic letters
            // just as it is for Latin ones.
            if (rawText.length == 1 &&
                (Character.isLetter(rawText[0]) || Character.isDigit(rawText[0]))
            ) {
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
            if (knownSel >= 0) {
                knownSel += text.length
                ownSels.addLast(knownSel)
            }
            // keep the tap list aligned with the buffer; a character that did
            // not come from a tap (popup, autotext) contributes no evidence
            val tap = pendingTap
            pendingTap = null
            if (tap != null && wordTaps.size == wordBuffer.length - 1) {
                wordTaps.add(tap)
                recordTapAlternatives(tap)
            } else {
                wordTaps.clear()
                wordTapAlts.clear()
                wordTapAlts.clear()
            }
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
    /**
     * A separator (space, punctuation, emoji, newline) ends the current word:
     * auto-replacement and learning happen here, then the separator itself
     * is committed. [newline] marks an Enter that inserts a line break (an
     * Enter that sends or searches never corrects, as on Samsung).
     */
    private fun handleSeparator(sep: String, commitSep: Boolean = true, newline: Boolean = false) {
        val ic = currentInputConnection
        val word = wordBuffer.toString()
        val trigger = newline || sep in AUTO_TRIGGERS
        val taps = ArrayList(tapAlternatives())
        val touches = if (wordTaps.size == word.length) ArrayList(wordTaps) else null
        val cands = if (trigger && word.length >= 2 && ic != null && !isPasswordField)
            candidatesFor(word, taps) else emptyList()
        wordBuffer.setLength(0)
        wordTaps.clear()
        wordTapAlts.clear()

        if (word.isNotEmpty() && ic != null && !isPasswordField) {
            var committed = word
            val target = if (trigger) autoReplaceTarget(word, cands, taps) else null
            if (target != null) {
                ic.deleteSurroundingText(word.length, 0)
                val replacement = recaseLike(word, target.word)
                ic.commitText(replacement, 1)
                committed = replacement
                // backspace right after this replacement restores the
                // original word (Samsung-style revert)
                revertOriginal = word
                revertCorrected = replacement
            }
            if (committed.length >= 2) {
                if (committed != word) {
                    queueLearn(committed, WordStore.Signal.USED, false)
                } else {
                    // the decoder had a close correction the user did not
                    // take: maybe a word, maybe a slip — it needs more proof
                    val best = cands.firstOrNull()
                    val suspect = best != null && !best.exact && best.cost <= 1.0f &&
                        !store().contains(word)
                    queueLearn(committed, WordStore.Signal.TYPED, suspect)
                }
                if (learnWordsOn && !noLearnField) store().sessionLearn(committed)
            }
            // the touch model learns only from a word that came out exactly as
            // tapped and is a real word: every touch hit its letter
            if (committed == word && touches != null && !noLearnField && store().contains(word)) {
                teachTouches(touches, word)
            }
            lastWord = committed
            pushContext(committed)
        }
        if (newline || sep in SENTENCE_END) ctxWords.clear()
        else if (word.isEmpty() && sep.isNotEmpty() && sep != " " && sep.none { it.isLetter() }) {
            // a lone mark or emoji between words breaks the word sequence
            if (sep !in SOFT_PUNCT) ctxWords.clear()
        }
        bestCandidate = null
        bestFor = null
        bestCands = emptyList()
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
        lastWasDelete = false
        updateAutoCaps()
    }

    /** Feed the touch model the taps whose letter matches [word]. */
    private fun teachTouches(touches: List<SpatialModel.Tap>, word: String) {
        val cal = calibration()
        for ((i, t) in touches.withIndex()) {
            if (i < word.length && t.ch.lowercaseChar() == word[i].lowercaseChar()) cal.record(t.offX, t.offY)
        }
    }

    /** The words before the cursor in this sentence (oldest first, ≤ 3);
     *  empty at the start of a message or sentence. */
    private val ctxWords = ArrayList<String>()

    private fun pushContext(w: String) {
        if (w.none { it.isLetter() }) return
        ctxWords.add(w)
        while (ctxWords.size > 3) ctxWords.removeAt(0)
    }

    /** Re-derive [ctxWords] from the text before the current word. */
    private fun contextFrom(before: CharSequence) {
        ctxWords.clear()
        val t = before.toString()
        // only the current sentence counts
        var cut = -1
        for (i in t.indices.reversed()) {
            val c = t[i].toString()
            if (c in SENTENCE_END || c == "\n") { cut = i; break }
        }
        val words = t.substring(cut + 1).split(Regex("[^\\p{L}\\p{M}\u200C']+")).filter { it.isNotEmpty() }
        for (w in words.takeLast(3)) ctxWords.add(w)
    }

    /**
     * Should the typed [word] be replaced on its own, and by what — the
     * auto-replace rules of Samsung's keyboard (g50/a.shouldAutoReplace):
     *
     *  - never in fields where it does harm (password, e-mail, URL, names,
     *    addresses, no-suggestion fields, apps that ask for it off);
     *  - never for a word the user already uses (dictionary, learned, typed
     *    earlier in this field), a shortcut, or one whose correction they
     *    took back before (the revoked list);
     *  - never for shapes that are deliberate: digits, - _ # @, ALL CAPS,
     *    CamelCase, a capitalised name in mid-sentence, "x.y", possessives;
     *  - only to a word from the bundled dictionary — never to a word only
     *    the user taught — within two slips;
     *  - a completion only when at least 4 letters were typed and the word
     *    is 6 or more long;
     *  - and only when the target is clearly the likeliest: at least twice
     *    as likely as the runner-up when it fixes a slip, ten times as
     *    likely otherwise.
     */
    private fun autoReplaceTarget(
        word: String, cands: List<WordStore.Cand>,
        taps: List<List<Pair<Char, Float>>> = ArrayList(tapAlternatives())
    ): WordStore.Cand? {
        if (!autocorrectOn || !suggestionsOn || noAutoReplaceField) return null
        if (word.length < 2 || cands.isEmpty()) return null
        val s = store()
        if (!s.isReady || s.contains(word) || s.isLearnedVisible(word) || s.inSession(word)) return null
        if (rejectedWords.contains(word) || s.isRevoked(word)) return null
        if (autotextOn && autoTextStore().expansionFor(word) != null) return null
        if (word.any { it.isDigit() || it in "-_#@.\"'" }) return null
        if (word.length >= 2 && word.all { !it.isLetter() || it.isUpperCase() } && word.any { it.isUpperCase() }) return null
        // CamelCase / iPhone: a capital after the first letter
        if (word.drop(1).any { it.isUpperCase() }) return null
        // a capitalised word in mid-sentence is a name
        if (word[0].isUpperCase() && ctxWords.isNotEmpty()) return null

        // how likely each candidate is as THE word, on the log-frequency
        // scale: frequency minus the slips it takes to get there
        val conf = confusion()
        val readings = HashMap<String, WordStore.Reading>()
        for (c in cands) if (c.word !in readings) readings[c.word] = s.replaceReading(word, c.word, conf, taps)
        fun lk(c: WordStore.Cand) = c.freq - (readings.getValue(c.word).cost * LIKELIHOOD_PER_EDIT).toInt()
        fun eligible(c: WordStore.Cand): Boolean {
            val r = readings.getValue(c.word)
            if (!c.static || !r.withinLimits || c.word.equals(word, ignoreCase = true)) return false
            // a completion only from 4 typed letters and for words of 6+
            return r.completion == 0 || (word.length >= 4 && c.word.length >= 6)
        }
        val target = cands.filter { eligible(it) }.maxByOrNull { lk(it) } ?: return null
        val rival = cands.filter { it.word != target.word }.maxByOrNull { lk(it) }
        if (rival != null) {
            // log-frequency points: 40 per factor e, so ×2 ≈ 28 and ×10 ≈ 92;
            // a pure completion (no slip fixed) needs the larger margin
            val r = readings.getValue(target.word)
            val need = if (r.cost - r.completion * 2.0f > 0.001f) 28 else 92
            val gap = lk(target) - lk(rival)
            if (gap < need) return null
        }
        return target
    }

    /** Give the replacement the typed word's capitalisation. */
    private fun recaseLike(typed: String, w: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase() && w.isNotEmpty() && w[0].isLowerCase())
            w.replaceFirstChar { it.titlecase() } else w

    override fun onSpecial(code: Int) {
        val ic = currentInputConnection
        soundKind = if (code == Keys.DELETE) SOUND_DELETE else SOUND_MODIFIER
        if (mode == Mode.EMOJI_SEARCH) {
            when (code) {
                Keys.SPACE -> {
                    feedback()
                    // a space separates search terms; never leads
                    if (emojiQuery.isNotEmpty() && emojiQuery.last() != ' ') {
                        emojiQuery.append(' ')
                        updateEmojiSearch()
                    }
                    return
                }
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
                lastWasDelete = true
                if (tryRevertAutocorrect()) return
                if (wordBuffer.isEmpty()) cancelPendingAtCursor()
                if (wordBuffer.isNotEmpty()) wordBuffer.setLength(wordBuffer.length - 1)
                if (wordTaps.size > wordBuffer.length) {
                    wordTaps.subList(wordBuffer.length, wordTaps.size).clear()
                }
                if (wordTapAlts.size > wordBuffer.length) {
                    wordTapAlts.subList(wordBuffer.length, wordTapAlts.size).clear()
                }
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
                val info = currentInputEditorInfo
                val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                val noEnterAction =
                    info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                val sends = action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction
                handleSeparator("", commitSep = false, newline = !sends)
                ctxWords.clear()
                if (sends) {
                    // the message leaves the field: what was typed was meant
                    settleAllWords(verify = false)
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
                wordTaps.clear()
                wordTapAlts.clear()
                sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = false)
                updateSuggestions()
            }
            Keys.REDO -> {
                feedback()
                wordBuffer.setLength(0)
                wordTaps.clear()
                wordTapAlts.clear()
                sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = true)
                updateSuggestions()
            }
            Keys.KAOMOJI -> { feedback(); mode = Mode.KAOMOJI; rebuildKeyboard() }
            Keys.RESIZE -> { feedback(); toggleResizeMode() }
            Keys.HIDE -> { feedback(); hideKeyboardFromNavBar() }
            Keys.LANG_CYCLE -> switchLanguage(1)
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

    // ------------------------------------------------------------ learning
    private fun queueLearn(word: String, signal: WordStore.Signal, suspect: Boolean) {
        if (!suggestionsOn || noLearnField) return
        if (!learnWordsOn && !bigramsOn) return
        pendingWords.addLast(PendingWord(word, lang.code, signal, suspect, ArrayList(ctxWords)))
        // two words later the user has moved on: settle the oldest
        while (pendingWords.size > 2) settleWord(pendingWords.removeFirst(), verify = true)
    }

    /** Learn a held-back word — if it is still in the text. */
    private fun settleWord(p: PendingWord, verify: Boolean) {
        if (verify) {
            val ic = currentInputConnection
            val before = try { ic?.getTextBeforeCursor(800, 0)?.toString() } catch (_: Exception) { null }
            if (before != null && !containsWord(before, p.word)) return
        }
        val s = wordStores.getOrPut(p.code) { WordStore(this, p.code) }
        if (learnWordsOn) s.observe(p.word, p.signal, p.suspect)
        if (bigramsOn) s.learnSequence(p.prev, p.word)
    }

    private fun settleAllWords(verify: Boolean) {
        while (pendingWords.isNotEmpty()) settleWord(pendingWords.removeFirst(), verify)
    }

    /** [word] occurs in [text] as a whole word, not inside a longer one. */
    private fun containsWord(text: String, word: String): Boolean {
        var i = text.lastIndexOf(word)
        while (i >= 0) {
            val a = i == 0 || !Character.isLetter(text[i - 1])
            val e = i + word.length
            val b = e >= text.length || !Character.isLetter(text[e])
            if (a && b) return true
            i = if (i == 0) -1 else text.lastIndexOf(word, i - 1)
        }
        return false
    }

    /**
     * Backspace with no word in progress is about to eat into the word
     * before the cursor. If that is a word still waiting to be learned, the
     * user is correcting it: drop it, and count it against the word — the
     * "unlearn on backspace" rule of Android's own keyboard.
     */
    private fun cancelPendingAtCursor() {
        val last = pendingWords.lastOrNull() ?: return
        val ic = currentInputConnection ?: return
        val before = try {
            ic.getTextBeforeCursor(last.word.length + 3, 0)?.toString()
        } catch (_: Exception) { null } ?: return
        val trimmed = before.trimEnd { !Character.isLetter(it) }
        if (!trimmed.endsWith(last.word)) return
        pendingWords.removeLast()
        wordStores[last.code]?.penalize(last.word)
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
        // never auto-replace this word again (Samsung's revoked list)
        store().revoke(original)
        if (ctxWords.isNotEmpty()) ctxWords[ctxWords.size - 1] = original
        // the correction was wrong: it must not count, and the word the user
        // fought for is exactly the evidence a keyboard should learn from
        pendingWords.removeLastOrNull()
        if (learnWordsOn && !noLearnField) {
            store().penalize(corrected)
            store().observe(original, WordStore.Signal.REVERTED)
        }
        wordBuffer.setLength(0)
        wordTaps.clear()
        wordTapAlts.clear()
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
            // no particular area: the Settings tab, which lists all of them
            val intent = if (screen == null) MainActivity.intent(this, MainActivity.TAB_SETTINGS)
                else SettingsActivity.intent(this, screen)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        Keys.LANG_CYCLE -> R.drawable.ic_key_lang
        Keys.HIDE -> R.drawable.ic_key_arrow_down
        Keys.SETTINGS -> R.drawable.ic_key_settings
        Keys.SPLIT -> R.drawable.ic_key_split
        else -> 0
    }

    override fun onSymLongPress() {
        feedback()
        val kv = keyboardView ?: return
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val splitOn = p.getBoolean("split_kb", false)
        val items = Layouts.menuItems(this) +
            ((getString(R.string.menu_split) + (if (splitOn) " ✓" else "")) to Keys.SPLIT)
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
        // switching language while searching emoji keeps you in the search —
        // that is the whole point of being able to switch there
        if (mode != Mode.EMOJI_SEARCH) mode = Mode.LETTERS
        shift = 0
        wordBuffer.setLength(0)
        wordTaps.clear()
        wordTapAlts.clear()
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
            wordTaps.clear()
            wordTapAlts.clear()
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
    /**
     * The search index is the catalogue itself: one entry per emoji with its
     * tags already split. Tags carry English, Farsi and Arabic words, so the
     * same query box works whichever language the user is typing in.
     */
    private fun loadEmojiKeywords(): List<Pair<String, List<String>>> {
        emojiKeywords?.let { return it }
        val list = ArrayList<Pair<String, List<String>>>()
        try {
            for ((emoji, tags) in EmojiData.searchIndex(this)) {
                list.add(emoji to tags.split(' ').filter { it.isNotEmpty() })
            }
        } catch (_: Exception) {
        }
        emojiKeywords = list
        return list
    }

    // ------------------------------------------------- emoji search panel
    //
    // Laid out the way Gboard and Samsung do it: a search field with the
    // query and a clear button on top, the matching emoji in a scrollable
    // grid under it, and the ordinary keyboard — IN THE CURRENT LANGUAGE —
    // below that. Results are ranked, not capped at a single strip row.

    private var emojiSearchGrid: android.widget.GridView? = null
    private var emojiSearchField: TextView? = null

    /** Build the search panel into the panel holder above the keys. */
    private fun showEmojiSearchPanel() {
        val kv = keyboardView ?: return
        val holder = emojiHolder ?: return
        val density = resources.displayMetrics.density
        val theme = kv.theme
        val (keyCol, specialCol) = panelColors(kv)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(kv.resolvedBackground)

        // ---- header: back | search field | clear
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = android.view.Gravity.CENTER_VERTICAL

        fun iconBtn(iconRes: Int, click: () -> Unit): View {
            val iv = android.widget.ImageView(this)
            iv.setImageDrawable(tintedIcon(iconRes, theme.text, (20 * density).toInt()))
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(specialCol)
            bg.cornerRadius = 10 * density
            iv.background = bg
            iv.setOnClickListener { click() }
            return iv
        }

        val btnLp = LinearLayout.LayoutParams((44 * density).toInt(), (38 * density).toInt())
        btnLp.setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        header.addView(iconBtn(R.drawable.ic_key_back) {
            feedback()
            emojiQuery.setLength(0)
            mode = Mode.EMOJI
            rebuildKeyboard()
        }, btnLp)

        val field = TextView(this)
        field.maxLines = 1
        field.textSize = 16f
        field.typeface = Fonts.get(field.context)
        field.gravity = android.view.Gravity.CENTER_VERTICAL
        field.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
        val fieldBg = android.graphics.drawable.GradientDrawable()
        fieldBg.setColor(keyCol)
        fieldBg.cornerRadius = 19 * density
        field.background = fieldBg
        // the query is whatever the user typed — Pashto, Farsi, English…
        field.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        emojiSearchField = field
        val fieldLp = LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f)
        header.addView(field, fieldLp)

        header.addView(iconBtn(R.drawable.ic_key_backspace) {
            feedback()
            if (emojiQuery.isNotEmpty()) {
                emojiQuery.setLength(emojiQuery.length - 1)
                updateEmojiSearch()
            }
        }, btnLp)

        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (46 * density).toInt()))

        // ---- results: a scrollable grid, two rows tall
        val grid = android.widget.GridView(this)
        grid.numColumns = 8
        grid.layoutDirection = View.LAYOUT_DIRECTION_LTR
        grid.isVerticalScrollBarEnabled = false
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        grid.adapter = emojiResultAdapter
        emojiSearchGrid = grid
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (96 * density).toInt()))

        holder.removeAllViews()
        holder.addView(root)
        holder.visibility = View.VISIBLE
        applyBottomGap()
        updateEmojiSearch()
    }

    /** Current result list, shared by the adapter and the click handler. */
    private var emojiResults: List<String> = emptyList()

    private val emojiResultAdapter = object : android.widget.BaseAdapter() {
        override fun getCount() = emojiResults.size
        override fun getItem(position: Int): Any = emojiResults[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(
            position: Int, convertView: View?, parent: android.view.ViewGroup?
        ): View {
            val density = resources.displayMetrics.density
            val theme = keyboardView?.theme
            val cell = (convertView as? EmojiCell) ?: EmojiCell(this@MultilingIME).apply {
                maxLines = 1
                gravity = android.view.Gravity.CENTER
                textSize = 24f
                height = (46 * density).toInt()
            }
            val e = emojiResults[position]
            cell.text = e
            theme?.let { cell.setTextColor(it.text); cell.markColor = it.hint }
            cell.hasMore = EmojiData.variantsOf(this@MultilingIME, e).isNotEmpty()
            cell.setOnClickListener {
                currentInputConnection?.commitText(e, 1)
                feedback()
            }
            return cell
        }
    }

    /** Re-run the query and refresh the field and the result grid. */
    private fun updateEmojiSearch() {
        val q = emojiQuery.toString()
        val theme = keyboardView?.theme
        emojiSearchField?.let { f ->
            if (q.isEmpty()) {
                f.text = getString(R.string.emoji_search_hint)
                theme?.let { f.setTextColor(it.hint) }
            } else {
                // a caret so the field reads as a live text box
                f.text = q + "|"
                theme?.let { f.setTextColor(it.text) }
            }
        }
        emojiResults = if (q.isEmpty()) emptyList() else rankEmoji(q)
        emojiResultAdapter.notifyDataSetChanged()
        emojiSearchGrid?.setSelection(0)
    }

    /**
     * Rank by how the tag matched and how central it is to the emoji: an
     * exact hit beats a prefix beats a substring, and within each an early
     * tag beats a late one. So "flag" offers the real flags ahead of the
     * mailbox, whose name merely ends in "with raised flag".
     */
    private fun rankEmoji(rawQuery: String): List<String> {
        // several words mean "all of them": "afghanistan flag" -> 🇦🇫
        val terms = rawQuery.lowercase().split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<String, Int>>()
        for ((emoji, tags) in loadEmojiKeywords()) {
            var total = 0
            var matchedAll = true
            for (term in terms) {
                var best = Int.MAX_VALUE
                for ((i, t) in tags.withIndex()) {
                    val kind = when {
                        t == term -> 0
                        t.startsWith(term) -> 1
                        term.length >= 3 && t.contains(term) -> 2
                        else -> continue
                    }
                    val score = kind * 1000 + i
                    if (score < best) best = score
                    if (best == 0) break
                }
                if (best == Int.MAX_VALUE) { matchedAll = false; break }
                total += best
            }
            if (matchedAll) scored.add(emoji to total)
        }
        // sortedBy is stable, so ties keep the catalogue's Unicode order
        return scored.sortedBy { it.second }.map { it.first }.take(240)
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
    private val suggHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val suggRunnable = Runnable {
        suggPending = false
        try {
            buildSuggestions()
        } catch (_: Exception) {
            // never let one bad rebuild kill the strip permanently
        }
    }

    /** Coalesce bursts (selection-update storms after a big paste) into a
     *  single rebuild per frame — the strip work itself queries the target
     *  app for text, which is expensive while that app is busy.
     *  IMPORTANT: posted on a main-looper Handler, NOT on the view — a
     *  View.post while the input view is detached loses the runnable and
     *  left suggPending stuck true, deadening the strip forever. */
    private fun updateSuggestions() {
        if (suggPending) return
        suggPending = true
        if (!suggHandler.post(suggRunnable)) {
            suggPending = false
        }
    }

    /** One entry on the suggestion strip. */
    private class SuggItem(
        val text: String,
        val style: Int,
        val isClip: Boolean = false,
        val isAutoText: Boolean = false
    )

    /** What the dictionary said about one typed word (worker thread). */
    private class DictResult(
        val typedIsKnown: Boolean,
        val cands: List<WordStore.Cand>,
        val next: List<String>
    )

    /**
     * Suggestions are worked out on their own thread, the way AOSP's
     * keyboard does it: a key press commits its letter at once and the
     * strip follows a moment later. Searching the dictionary on the UI
     * thread made every keystroke wait for it, so fast typing lagged and
     * dropped letters once the dictionaries grew.
     */
    private val suggThread = android.os.HandlerThread("keyflux-suggest").also { it.start() }
    private val suggWorker = android.os.Handler(suggThread.looper)
    /** Bumped for every request; a result for an older one is dropped. */
    private var suggGen = 0
    /** The word [bestCandidate] / [bestCands] were computed for. */
    private var bestFor: String? = null
    /** The strip's candidates for [bestFor] (they decide auto-replace). */
    private var bestCands: List<WordStore.Cand> = emptyList()

    private fun buildSuggestions() {
        val bar = suggestionBar ?: return
        val gen = ++suggGen
        if (!suggestionsOn || isPasswordField || mode == Mode.EMOJI_SEARCH) {
            // in emoji search the query lives in the panel's own field, so the
            // strip stays empty instead of showing word suggestions for it
            bestCandidate = null
            bestFor = null
            bar.removeAllViews()
            return
        }
        val prefix = wordBuffer.toString()
        val store = store()
        val ctx = ArrayList(ctxWords)
        val seeds = seedDictOn
        val bigrams = bigramsOn
        val conf = confusion()
        val taps = ArrayList(tapAlternatives())
        // main-thread parts: the clipboard and the AutoText list are cheap
        // and owned by this thread
        val clip = if (prefix.isEmpty()) clipChipText() else null
        val autos = if (prefix.isNotEmpty() && autotextOn) autoTextStore().matching(prefix, 3)
            else emptyList()
        suggWorker.post {
            val r = try {
                if (prefix.isEmpty()) DictResult(false, emptyList(),
                    if (bigrams) store.suggestNext(ctx, 4, seeds) else emptyList())
                else DictResult(store.contains(prefix),
                    store.suggestSmart(prefix, 6, seeds, conf, taps), emptyList())
            } catch (_: Exception) { return@post }
            suggHandler.post {
                if (gen != suggGen) return@post
                try { showSuggestions(prefix, clip, autos, r) } catch (_: Exception) {}
            }
        }
    }

    /**
     * The clipboard chip for an empty line, or null. The setting decides:
     * never, once per copied text (spent on use), or every time.
     */
    private fun clipChipText(): String? {
        if (clipMode == "never") return null
        val item = clipboardStore().newest() ?: return null
        if (clipMode != "always" && clipKey(item) == consumedClipKey) return null
        // asking the app for the line costs a round trip: only when a chip
        // would actually show
        return if (isCurrentLineEmpty()) item else null
    }

    /** The candidates for [word], from the strip when it is current, else
     *  worked out now (a separator typed before the strip caught up). */
    private fun candidatesFor(word: String, taps: List<List<Pair<Char, Float>>>): List<WordStore.Cand> {
        if (bestFor == word) return bestCands
        if (!suggestionsOn || word.isEmpty()) return emptyList()
        val s = store()
        // still loading: no correction rather than a frozen space bar
        if (!s.isReady || s.contains(word)) return emptyList()
        return s.suggestSmart(word, 6, seedDictOn, confusion(), taps)
    }

    private fun showSuggestions(prefix: String, clip: String?, autos: List<String>, r: DictResult) {
        val bar = suggestionBar ?: return
        val kv = keyboardView ?: return
        bar.removeAllViews()
        bestCandidate = null
        bestFor = prefix
        bestCands = if (r.typedIsKnown) emptyList() else r.cands
        val items = ArrayList<SuggItem>()
        val STYLE_NORMAL = 0
        val STYLE_ACCENT = 1
        val STYLE_TYPED = 2

        if (prefix.isEmpty()) {
            clip?.let { items.add(SuggItem(it, STYLE_ACCENT, isClip = true)) }
            for (w in r.next) items.add(SuggItem(w, STYLE_NORMAL))
        } else if (r.typedIsKnown) {
            // the typed word is itself a valid word: highlight IT and never
            // auto-replace it; similar words are only tappable extras
            items.add(SuggItem(prefix, STYLE_ACCENT))
            for (e in autos) items.add(SuggItem(e, STYLE_NORMAL, isAutoText = true))
            for (c in r.cands) items.add(SuggItem(c.word, STYLE_NORMAL))
        } else {
            // unknown word: the word space would put in its place leads in
            // blue; with no confident target nothing is blue and space keeps
            // what was typed. The typed word stays available (long-press to
            // save it).
            items.add(SuggItem(prefix, STYLE_TYPED))
            for (e in autos) items.add(SuggItem(e, STYLE_ACCENT, isAutoText = true))
            val target = autoReplaceTarget(prefix, r.cands)
            bestCandidate = target
            target?.let { items.add(SuggItem(recaseLike(prefix, it.word), STYLE_ACCENT)) }
            for (c in r.cands) {
                if (c.word != target?.word) items.add(SuggItem(c.word, STYLE_NORMAL))
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
                R.drawable.ic_key_emoji to Keys.EMOJI,
                R.drawable.ic_key_resize to Keys.RESIZE
            )
            // spread the actions evenly over the whole strip width
            val stripW = (suggestionScroll?.width ?: 0).let {
                if (it > 0) it else resources.displayMetrics.widthPixels
            }
            for ((iconRes, code) in actions) {
                val iv = android.widget.ImageView(this)
                // the resize icon turns accent-blue while resize mode is on
                val active = code == Keys.RESIZE && resizeMode
                iv.setImageDrawable(
                    tintedIcon(
                        iconRes,
                        if (active) kv.theme.accent else kv.theme.hint,
                        (21 * density).toInt()
                    )
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
            tv.typeface = Fonts.get(this)
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
                        if (clipMode != "always") {
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
                        // The user is telling us this IS a word. That has to
                        // bypass the typo gate — otherwise the one deliberate
                        // way to teach the keyboard a word it thinks is a
                        // near-miss would be silently refused.
                        store().learnExplicit(text)
                        rejectedWords.add(text)
                        Toast.makeText(this, R.string.word_saved, Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                else -> {
                    tv.setOnClickListener { commitSuggestion(text) }
                    tv.setOnLongClickListener {
                        showWordMenu(tv, text)
                        true
                    }
                }
            }
            bar.addView(tv)
        }
    }

    private var wordMenu: android.widget.PopupWindow? = null

    /**
     * Small menu above a suggested word: raise its rank (so it is offered
     * sooner) or delete it from the dictionary.
     */
    private fun showWordMenu(anchor: View, word: String) {
        wordMenu?.dismiss()
        val kv = keyboardView ?: return
        val density = resources.displayMetrics.density
        val theme = kv.theme

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(if (kv.customColors && kv.colBg != 0) kv.colBg else theme.keyFill)
        bg.cornerRadius = 12 * density
        bg.setStroke((1 * density).toInt(), theme.accent)
        box.background = bg
        box.elevation = 8 * density

        fun row(label: String, iconRes: Int, tint: Int, action: () -> Unit) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 14.5f
            tv.typeface = Fonts.get(tv.context)
            tv.setTextColor(theme.text)
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            tv.setPadding((14 * density).toInt(), (12 * density).toInt(),
                (18 * density).toInt(), (12 * density).toInt())
            tintedIcon(iconRes, tint, (18 * density).toInt())?.let {
                tv.setCompoundDrawables(it, null, null, null)
                tv.compoundDrawablePadding = (10 * density).toInt()
            }
            tv.setOnClickListener {
                feedback()
                action()
                wordMenu?.dismiss()
                wordMenu = null
                updateSuggestions()
            }
            box.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        row(getString(R.string.word_boost), R.drawable.ic_key_arrow_up, theme.accent) {
            // an explicit request: trust it outright. repeat(learn) would be
            // refused now that learning rejects near-misses of real words,
            // and refusing the user's own instruction is not the intent.
            store().learnExplicit(word)
            Toast.makeText(this, R.string.word_boosted, Toast.LENGTH_SHORT).show()
        }
        row(getString(R.string.word_delete), R.drawable.ic_key_trash, 0xFFE05B5B.toInt()) {
            store().forget(word)
            rejectedWords.add(word)
            Toast.makeText(this, R.string.word_deleted, Toast.LENGTH_SHORT).show()
        }

        val popup = android.widget.PopupWindow(
            box,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isClippingEnabled = false
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        box.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val x = (loc[0] + anchor.width / 2 - box.measuredWidth / 2)
            .coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - box.measuredWidth))
        val y = loc[1] - box.measuredHeight - (6 * density).toInt()
        try {
            popup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
            wordMenu = popup
        } catch (_: Exception) {
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
        wordTaps.clear()
        wordTapAlts.clear()
        lastWord = ""
        bestCandidate = null
        feedback()
        updateSuggestions()
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = wordBuffer.toString()
        if (wordTaps.size == prefix.length && !noLearnField) teachTouches(ArrayList(wordTaps), word)
        // Replacing what you typed with something else is the clearest signal
        // that the typed form was wrong — withdraw the evidence for it, or a
        // habitual mistyping slowly accumulates its way into the dictionary.
        if (prefix.isNotEmpty() && prefix != word) store().penalize(prefix)
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        queueLearn(word,
            if (prefix == word) WordStore.Signal.PICKED_TYPED else WordStore.Signal.USED, false)
        if (learnWordsOn && !noLearnField) store().sessionLearn(word)
        lastWord = word
        pushContext(word)
        wordBuffer.setLength(0)
        wordTaps.clear()
        wordTapAlts.clear()
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
            if (soundType == "keyflux") {
                // three clicks: letters, the big keys (space / enter / shift
                // and the other function keys) and delete — the way phone
                // keyboards tell them apart by ear
                popSoundId = sp.load(this, R.raw.kf_standard, 1)
                modSoundId = sp.load(this, R.raw.kf_modifier, 1)
                delSoundId = sp.load(this, R.raw.kf_delete, 1)
            } else {
                popSoundId = sp.load(this, soundRes(soundType), 1)
                modSoundId = 0
                delSoundId = 0
            }
            soundPool = sp
            loadedSoundType = soundType
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        suggThread.quitSafely()
        touchCal?.save()
        unregisterBackCallback()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun feedback() {
        val kind = soundKind
        soundKind = SOUND_STANDARD
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
                    val id = when (kind) {
                        SOUND_DELETE -> delSoundId
                        SOUND_MODIFIER -> modSoundId
                        else -> 0
                    }.takeIf { it != 0 } ?: popSoundId
                    if (sp != null && id != 0) {
                        sp.play(id, soundVol, soundVol, 1, 0, 1f)
                    }
                } else {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.playSoundEffect(when (kind) {
                        SOUND_DELETE -> AudioManager.FX_KEYPRESS_DELETE
                        SOUND_MODIFIER -> AudioManager.FX_KEYPRESS_SPACEBAR
                        else -> AudioManager.FX_KEYPRESS_STANDARD
                    }, soundVol)
                }
            } catch (_: Exception) {
            }
        }
    }
}
