package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * On-device word store powering suggestions.
 *
 * Three sources, fully offline:
 *  - a bundled seed word list per language (assets/dict/<lang>.txt)
 *  - words learned from the user's typing ("dict_<lang>.txt")
 *  - imported word lists (merged into the learned file), one word per line
 *    or "word<TAB>count" — the same plain-text format MultiLing-style user
 *    dictionaries export to.
 *
 * Also learns bigrams (word pairs) for next-word prediction.
 */
class WordStore(private val context: Context, private val langCode: String) {

    private val learned = HashMap<String, Int>()
    private var seeds: List<String> = emptyList()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var loaded = false
    private var dirty = false

    private fun wordFile(): File = File(context.filesDir, "dict_$langCode.txt")
    private fun bigramFile(): File = File(context.filesDir, "bigram_$langCode.txt")

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val f = wordFile()
            if (f.exists()) {
                f.forEachLine { line -> parseWordLine(line) }
            }
        } catch (_: Exception) {
        }
        try {
            seeds = context.assets.open("dict/$langCode.txt")
                .bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            seeds = emptyList()
        }
        try {
            val f = bigramFile()
            if (f.exists()) {
                f.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size == 3) {
                        val c = parts[2].toIntOrNull() ?: 1
                        bigrams.getOrPut(parts[0]) { HashMap() }[parts[1]] = c
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun parseWordLine(line: String) {
        val idx = line.indexOf('\t')
        if (idx > 0) {
            val w = line.substring(0, idx).trim()
            val c = line.substring(idx + 1).trim().toIntOrNull() ?: 1
            if (w.isNotEmpty()) learned[w] = maxOf(learned[w] ?: 0, c)
        } else {
            val w = line.trim()
            if (w.isNotEmpty()) learned[w] = maxOf(learned[w] ?: 0, 1)
        }
    }

    fun learn(word: String) {
        if (word.length < 2 || word.length > 32) return
        ensureLoaded()
        learned[word] = (learned[word] ?: 0) + 1
        dirty = true
    }

    fun learnBigram(prev: String, next: String) {
        if (prev.length < 2 || next.length < 2) return
        ensureLoaded()
        val m = bigrams.getOrPut(prev) { HashMap() }
        m[next] = (m[next] ?: 0) + 1
        dirty = true
    }

    fun forget(word: String) {
        ensureLoaded()
        if (learned.remove(word) != null) dirty = true
    }

    fun clearLearned() {
        ensureLoaded()
        learned.clear()
        bigrams.clear()
        dirty = true
        save()
    }

    /**
     * Prefix completions: learned words first (by frequency), then seeds.
     * A word typed only once is NOT suggested yet — this keeps one-off
     * typos out of the suggestion strip; a word must repeat to qualify.
     */
    fun suggest(prefix: String, max: Int, useSeeds: Boolean): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<String>()
        learned.entries
            .asSequence()
            .filter { it.value >= 2 && it.key.startsWith(prefix) && it.key != prefix }
            .sortedByDescending { it.value }
            .take(max)
            .forEach { out.add(it.key) }
        if (useSeeds && out.size < max) {
            for (w in seeds) {
                if (out.size >= max) break
                if (w.startsWith(prefix) && w != prefix && !out.contains(w)) out.add(w)
            }
        }
        return out
    }

    /** Next-word prediction from learned bigrams (repeated pairs only). */
    fun suggestNext(prev: String, max: Int): List<String> {
        if (prev.isEmpty()) return emptyList()
        ensureLoaded()
        val m = bigrams[prev] ?: return emptyList()
        return m.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key }
    }

    /**
     * Merge an imported plain-text word list. Imported words get a count of
     * at least 2 so they are suggested immediately (unlike one-off typos).
     * Returns how many words were added.
     */
    fun importText(text: String): Int {
        ensureLoaded()
        val before = learned.size
        val imported = ArrayList<String>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.length > 48) continue
            val idx = t.indexOf('\t')
            val w = (if (idx > 0) t.substring(0, idx) else t).trim()
            if (w.isEmpty()) continue
            parseWordLine(t)
            imported.add(w)
        }
        for (w in imported) {
            val c = learned[w]
            if (c != null && c < 2) learned[w] = 2
        }
        dirty = true
        save()
        return learned.size - before
    }

    fun exportText(): String {
        ensureLoaded()
        val sb = StringBuilder()
        for ((w, c) in learned.entries.sortedByDescending { it.value }) {
            sb.append(w).append('\t').append(c).append('\n')
        }
        return sb.toString()
    }

    fun save() {
        if (!dirty) return
        dirty = false
        try {
            val sb = StringBuilder()
            // keep the store bounded: drop least-used words beyond 20000
            val entries = learned.entries.sortedByDescending { it.value }.take(20000)
            for ((w, c) in entries) sb.append(w).append('\t').append(c).append('\n')
            wordFile().writeText(sb.toString())
        } catch (_: Exception) {
        }
        try {
            val sb = StringBuilder()
            var n = 0
            outer@ for ((w1, m) in bigrams) {
                for ((w2, c) in m) {
                    sb.append(w1).append('\t').append(w2).append('\t').append(c).append('\n')
                    if (++n >= 20000) break@outer
                }
            }
            bigramFile().writeText(sb.toString())
        } catch (_: Exception) {
        }
    }
}
