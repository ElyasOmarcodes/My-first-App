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

    companion object {
        // ---- edit costs, in units where 1.0 is "one whole wrong character"
        /** Substituting a character the spatial model does not link at all. */
        private const val MISMATCH = 1.0f
        /** A substitution known only from the static layout neighbour map. */
        private const val FALLBACK_SUB = 0.55f
        /** The user typed a character the word does not have. */
        private const val INS_COST = 0.9f
        /** The user missed a character the word has. */
        private const val DEL_COST = 0.9f
        /** Two adjacent characters swapped — common and cheap. */
        private const val TRANSPOSE_COST = 0.7f

        /** Unreachable cell — larger than any budget, safe to add to. */
        private const val INF = 1e6f

        // ---- how much evidence before a typed word counts as a real word
        /** Seen this often, it may be offered but is never used to correct. */
        const val LEARN_PROBATION = 3
        /** Seen this often on separate occasions, it is a dictionary word. */
        const val LEARN_CONFIRMED = 6
        /** An explicit "add to dictionary" jumps straight to this. */
        const val LEARN_EXPLICIT = 50

        /** Only words at least this common count as typo neighbours. */
        private const val TYPO_NEIGHBOUR_FREQ = 200
    }

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

    /**
     * Record that the user typed this word.
     *
     * Counting repetitions is NOT enough on its own. Over a month of real
     * use, a habitual mistyping gets repeated far more than twice, so a
     * simple threshold — at any value — eventually swallows it, and once a
     * misspelling is in the dictionary it stops being correctable and starts
     * being suggested. Raising the bar only delays that.
     *
     * So a word also has to look like a word the user MEANT:
     *
     *  * it must not be a near-miss of a real dictionary word. If one slip
     *    away from a common word explains it, it is a typo, not vocabulary —
     *    this is the rule that keeps "بیولوړي" out while letting a genuinely
     *    new name in.
     *  * it must survive. [unlearnRecent] is called when the user deletes
     *    what they just typed, which withdraws the evidence.
     *  * it is only trusted for correcting others once it reaches
     *    [LEARN_CONFIRMED]; below that it can be offered but never used to
     *    overrule what was typed.
     *
     * An explicit "add to dictionary" bypasses all of it — see [learnExplicit].
     */
    fun learn(word: String) {
        if (word.length < 2 || word.length > 32) return
        ensureLoaded()
        val cur = learned[word] ?: 0
        // already trusted, or the user added it by hand: just keep counting
        if (cur >= LEARN_CONFIRMED) {
            learned[word] = cur + 1
            dirty = true
            return
        }
        if (seedSet.contains(word)) return          // already a real word
        if (looksLikeTypo(word)) return
        learned[word] = cur + 1
        dirty = true
    }

    /** The user asked for this word by name; trust it immediately. */
    fun learnExplicit(word: String) {
        if (word.isEmpty() || word.length > 32) return
        ensureLoaded()
        learned[word] = maxOf(learned[word] ?: 0, LEARN_EXPLICIT)
        dirty = true
        save()
    }

    /**
     * Withdraw evidence for a word the user typed and then removed. Deleting
     * what you just wrote is the clearest signal available that it was wrong.
     */
    fun unlearnRecent(word: String) {
        ensureLoaded()
        val c = learned[word] ?: return
        if (c >= LEARN_EXPLICIT) return             // hand-added, leave alone
        if (c <= 1) learned.remove(word) else learned[word] = c - 2
        dirty = true
    }

    /**
     * True when a single slip explains the word as a common dictionary word.
     * Deliberately strict — one substitution, insertion, deletion or swap
     * against a word of real corpus frequency.
     */
    fun looksLikeTypo(word: String): Boolean {
        ensureLoaded()
        if (word.length < 3) return false
        val lw = word.lowercase()
        // only lengths within one can be within one edit, so the index keeps
        // this to a few hundred comparisons instead of the whole dictionary
        val byLen = commonByLength()
        for (len in lw.length - 1..lw.length + 1) {
            val bucket = byLen[len] ?: continue
            for (cand in bucket) if (withinOneEdit(lw, cand)) return true
        }
        return false
    }

    /** Common seed words bucketed by length, built once on first use. */
    private var commonIndex: Map<Int, List<String>>? = null

    @Synchronized
    private fun commonByLength(): Map<Int, List<String>> {
        commonIndex?.let { return it }
        val m = HashMap<Int, ArrayList<String>>()
        for (i in seeds.indices) {
            if (seedFreq[i] < TYPO_NEIGHBOUR_FREQ) continue
            val w = seeds[i]
            if (w.length < 3) continue
            m.getOrPut(w.length) { ArrayList() }.add(w)
        }
        val built: Map<Int, List<String>> = m
        commonIndex = built
        return built
    }

    // ------------------------------------------------- dictionary cleanup
    /**
     * Learned words that are one slip away from a common dictionary word.
     * These are almost certainly typos that the old count-only rule let in,
     * and the settings screen offers to remove them in bulk.
     */
    fun suspiciousLearned(): List<String> {
        ensureLoaded()
        return learned.keys
            .filter { (learned[it] ?: 0) < LEARN_EXPLICIT && looksLikeTypo(it) }
            .sorted()
    }

    /** All learned words with their counts, most used first. */
    fun learnedWords(): List<Pair<String, Int>> {
        ensureLoaded()
        return learned.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun forgetAll(words: Collection<String>) {
        ensureLoaded()
        var any = false
        for (w in words) if (learned.remove(w) != null) any = true
        if (any) { dirty = true; save() }
    }

    /** Cheap "is the edit distance at most 1" test. */
    private fun withinOneEdit(a: String, b: String): Boolean {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        if (a == b) return false
        if (la == lb) {
            var diff = -1
            for (i in 0 until la) {
                if (a[i] != b[i]) {
                    if (diff >= 0) {
                        // one swap of adjacent letters also counts as one slip
                        return diff == i - 1 && a[diff] == b[i] && a[i] == b[diff] &&
                            a.regionMatches(i + 1, b, i + 1, la - i - 1)
                    }
                    diff = i
                }
            }
            return diff >= 0
        }
        // lengths differ by one: the longer must contain the shorter in order
        val shorter = if (la < lb) a else b
        val longer = if (la < lb) b else a
        var i = 0
        var j = 0
        var skipped = false
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) { i++; j++ } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true
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
            .filter { it.value >= LEARN_PROBATION && it.key.startsWith(prefix) && it.key != prefix }
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

    /** True when the word is an established dictionary word (any case). */
    fun contains(word: String): Boolean {
        ensureLoaded()
        if ((learned[word] ?: 0) >= LEARN_CONFIRMED || seedSet.contains(word)) return true
        val lc = word.lowercase()
        return lc != word && ((learned[lc] ?: 0) >= LEARN_CONFIRMED || seedSet.contains(lc))
    }

    /**
     * Layout-aware correction and completion.
     *
     * The old matcher compared typed[i] against word[i] and allowed only
     * substitutions from a fixed neighbour set. That alignment is why a
     * single missing or extra letter broke it completely: every character
     * after the slip lined up against the wrong position, so "بیولوژي" was
     * only ever found from a same-length near-miss.
     *
     * This is the standard arrangement instead — a weighted edit distance
     * (substitute / insert / delete / transpose) scored against a spatial
     * model, then combined with word frequency, which is how Gboard and
     * Samsung rank candidates. Costs come from where the finger ACTUALLY
     * landed when we have it ([taps]), so a letter next to the intended key
     * is cheap and one across the keyboard is not.
     *
     * @param taps per-typed-character alternatives with costs, from
     *   [SpatialModel.alternativesFor]; may be shorter than [typed] (or
     *   empty), in which case [confusable] supplies a flat fallback.
     */
    fun suggestSmart(
        typed: String,
        max: Int,
        useSeeds: Boolean,
        confusable: Map<Char, Set<Char>>,
        taps: List<List<Pair<Char, Float>>> = emptyList()
    ): List<Cand> {
        if (typed.isEmpty()) return emptyList()
        ensureLoaded()
        val lower = typed.lowercase()
        val n = lower.length
        // per position: character -> cost of having meant it
        val subCost = ArrayList<Map<Char, Float>>(n)
        for (i in 0 until n) {
            val m = HashMap<Char, Float>()
            m[lower[i]] = 0f
            val alts = taps.getOrNull(i)
            if (alts != null) {
                for ((ch, c) in alts) {
                    val lc = ch.lowercaseChar()
                    val prev = m[lc]
                    if (prev == null || c < prev) m[lc] = c
                }
            } else {
                confusable[lower[i]]?.forEach { ch ->
                    val lc = ch.lowercaseChar()
                    if (!m.containsKey(lc)) m[lc] = FALLBACK_SUB
                }
            }
            subCost.add(m)
        }

        val budget = maxErrors(n)
        val slack = budget + 1
        // The DP never looks further along a word than the typed text can
        // reach, so one set of rows sized to that serves every candidate —
        // allocating three arrays per dictionary word was most of the cost.
        val width = n + slack + 2
        val rows = Array(3) { FloatArray(width) }

        // Which first letters are worth trying at all. This is the filter
        // that was missing: it only rejected on the first letter for words of
        // two characters or fewer, so in practice every word of a compatible
        // length went through the full table on every keystroke.
        val firstOk = HashSet<Char>(subCost[0].keys)
        // an extra character typed at the front: typed[1] lands on w[0]
        if (n >= 2) firstOk.addAll(subCost[1].keys)

        val out = ArrayList<Cand>()
        for ((w, c) in learned) {
            if (c < LEARN_CONFIRMED) continue
            if (!plausible(w, n, budget, firstOk)) continue
            score(lower, w, freqScore(c * 100), subCost, budget, slack, rows)
                ?.let { out.add(it) }
        }
        if (useSeeds) {
            val index = seedsByFirst()
            for (ch in firstOk) {
                val bucket = index[ch] ?: continue
                for (idx in bucket) {
                    val w = seeds[idx]
                    if (!plausible(w, n, budget, firstOk)) continue
                    score(lower, w, freqScore(seedFreq[idx]), subCost, budget, slack, rows)
                        ?.let { out.add(it) }
                }
            }
        }
        return out.asSequence()
            .sortedByDescending { it.score }
            .distinctBy { it.word }
            .take(max)
            .map { it.copy(word = recase(typed, it.word)) }
            .toList()
    }

    /** Seed words grouped by first letter, so only plausible starts are read. */
    private var firstIndex: Map<Char, IntArray>? = null

    @Synchronized
    private fun seedsByFirst(): Map<Char, IntArray> {
        firstIndex?.let { return it }
        val tmp = HashMap<Char, ArrayList<Int>>()
        for (i in seeds.indices) {
            val w = seeds[i]
            if (w.isEmpty()) continue
            tmp.getOrPut(w[0].lowercaseChar()) { ArrayList() }.add(i)
        }
        val built = HashMap<Char, IntArray>(tmp.size)
        for ((k, v) in tmp) built[k] = v.toIntArray()
        firstIndex = built
        return built
    }

    /** Rejects a candidate without touching the DP table. */
    private fun plausible(w: String, n: Int, budget: Int, firstOk: Set<Char>): Boolean {
        if (w.isEmpty()) return false
        // a correction cannot shorten the word past the error budget, though a
        // completion may run long
        if (w.length + budget < n) return false
        if (w.length > n + 12) return false
        return firstOk.contains(w[0].lowercaseChar())
    }

    private fun maxErrors(len: Int): Int = when {
        len <= 2 -> 0
        len <= 4 -> 1
        len <= 7 -> 2
        else -> 3
    }

    /** Matching is case-insensitive; give the suggestion the user's case:
     *  "Hel" → "Hello", "HEL" → "HELLO", "hel" → "hello". */
    private fun recase(typed: String, w: String): String {
        val first = typed.first()
        if (!first.isUpperCase()) return w
        return if (typed.length > 1 && typed.none { it.isLowerCase() })
            w.uppercase()
        else
            w.replaceFirstChar { it.titlecase() }
    }

    /**
     * Corpus frequencies span 1 … ~1,000,000, so they are folded onto a log
     * scale (≈ 0–550). One log-unit step (~40 points) weighs the same as one
     * extra character of completion length, keeping very common words ahead
     * without ever outranking an exact match with an error match.
     */
    private fun freqScore(c: Int): Int = (ln(1.0 + c) * 40.0).toInt()

    /**
     * Weighted Levenshtein with transposition, where the typed string is only
     * a PREFIX of the candidate: the trailing characters of a longer word are
     * free, which is what makes one routine serve completion and correction.
     *
     * Only a diagonal BAND is computed. A cell (i, j) can only come in under
     * budget while |i - j| stays within it, because every length mismatch
     * costs an insertion or a deletion — so the work is proportional to the
     * error budget, not to the length of the dictionary word. Without this the
     * table was O(typed × word) for tens of thousands of words per keystroke,
     * which is what made fast typing fall behind.
     *
     * [rows] is three scratch rows owned by the caller and reused across every
     * candidate.
     */
    private fun score(
        typed: String,
        w: String,
        freq: Int,
        subCost: List<Map<Char, Float>>,
        budget: Int,
        slack: Int,
        rows: Array<FloatArray>
    ): Cand? {
        if (w.equals(typed, ignoreCase = true)) return null
        val n = typed.length
        val m = w.length
        val maxCost = budget.toFloat()
        // never look further along the word than the typed text can reach
        val jMax = if (m < n + slack) m else n + slack

        var prevPrev = rows[0]
        var prev = rows[1]
        var cur = rows[2]

        for (j in 0..jMax) prev[j] = j * DEL_COST
        if (jMax + 1 < prev.size) prev[jMax + 1] = INF

        for (i in 1..n) {
            val jLo = if (i - slack > 1) i - slack else 1
            val jHi = if (i + slack < jMax) i + slack else jMax
            // seal the cells just outside the band so the recurrence cannot
            // read a stale value from an earlier, wider row
            cur[jLo - 1] = if (jLo - 1 == 0) i * INS_COST else INF
            if (jHi + 1 < cur.size) cur[jHi + 1] = INF

            var rowBest = cur[jLo - 1]
            val subs = subCost[i - 1]
            val ti = typed[i - 1].lowercaseChar()
            val tiPrev = if (i > 1) typed[i - 2].lowercaseChar() else ' '
            for (j in jLo..jHi) {
                val cj = w[j - 1].lowercaseChar()
                var best = prev[j - 1] + (subs[cj] ?: MISMATCH)
                val del = prev[j] + INS_COST      // typed char not in the word
                if (del < best) best = del
                val ins = cur[j - 1] + DEL_COST   // word char the user missed
                if (ins < best) best = ins
                if (i > 1 && j > 1 && ti == w[j - 2].lowercaseChar() && tiPrev == cj) {
                    val tr = prevPrev[j - 2] + TRANSPOSE_COST
                    if (tr < best) best = tr
                }
                cur[j] = best
                if (best < rowBest) rowBest = best
            }
            // nothing in this row can still come in under budget
            if (rowBest > maxCost) return null
            val spare = prevPrev
            prevPrev = prev
            prev = cur
            cur = spare
        }

        // the typed text may stop anywhere in the word (completion): take the
        // cheapest alignment inside the band
        var bestCost = Float.MAX_VALUE
        var bestJ = jMax
        val lo = if (n - slack > 1) n - slack else 1
        for (j in lo..jMax) {
            val c = prev[j]
            if (c < bestCost) { bestCost = c; bestJ = j }
        }
        if (bestCost > maxCost) return null

        val exact = bestCost < 0.001f
        var s = freq
        // an error-free match must always beat a corrected one
        s += if (exact) 10000 else 5000 - (bestCost * 1600f).toInt()
        // prefer finishing the word soon over a long completion
        s -= (m - bestJ) * 40
        s -= (m - n).coerceAtLeast(0) * 8
        return Cand(w, s, exact, m == n)
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
                // dictionary files may carry punctuation tokens ("." etc.);
                // never predict those as next words
                if (w != prev && !out.contains(w) && w.any { it.isLetter() }) out.add(w)
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
        // snapshot on the caller's thread, write on the background thread so
        // saving never stalls the keyboard
        val words = try {
            val sb = StringBuilder()
            // keep the store bounded: drop least-used words beyond 20000
            val entries = learned.entries.sortedByDescending { it.value }.take(20000)
            for ((w, c) in entries) sb.append(w).append('\t').append(c).append('\n')
            sb.toString()
        } catch (_: Exception) { null }
        val pairs = try {
            val sb = StringBuilder()
            var n = 0
            outer@ for ((w1, m) in bigrams) {
                for ((w2, c) in m) {
                    sb.append(w1).append('\t').append(w2).append('\t').append(c).append('\n')
                    if (++n >= 20000) break@outer
                }
            }
            sb.toString()
        } catch (_: Exception) { null }
        Io.writer.execute {
            try {
                if (words != null) wordFile().writeText(words)
            } catch (_: Exception) {
            }
            try {
                if (pairs != null) bigramFile().writeText(pairs)
            } catch (_: Exception) {
            }
        }
    }
}
