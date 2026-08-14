package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * AutoText / shorthand store: typing a shortcut followed by a separator
 * expands it to the full phrase (e.g. "برب" → "بیرته به راشم").
 *
 * Plain-text file, one "shortcut<TAB>expansion" per line — compatible with
 * exported MultiLing-style autotext lists, so users can import them.
 */
class AutoTextStore(private val context: Context) {

    companion object {
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
    }

    /** A shortcut may carry SEVERAL phrases — all offered on the strip. */
    private val map = LinkedHashMap<String, ArrayList<String>>()
    private var loaded = false
    private var dirty = false

    private fun file(): File = File(context.filesDir, "autotext.txt")

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
        for ((s, list) in map) {
            if (out.size >= max) break
            if (s != prefix && s.startsWith(prefix)) {
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
