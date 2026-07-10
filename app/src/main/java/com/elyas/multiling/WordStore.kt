package com.elyas.multiling

import android.content.Context
import org.tukaani.xz.XZInputStream
import java.io.File
import kotlin.math.ln

/**
 * On-device word store powering suggestions.
 *
 * Three sources, fully offline:
 *  - a bundled frequency dictionary per language (assets/dict/<lang>.txt.xz,
 *    LZMA2-compressed; lines are "word<TAB>frequency" sorted by frequency,
 *    highest first) built from the Leipzig news corpora
 *  - words learned from the user's typing ("dict_<lang>.txt")
 *  - imported word lists (merged into the learned file), one word per line
 *    or "word<TAB>count" — the same plain-text format MultiLing-style user
 *    dictionaries export to.
 *
 * Also learns bigrams (word pairs) for next-word prediction.
 */
class WordStore(private val context: Context, private val langCode: String) {

    /** A scored suggestion candidate. */
    data class Cand(val word: String, val score: Int, val exact: Boolean, val sameLen: Boolean)

    private val learned = HashMap<String, Int>()
    private var seeds: List<String> = emptyList()   // frequency order, high → low
    private var seedFreq: IntArray = IntArray(0)    // parallel corpus frequencies
    private var seedSet: HashSet<String> = HashSet()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var loaded = false
    private var dirty = false

    private fun wordFile(): File = File(context.filesDir, "dict_$langCode.txt")
    private fun bigramFile(): File = File(context.filesDir, "bigram_$langCode.txt")

    /** Parse everything up front (call from a background thread) so the
     *  first keystroke doesn't pay the decompression cost. */
    fun preload() {
        ensureLoaded()
    }

    @Synchronized
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
            val stream = try {
                XZInputStream(context.assets.open("dict/$langCode.txt.xz"))
            } catch (_: Exception) {
                context.assets.open("dict/$langCode.txt")
            }
            val ws = ArrayList<String>(70000)
            val fs = ArrayList<Int>(70000)
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val idx = line.indexOf('\t')
                    if (idx > 0) {
                        val w = line.substring(0, idx)
                        ws.add(w)
                        fs.add(line.substring(idx + 1).toIntOrNull() ?: 1)
                    } else {
                        val w = line.trim()
                        if (w.isNotEmpty()) {
                            ws.add(w)
                            fs.add(1)
                        }
                    }
                }
            }
            seeds = ws
            seedFreq = fs.toIntArray()
            seedSet = HashSet(ws)
        } catch (_: Exception) {
            seeds = emptyList()
            seedFreq = IntArray(0)
            seedSet = HashSet()
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
     * Prefix completions: learned words first (by frequency), then seeds —
     * which are already ordered by corpus frequency, so the most common
     * words of the language come first.
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

    /** True when the word is an established dictionary word. */
    fun contains(word: String): Boolean {
        ensureLoaded()
        return (learned[word] ?: 0) >= 2 || seedSet.contains(word)
    }

    /**
     * Smart prefix suggestions with a layout-aware error model: a typed
     * character also matches its shift/long-press character (missed
     * long-press) and physically adjacent keys (fat-finger). E.g. typing
     * "چط" still finds "چې", and "هفه" finds "هغه".
     */
    fun suggestSmart(
        typed: String,
        max: Int,
        useSeeds: Boolean,
        confusable: Map<Char, Set<Char>>
    ): List<Cand> {
        if (typed.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<Cand>()
        for ((w, c) in learned) {
            if (c < 2) continue
            // the user's own words count as if used 100× more than corpus words
            score(typed, w, freqScore(c * 100), confusable)?.let { out.add(it) }
        }
        if (useSeeds) {
            for (i in seeds.indices) {
                score(typed, seeds[i], freqScore(seedFreq[i]), confusable)?.let { out.add(it) }
            }
        }
        return out.asSequence()
            .sortedByDescending { it.score }
            .distinctBy { it.word }
            .take(max)
            .toList()
    }

    /**
     * Corpus frequencies span 1 … ~1,000,000, so they are folded onto a log
     * scale (≈ 0–550). One log-unit step (~40 points) weighs the same as one
     * extra character of completion length, keeping very common words ahead
     * without ever outranking an exact match (+10000) with an error match.
     */
    private fun freqScore(c: Int): Int = (ln(1.0 + c) * 40.0).toInt()

    private fun score(
        typed: String,
        w: String,
        freq: Int,
        confusable: Map<Char, Set<Char>>
    ): Cand? {
        if (w == typed) return null
        if (w.length < typed.length || w.length > typed.length + 12) return null
        val maxErr = if (typed.length <= 3) 1 else 2
        var errors = 0
        for (i in typed.indices) {
            val a = typed[i]
            val b = w[i]
            if (a == b) continue
            if (confusable[a]?.contains(b) == true) {
                errors++
                if (errors > maxErr) return null
            } else {
                return null
            }
        }
        val exact = errors == 0
        var s = freq
        s += if (exact) 10000 else 5000 - errors * 1500
        s -= (w.length - typed.length) * 40
        return Cand(w, s, exact, w.length == typed.length)
    }

    /**
     * Next-word prediction: learned bigrams first (repeated pairs only),
     * then the most frequent words of the language fill the empty slots.
     */
    fun suggestNext(prev: String, max: Int, useSeeds: Boolean): List<String> {
        if (prev.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<String>()
        bigrams[prev]?.entries
            ?.filter { it.value >= 2 }
            ?.sortedByDescending { it.value }
            ?.take(max)
            ?.forEach { out.add(it.key) }
        if (useSeeds && out.size < max) {
            for (w in seeds) {
                if (out.size >= max) break
                if (w != prev && !out.contains(w)) out.add(w)
            }
        }
        return out
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
