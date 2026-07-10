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
            k("ش", "ښ"), k("س", "ۍ"), k("ی", "ي", "ې ئ ى"), k("ب", "پ"),
            k("ل", "أ"), k("ا", "آ", "أ إ ء"), k("ت", "ټ"), k("ن", "ڼ", "ں"),
            k("م", "ة"), k("ک", "ك"), k("ګ", "گ")
        ),
        listOf(
            shiftKey(), k("ظ", "ئ"), k("ط", "ې"), k("ز", "ژ"), k("ر", "ء"),
            k("ذ", "؟"), k("د", "ډ"), k("ړ", "ؤ"), k("و", "،", "ؤ"), k("ږ", "ے"), delKey()
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
            k("م", "»"), k("ک", "ك"), k("گ")
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
            k("م", "ۃ"), k("ک", "ك"), k("گ")
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

    /** Rich long-press set on the tatweel key: punctuation, diacritics and
     *  ZWNJ, with the comma and the Islamic phrase ligatures ﷲ ﷺ ﷻ ﷽ on
     *  the LAST row — the popup opens above the key, so the last items sit
     *  closest to the finger. (No digits here: the numpad covers those.) */
    private fun rtlExtraKey(@Suppress("UNUSED_PARAMETER") digits: String): KeyDef {
        val alts = listOf(
            "؛", "٪", "«", "»", "؟", "!",
            "ً", "ٌ", "ٍ", "َ", "ُ", "ِ",
            "ّ", "ْ", "ٔ", "ٰ", "zwnj", "ـ",
            "…", "،", "ﷲ", "ﷺ", "ﷻ", "﷽"
        )
        return KeyDef("ـ", null, alts)
    }

    /** Rich period-key long-press set (RTL). */
    private val rtlPeriodAlts = listOf(
        "،", "؟", "!", ":", "؛", "…", "\"", "'", "@", "&", "#",
        "(", ")", "-", "_", "+", "=", "/", "\\", "«", "»", "<", ">",
        "{", "}", "°", "❤"
    )

    val PASHTO = Language(
        "ps", "پښتو", psRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true,
        periodAlts = rtlPeriodAlts, extraKey = rtlExtraKey("۰۱۲۳۴۵۶۷۸۹")
    )
    val DARI = Language(
        "fa", "دري", faRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true,
        periodAlts = rtlPeriodAlts, extraKey = rtlExtraKey("۰۱۲۳۴۵۶۷۸۹")
    )
    val ARABIC = Language(
        "ar", "العربية", arRows, "٠١٢٣٤٥٦٧٨٩", rtl = true,
        periodAlts = rtlPeriodAlts, extraKey = rtlExtraKey("٠١٢٣٤٥٦٧٨٩")
    )
    val URDU = Language(
        "ur", "اردو", urRows, "۰۱۲۳۴۵۶۷۸۹", rtl = true,
        period = "۔", periodAlts = listOf(".") + rtlPeriodAlts,
        extraKey = rtlExtraKey("۰۱۲۳۴۵۶۷۸۹")
    )
    val ENGLISH = Language(
        "en", "English", enRows, "0123456789", rtl = false,
        period = ".",
        periodAlts = listOf(
            ",", "?", "!", ":", ";", "…", "\"", "'", "@", "&", "#",
            "(", ")", "-", "_", "+", "=", "/", "\\", "<", ">", "{", "}", "°", "❤"
        ),
        extraKey = KeyDef(
            ",", null, listOf(
                "date", "«", "—", "»", "time", "✗", "∴", "※", "∵", "✔",
                "@", "€", "§", "%", "*", "…", "$", "~", "★", "'", "\"", "-", "_"
            )
        )
    )

    val ALL = listOf(PASHTO, DARI, ARABIC, URDU, ENGLISH)

    fun byCode(code: String): Language = ALL.firstOrNull { it.code == code } ?: PASHTO

    // -------------------------------------------------------------- Symbols
    /** Symbols page 1. Digit row is localized per language, ordered 1…9,0. */
    /** Unicode forms of every digit for the long-press popup: Arabic-Indic,
     *  extended Arabic (Pashto/Farsi), superscript, subscript, circled,
     *  negative-circled, parenthesized, Roman, fullwidth and keycap. */
    private val DIGIT_VARIANTS: List<List<String>> = listOf(
        listOf("٠", "۰", "⁰", "₀", "⓪", "⓿", "０", "0️⃣"),
        listOf("١", "۱", "¹", "₁", "①", "❶", "⑴", "Ⅰ", "１", "1️⃣"),
        listOf("٢", "۲", "²", "₂", "②", "❷", "⑵", "Ⅱ", "２", "2️⃣"),
        listOf("٣", "۳", "³", "₃", "③", "❸", "⑶", "Ⅲ", "３", "3️⃣"),
        listOf("٤", "۴", "⁴", "₄", "④", "❹", "⑷", "Ⅳ", "４", "4️⃣"),
        listOf("٥", "۵", "⁵", "₅", "⑤", "❺", "⑸", "Ⅴ", "５", "5️⃣"),
        listOf("٦", "۶", "⁶", "₆", "⑥", "❻", "⑹", "Ⅵ", "６", "6️⃣"),
        listOf("٧", "۷", "⁷", "₇", "⑦", "❼", "⑺", "Ⅶ", "７", "7️⃣"),
        listOf("٨", "۸", "⁸", "₈", "⑧", "❽", "⑻", "Ⅷ", "８", "8️⃣"),
        listOf("٩", "۹", "⁹", "₉", "⑨", "❾", "⑼", "Ⅸ", "９", "9️⃣")
    )

    fun symbols1(lang: Language): List<List<KeyDef>> {
        val order = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        val digitRow = order.map { i ->
            val c = lang.digits[i].toString()
            val latin = ('0' + i).toString()
            val variants = DIGIT_VARIANTS[i].filter { it != c && it != latin }
            if (c == latin) KeyDef(latin, null, variants)
            else KeyDef(c, latin, listOf(latin) + variants)
        }
        return listOf(
            digitRow,
            listOf(
                k("@", null, "﹫"), k("#", null, "№ ♯"),
                k("$", "؋", "€ £ ¥ ¢ ₹ ₨ ₽ ₺ ₩ ¤ ₿"),
                k("_", null, "‾"), k("&", null, "§ ¶"),
                k("-", null, "– — ±"), k("+", null, "±"),
                k("(", null, "[ { ⟨ ‹"), k(")", null, "] } ⟩ ›"),
                k("/", null, "\\ | ÷")
            ),
            listOf(
                KeyDef("=\\<", code = Keys.SYM2, width = 1.4f),
                k("*", null, "† ‡ ★ ✱"), k("\"", null, "“ ” „ « »"),
                k("'", null, "‘ ’ ` ′"), k(":", null, "∶"), k(";"),
                k("!", null, "¡ ‼"),
                if (lang.rtl) KeyDef("؟", "?", listOf("¿", "⁇"))
                else KeyDef("?", "؟", listOf("¿", "⁇")), delKey()
            )
        )
    }

    /** Symbols page 2. */
    fun symbols2(): List<List<KeyDef>> = listOf(
        listOf(
            k("~", null, "≈ ≃"), k("`", null, "´ ˝"), k("|", null, "¦"),
            k("•", null, "· ◦ ▪ ●"), k("√", null, "∛ ∜"),
            k("π", null, "µ Ω ∞ ∑ ∫ φ"), k("÷", null, "∕"), k("×", null, "∙ ⋅"),
            k("¶", null, "§"), k("∆", null, "∇ ∂")
        ),
        listOf(
            k("£"), k("€"), k("¥", null, "₹ ₨ ₽ ₺ ₩ ¢ ¤ ₿ ؋"),
            k("^", null, "ˆ ↑"), k("°", null, "± ‰ ℃ ℉"),
            k("=", null, "≠ ≡ ≤ ≥"), k("{"), k("}"), k("\\", null, "‖"),
            k("%", null, "‰ ٪ ‱")
        ),
        listOf(
            KeyDef("۱۲۳", code = Keys.SYM, width = 1.4f),
            k("©"), k("®"), k("™", null, "℠"), k("✓", null, "✔ ✗ ✘ ☑ ☐"),
            k("[", null, "⟦"), k("]", null, "⟧"),
            k("«", null, "< ‹ ≪"), k("»", null, "> › ≫"), delKey()
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

    /** Number pad — always standard Latin digits. */
    fun numPad(lang: Language, enterLabel: String = "↵"): List<List<KeyDef>> {
        val d = "0123456789".map { it.toString() }
        return listOf(
            listOf(k(d[1]), k(d[2]), k(d[3]), k("÷", null, "/")),
            listOf(k(d[4]), k(d[5]), k(d[6]), k("×", null, "*")),
            listOf(k(d[7]), k(d[8]), k(d[9]), k("-", null, "_")),
            listOf(k("+"), k(d[0]), k("."), k("=", null, "%")),
            listOf(
                KeyDef("ابت", code = Keys.ABC, width = 1.5f),
                k(","), k(":"),
                KeyDef("⌫", code = Keys.DELETE, repeatable = true),
                KeyDef(enterLabel, code = Keys.ENTER, width = 1.5f)
            )
        )
    }

    /**
     * Characters a typo could plausibly stand for: the key's own shift/
     * alternate characters (missed long-press) plus physically adjacent
     * keys (fat-finger). Built from the layout itself.
     */
    fun confusionMap(lang: Language): Map<Char, Set<Char>> {
        val map = HashMap<Char, HashSet<Char>>()
        fun add(a: Char, b: Char) {
            if (a == b) return
            map.getOrPut(a) { HashSet() }.add(b)
            map.getOrPut(b) { HashSet() }.add(a)
        }
        val rows = lang.rows
        for ((ri, row) in rows.withIndex()) {
            val letters = row.filter { it.code == 0 && it.label.length == 1 }
            for ((li, key) in letters.withIndex()) {
                val c = key.label[0]
                key.shifted?.let { if (it.length == 1) add(c, it[0]) }
                for (alt in key.alternates) if (alt.length == 1) add(c, alt[0])
                letters.getOrNull(li - 1)?.let { add(c, it.label[0]) }
                letters.getOrNull(li + 1)?.let { add(c, it.label[0]) }
                for (nri in intArrayOf(ri - 1, ri + 1)) {
                    val nrow = rows.getOrNull(nri) ?: continue
                    val nletters = nrow.filter { it.code == 0 && it.label.length == 1 }
                    if (nletters.size < 2 || letters.size < 2) continue
                    val j = (li.toFloat() * (nletters.size - 1) / (letters.size - 1) + 0.5f).toInt()
                    nletters.getOrNull(j)?.let { add(c, it.label[0]) }
                }
            }
        }
        return map
    }

    /**
     * Items of the long-press-123 slide-to-select menu popup.
     * Text labels only — no emoji glyphs in the chrome.
     */
    fun menuItems(): List<Pair<String, Int>> = listOf(
        "کنټرول" to Keys.EDIT_PANEL,
        "شمېرې" to Keys.NUMPAD,
        "ایموجي" to Keys.EMOJI,
        "کلیپ بورډ" to Keys.CLIPBOARD,
        "غږ" to Keys.MIC,
        "ژبې" to Keys.LANGS,
        "تنظیمات" to Keys.SETTINGS
    )
}
