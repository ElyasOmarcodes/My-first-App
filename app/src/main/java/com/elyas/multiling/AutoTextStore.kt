package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * AutoText / shorthand store for one keyboard language: typing a shortcut
 * offers the full phrase on the suggestion strip (e.g. "سلام" → "السلام
 * علیکم ورحمة الله وبرکاته!"). Each language keeps its own list, so the
 * Dari keyboard offers Dari phrases and the English one English phrases.
 *
 * Plain-text file, one "shortcut<TAB>expansion" per line — compatible with
 * exported MultiLing-style autotext lists, so users can import them.
 */
class AutoTextStore(private val context: Context, val lang: String) {

    companion object {
        /** Languages with their own list, in the order the app shows them. */
        val LANGS = listOf("ps", "fa", "ar", "ur", "en")

        /** The language last picked on the Shortcuts page (import/export use it). */
        fun selectedLang(context: Context): String =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString("autotext_lang", null)?.takeIf { it in LANGS } ?: "ps"

        fun selected(context: Context): AutoTextStore = AutoTextStore(context, selectedLang(context))

        /** Shortcuts across every language. */
        fun totalCount(context: Context): Int = LANGS.sumOf { AutoTextStore(context, it).all().size }

        /**
         * The keyboard service caches dictionaries and autotext in memory.
         * Editors (autotext screen, imports) bump this counter so the
         * service knows to reload from disk.
         */
        fun bumpDataVersion(context: Context) {
            val p = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context)
            p.edit().putInt("data_version", p.getInt("data_version", 0) + 1).apply()
        }

        /**
         * First run (of the app OR the keyboard, whichever comes first):
         * give every language its bundled default shortcuts, so the
         * Shortcuts page is never empty on a fresh install. Lists made before
         * shortcuts were per language were Pashto lists: they become the
         * Pashto one, and the other languages get their defaults once.
         */
        fun bootstrapDefaults(context: Context) {
            val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            try {
                val legacy = File(context.filesDir, "autotext.txt")
                val ps = File(context.filesDir, "autotext_ps.txt")
                if (legacy.exists() && !ps.exists()) legacy.renameTo(ps)
            } catch (_: Exception) {
            }
            val e = p.edit()
            for (lang in LANGS) {
                val key = "autotext_init_$lang"
                // the old single flag meant the Pashto defaults were already given
                val done = p.getBoolean(key, false) ||
                    (lang == "ps" && p.getBoolean("autotext_init", false))
                if (done) continue
                e.putBoolean(key, true)
                try {
                    val store = AutoTextStore(context, lang)
                    if (store.all().isEmpty()) store.restoreDefaults()
                } catch (_: Exception) {
                }
            }
            e.apply()
        }
    }

    /** Add this language's bundled shortcuts (existing ones are kept). */
    fun restoreDefaults(): Int = try {
        importText(context.assets.open("default_autotext_$lang.txt").bufferedReader().use { it.readText() })
    } catch (_: Exception) { 0 }

    /** A shortcut may carry SEVERAL phrases — all offered on the strip. */
    private val map = LinkedHashMap<String, ArrayList<String>>()
    private var loaded = false
    private var dirty = false

    private fun file(): File = File(context.filesDir, "autotext_$lang.txt")

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val f = file()
            if (f.exists()) {
                f.forEachLine { line -> parseLine(line) }
            }
        } catch (_: Exception) {
        }
    }

    private fun parseLine(line: String) {
        val idx = line.indexOf('\t')
        if (idx > 0 && idx < line.length - 1) {
            val short = line.substring(0, idx).trim()
            val full = unescape(line.substring(idx + 1).trim())
            if (short.isNotEmpty() && full.isNotEmpty()) {
                val list = map.getOrPut(short) { ArrayList() }
                if (!list.contains(full)) list.add(full)
            }
        }
    }

    // multi-line expansions live in a line-based file: newlines are stored
    // as the two characters \n (and a literal backslash as \\)
    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun expansionFor(shortcut: String): String? {
        ensureLoaded()
        return map[shortcut]?.firstOrNull()
    }

    /** Expansions whose shortcut starts with [prefix] — for the suggestion
     *  strip. Exact-shortcut phrases come first, then prefix matches. */
    fun matching(prefix: String, max: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<String>()
        map[prefix]?.let { out.addAll(it) }
        // "Salam" finds "salam": capitals come from sentence starts
        val lower = prefix.lowercase()
        if (lower != prefix) map[lower]?.let { for (e in it) if (e !in out) out.add(e) }
        for ((s, list) in map) {
            if (out.size >= max) break
            if (s != prefix && s != lower && (s.startsWith(prefix) || s.startsWith(lower))) {
                for (e in list) {
                    out.add(e)
                    if (out.size >= max) break
                }
            }
        }
        return out.take(max)
    }

    /** Adds one more phrase to the shortcut (nothing is overwritten). */
    fun put(shortcut: String, expansion: String) {
        ensureLoaded()
        val list = map.getOrPut(shortcut) { ArrayList() }
        if (!list.contains(expansion)) {
            list.add(expansion)
            dirty = true
            save()
        }
    }

    /** Remove one phrase; with [expansion] null, the whole shortcut goes. */
    fun remove(shortcut: String, expansion: String? = null) {
        ensureLoaded()
        val list = map[shortcut] ?: return
        val changed = if (expansion == null) {
            map.remove(shortcut) != null
        } else {
            val r = list.remove(expansion)
            if (list.isEmpty()) map.remove(shortcut)
            r
        }
        if (changed) {
            dirty = true
            save()
        }
    }

    fun all(): List<Pair<String, String>> {
        ensureLoaded()
        val out = ArrayList<Pair<String, String>>()
        for ((s, list) in map) for (e in list) out.add(s to e)
        return out
    }

    fun importText(text: String): Int {
        ensureLoaded()
        val before = map.values.sumOf { it.size }
        for (line in text.lineSequence()) parseLine(line)
        dirty = true
        save()
        return map.values.sumOf { it.size } - before
    }

    fun exportText(): String {
        ensureLoaded()
        val sb = StringBuilder()
        for ((s, list) in map) {
            for (e in list) sb.append(s).append('\t').append(escape(e)).append('\n')
        }
        return sb.toString()
    }

    fun save() {
        if (!dirty) return
        dirty = false
        // snapshot now, write on the background thread — saves must never
        // stall the keyboard
        val data = exportText()
        Io.writer.execute {
            try {
                file().writeText(data)
            } catch (_: Exception) {
            }
        }
    }
}
