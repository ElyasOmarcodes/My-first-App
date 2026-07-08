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

    private val map = LinkedHashMap<String, String>()
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
            val full = line.substring(idx + 1).trim()
            if (short.isNotEmpty() && full.isNotEmpty()) map[short] = full
        }
    }

    fun expansionFor(shortcut: String): String? {
        ensureLoaded()
        return map[shortcut]
    }

    /** Expansions whose shortcut starts with [prefix] — for the suggestion strip. */
    fun matching(prefix: String, max: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<String>()
        for ((s, e) in map) {
            if (s.startsWith(prefix)) {
                out.add(e)
                if (out.size >= max) break
            }
        }
        return out
    }

    fun put(shortcut: String, expansion: String) {
        ensureLoaded()
        map[shortcut] = expansion
        dirty = true
        save()
    }

    fun remove(shortcut: String) {
        ensureLoaded()
        if (map.remove(shortcut) != null) {
            dirty = true
            save()
        }
    }

    fun all(): List<Pair<String, String>> {
        ensureLoaded()
        return map.entries.map { it.key to it.value }
    }

    fun importText(text: String): Int {
        ensureLoaded()
        val before = map.size
        for (line in text.lineSequence()) parseLine(line)
        dirty = true
        save()
        return map.size - before
    }

    fun exportText(): String {
        ensureLoaded()
        val sb = StringBuilder()
        for ((s, e) in map) sb.append(s).append('\t').append(e).append('\n')
        return sb.toString()
    }

    fun save() {
        if (!dirty) return
        dirty = false
        try {
            file().writeText(exportText())
        } catch (_: Exception) {
        }
    }
}
