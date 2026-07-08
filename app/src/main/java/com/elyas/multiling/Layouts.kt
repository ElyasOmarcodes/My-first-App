package com.elyas.multiling

/**
 * Keyboard layouts for every supported language, modeled on classic
 * multilingual phone keyboards: the small hint character on each key is
 * typed with Shift, and long-press shows it plus extra alternates.
 */
object Layouts {

    private fun k(label: String, shifted: String? = null, alts: String = "", w: Float = 1f) =
        KeyDef(label, shifted, if (alts.isEmpty()) emptyList() else alts.split(" "), width = w)

    private fun shiftKey(w: Float = 1.4f) = KeyDef("⇧", code = Keys.SHIFT, width = w)
    private fun delKey(w: Float = 1.4f) = KeyDef("⌫", code = Keys.DELETE, width = w, repeatable = true)

    // ---------------------------------------------------------------- Pashto
    private val psRows = listOf(
        listOf(
            k("ض", "ً"), k("ص", "ٌ"), k("ث", "ٍ"), k("ق", "َ"), k("ف", "ُ"),
            k("غ", "ِ"), k("ع", "ّ"), k("ه", "ْ", "ة ۀ ہ"), k("خ", "ځ"),
            k("ح", "څ"), k("ج", "]", "}"), k("چ", "[", "{")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ۍ"), k("ی", "ي", "ې ئ ے ى"), k("ب", "پ"),
            k("ل", "أ"), k("ا", "آ", "أ إ ء"), k("ت", "ټ"), k("ن", "ڼ", "ں"),
            k("م", "ة"), k("ک", "ك"), k("ګ", "گ")
        ),
        listOf(
            shiftKey(), k("ظ", "ئ"), k("ط", "ې"), k("ز", "ژ"), k("ر", "ء"),
            k("ذ", "؟"), k("د", "ډ"), k("ړ", "ڑ"), k("و", "ؤ"), k("ږ", "ے"), delKey()
        )
    )

    // ------------------------------------------------------------ Dari/Farsi
    private val faRows = listOf(
        listOf(
            k("ض", "ً"), k("ص", "ٌ"), k("ث", "ٍ"), k("ق", "َ"), k("ف", "ُ"),
            k("غ", "ِ"), k("ع", "ّ"), k("ه", "ة", "ۀ ه‌"), k("خ", "ْ"),
            k("ح", "ٔ"), k("ج", "]", "}"), k("چ", "[", "{")
        ),
        listOf(
            k("ش", "ؤ"), k("س", "ئ"), k("ی", "ي", "ې ى ے"), k("ب", "پ"),
            k("ل", "أ"), k("ا", "آ", "أ إ ء"), k("ت", "ة"), k("ن", "«"),
            k("م", "»"), k("ک", "ك"), k("گ", "ڭ")
        ),
        listOf(
            shiftKey(), k("ظ", "ئ"), k("ط", "ي"), k("ز", "ژ"), k("ر", "ٰ"),
            k("ذ", "ء"), k("د", "ٔ"), k("پ", "؟"), k("و", "ؤ"), k("ژ", "ے"), delKey()
        )
    )

    // ---------------------------------------------------------------- Arabic
    private val arRows = listOf(
        listOf(
            k("ض", "ً"), k("ص", "ٌ"), k("ث", "ٍ"), k("ق", "َ"), k("ف", "ُ"),
            k("غ", "ِ"), k("ع", "ّ"), k("ه", "ْ", "ة"), k("خ", "ٔ"),
            k("ح", "ٰ"), k("ج", "]", "}")
        ),
        listOf(
            k("ش", "ؤ"), k("س", "ئ"), k("ي", "ى", "ئ"), k("ب", "لا"),
            k("ل", "أ"), k("ا", "آ", "أ إ ء"), k("ت", "ة"), k("ن", "«"),
            k("م", "»"), k("ك", "؛"), k("ة", "ۃ")
        ),
        listOf(
            shiftKey(), k("ظ", "ّ"), k("ط", "َ"), k("ذ", "ِ"), k("د", "ُ"),
            k("ز", "ً"), k("ر", "ٍ"), k("و", "ؤ"), k("ى", "ئ"), k("ء", "أ", "إ آ ؤ ئ"), delKey()
        )
    )

    // ------------------------------------------------------------------ Urdu
    private val urRows = listOf(
        listOf(
            k("ض", "ً"), k("ص", "ٌ"), k("ث", "ٍ"), k("ق", "َ"), k("ف", "ُ"),
            k("غ", "ِ"), k("ع", "ّ"), k("ہ", "ھ", "ۃ ه"), k("خ", "ْ"),
            k("ح", "ٔ"), k("ج", "]", "}"), k("چ", "[", "{")
        ),
        listOf(
            k("ش", "ؤ"), k("س", "ئ"), k("ی", "ے", "ي ئ ى"), k("ب", "پ"),
            k("ل", "أ"), k("ا", "آ", "أ إ ء"), k("ت", "ٹ"), k("ن", "ں"),
            k("م", "ۃ"), k("ک", "ك"), k("گ", "ڭ")
        ),
        listOf(
            shiftKey(), k("ظ", "ئ"), k("ط", "ي"), k("ز", "ژ"), k("ر", "ڑ"),
            k("ذ", "؟"), k("د", "ڈ"), k("ٹ", "ث"), k("و", "ؤ"), k("ے", "ء"), delKey()
        )
    )

    // --------------------------------------------------------------- English
    private val enRows = listOf(
        listOf(
            k("q", null, "1", ), k("w", null, "2"), k("e", null, "3 è é ê ë"),
            k("r", null, "4"), k("t", null, "5"), k("y", null, "6"),
            k("u", null, "7 ù ú û ü"), k("i", null, "8 ì í î ï"),
            k("o", null, "9 ò ó ô ö"), k("p", null, "0")
        ),
        listOf(
            k("a", null, "@ à á â ä"), k("s", null, "# ß"), k("d", null, "$"),
            k("f", null, "%"), k("g", null, "&"), k("h", null, "-"),
            k("j", null, "+"), k("k", null, "("), k("l", null, ")")
        ),
        listOf(
            shiftKey(), k("z", null, "*"), k("x", null, "\""), k("c", null, "' ç"),
            k("v", null, ":"), k("b", null, ";"), k("n", null, "! ñ"),
            k("m", null, "?"), delKey()
        )
    )

    val PASHTO = Language("ps", "پښتو", psRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true)
    val DARI = Language("fa", "دري", faRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true)
    val ARABIC = Language("ar", "العربية", arRows, "٠١٢٣٤٥٦٧٨٩", rtl = true)
    val URDU = Language(
        "ur", "اردو", urRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true,
        period = "۔", periodAlts = listOf("،", "؟", "!", ":", "؛", ".")
    )
    val ENGLISH = Language(
        "en", "English", enRows, "0123456789", rtl = false,
        period = ".", periodAlts = listOf(",", "?", "!", ":", ";", "…"),
        extraKey = KeyDef(",", null, listOf("'", "\"", "-", "_"))
    )

    val ALL = listOf(PASHTO, DARI, ARABIC, URDU, ENGLISH)

    fun byCode(code: String): Language = ALL.firstOrNull { it.code == code } ?: PASHTO

    // -------------------------------------------------------------- Symbols
    /** Symbols page 1. Digit row is localized per language, ordered 1…9,0. */
    fun symbols1(lang: Language): List<List<KeyDef>> {
        val order = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        val digitRow = order.map { i ->
            val c = lang.digits[i].toString()
            val latin = ('0' + i).toString()
            if (c == latin) k(latin)
            else KeyDef(c, latin, listOf(latin))
        }
        return listOf(
            digitRow,
            listOf(
                k("@"), k("#"), k("؋", "$", "€ £ ¥ ¢"), k("_"), k("&"),
                k("-"), k("+"), k("("), k(")"), k("/")
            ),
            listOf(
                KeyDef("=\\<", code = Keys.SYM2, width = 1.4f),
                k("*"), k("\""), k("'"), k(":"), k(";"), k("!", null, "¡"),
                k("?", null, "؟ ¿"), delKey()
            )
        )
    }

    /** Symbols page 2. */
    fun symbols2(): List<List<KeyDef>> = listOf(
        listOf(k("~"), k("`"), k("|"), k("•"), k("√"), k("π"), k("÷"), k("×"), k("¶"), k("∆")),
        listOf(k("£"), k("€"), k("¥"), k("^"), k("°"), k("="), k("{"), k("}"), k("\\"), k("%")),
        listOf(
            KeyDef("۱۲۳", code = Keys.SYM, width = 1.4f),
            k("©"), k("®"), k("™"), k("✓"), k("["), k("]"), k("«"), k("»"), delKey()
        )
    )

    /**
     * Edit/control panel, like classic multilingual keyboards:
     * Esc Paste ▲ Copy Del / Tab ◀ All ▶ Cut / Sel Home ▼ End ⌫
     */
    fun editPanel(): List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("Esc", code = Keys.ESC),
            KeyDef("Paste", code = Keys.PASTE),
            KeyDef("▲", code = Keys.ARROW_UP, repeatable = true),
            KeyDef("Copy", code = Keys.COPY),
            KeyDef("Del.", code = Keys.FWD_DEL, repeatable = true)
        ),
        listOf(
            KeyDef("Tab ⇥", code = Keys.TAB),
            KeyDef("◀", code = Keys.ARROW_LEFT, repeatable = true),
            KeyDef("All", code = Keys.SELECT_ALL),
            KeyDef("▶", code = Keys.ARROW_RIGHT, repeatable = true),
            KeyDef("Cut", code = Keys.CUT)
        ),
        listOf(
            KeyDef("⇧", code = Keys.SHIFT),
            KeyDef("Home", code = Keys.HOME),
            KeyDef("▼", code = Keys.ARROW_DOWN, repeatable = true),
            KeyDef("End", code = Keys.END),
            KeyDef("⌫", code = Keys.DELETE, repeatable = true)
        )
    )

    /** Number pad with localized digits. */
    fun numPad(lang: Language): List<List<KeyDef>> {
        val d = lang.digits.map { it.toString() }
        return listOf(
            listOf(k(d[1]), k(d[2]), k(d[3]), k("÷", null, "/")),
            listOf(k(d[4]), k(d[5]), k(d[6]), k("×", null, "*")),
            listOf(k(d[7]), k(d[8]), k(d[9]), k("-", null, "_")),
            listOf(k("+"), k(d[0]), k("."), k("=", null, "%")),
            listOf(
                KeyDef("ابت", code = Keys.ABC, width = 1.5f),
                k(","), k(":"),
                KeyDef("⌫", code = Keys.DELETE, repeatable = true),
                KeyDef("↵", code = Keys.ENTER, width = 1.5f)
            )
        )
    }

    /** Emoji / smiley page. */
    fun emojiPage(): List<List<KeyDef>> = listOf(
        listOf(k("😀"), k("😂"), k("🤣"), k("😊"), k("😍"), k("🥰"), k("😘"), k("😉")),
        listOf(k("🙏"), k("👍"), k("👏"), k("🤲"), k("💪"), k("🤝"), k("✌️"), k("👌")),
        listOf(k("❤️"), k("💔"), k("🌹"), k("🌸"), k("⭐"), k("🔥"), k("💯"), k("✅")),
        listOf(k("😢"), k("😭"), k("😡"), k("🤔"), k("😴"), k("😎"), k("🎉"), k("🎊")),
        listOf(
            KeyDef("ابت", code = Keys.ABC, width = 1.5f),
            k(":-)"), k(";-)"), k("<3"),
            KeyDef("⌫", code = Keys.DELETE, repeatable = true),
            KeyDef("↵", code = Keys.ENTER, width = 1.5f)
        )
    )

    /**
     * Items of the long-press-123 slide-to-select menu popup.
     * Text labels only — no emoji glyphs in the chrome.
     */
    fun menuItems(): List<Pair<String, Int>> = listOf(
        "کنټرول" to Keys.EDIT_PANEL,
        "شمېرې" to Keys.NUMPAD,
        "ایموجي" to Keys.EMOJI,
        "غږ" to Keys.MIC,
        "ژبې" to Keys.LANGS,
        "تنظیمات" to Keys.SETTINGS
    )
}
