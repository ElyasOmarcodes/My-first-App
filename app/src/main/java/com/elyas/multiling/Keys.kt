package com.elyas.multiling

/**
 * A single key definition. For normal character keys [code] is 0 and [label]
 * is committed as text. Special keys use negative [code] values from [Keys].
 */
data class KeyDef(
    val label: String,
    val shifted: String? = null,
    val alternates: List<String> = emptyList(),
    val code: Int = 0,
    val width: Float = 1f,
    val hint: String? = null,
    val repeatable: Boolean = false
) {
    /** All long-press candidates: shifted char first, then extra alternates. */
    fun popupChars(shiftActive: Boolean): List<String> {
        val list = ArrayList<String>()
        if (shiftActive) {
            if (code == 0) list.add(label)
        } else {
            shifted?.let { list.add(it) }
        }
        list.addAll(alternates)
        return list.distinct()
    }
}

object Keys {
    const val SHIFT = -1
    const val DELETE = -2
    const val SYM = -3       // go to symbols page 1
    const val ABC = -4       // back to letters
    const val SYM2 = -5      // symbols page 2
    const val ENTER = -6
    const val SPACE = -7
    const val MIC = -8
    const val ARROW_UP = -10
    const val ARROW_DOWN = -11
    const val ARROW_LEFT = -12
    const val ARROW_RIGHT = -13
}

/** One language with its letter rows. Rows are defined visually left-to-right. */
data class Language(
    val code: String,
    val nativeName: String,
    val rows: List<List<KeyDef>>,
    val digits: String,
    val rtl: Boolean,
    /** period key label and its long-press alternates */
    val period: String = ".",
    val periodAlts: List<String> = listOf("،", "؟", "!", ":", "؛", "…"),
    /** the key shown between 123 and space */
    val extraKey: KeyDef = KeyDef("ـ", null, listOf("ٓ", "ٰ", "ٔ"))
)
