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
 *    highest first) built from the Leipzig news corpora merged with word
 *    counts from each language's Wikipedia
 *  - words learned from the user's typing ("dict_<lang>.txt")
 *  - imported word lists (merged into the learned file), one word per line
 *    or "word<TAB>count".
 *
 * Also learns bigrams (word pairs) for next-word prediction.
 *
 * ## How new words are learned
 *
 * Modelled on the user-history dictionary of Android's own keyboard (AOSP
 * LatinIME, the base Gboard grew from), not on a repeat counter. A counter at
 * any threshold eventually swallows a habitual typo; what separates a word
 * the user means from a slip is what happens AROUND it:
 *
 *  1. **It has to survive.** The keyboard does not learn a word when it is
 *     typed, only once the user has moved on and it is still in the text
 *     ([MultilingIME] holds the last few words back and checks). Deleting it,
 *     backspacing into it, or replacing it with a suggestion withdraws it —
 *     the same "unlearn on backspace / on revert" rule LatinIME applies.
 *  2. **Levels, not counts.** Each separate occasion raises a word one
 *     level; repeats within [OCCASION_SECONDS] are one occasion, so typing a
 *     word five times in one message is still one piece of evidence. A word
 *     is offered from [VISIBLE] and trusted (no longer corrected, used to
 *     correct others) from [TRUSTED] — LatinIME's MIN_VISIBLE_LEVEL is 2.
 *  3. **Deliberate acts count more.** Tapping the typed word on the strip,
 *     or undoing an autocorrection, is the user saying "this is my word":
 *     it becomes visible at once. Long-press "add" makes it permanent.
 *  4. **Suspects need twice the evidence.** A word one slip away from a
 *     common word, or one the decoder had a confident correction for, takes
 *     two occasions per level instead of one — it can still be learned (a
 *     real word is allowed to look like another), it just has to prove it.
 *  5. **Forgetting.** Unused words lose a level every [DECAY_SECONDS]
 *     (LatinIME: 15 days); one-off entries that never got anywhere vanish
 *     after [DISCARD_SECONDS]. So an old typo fades instead of living
 *     forever, while words in real use stay.
 *  6. **Removal sticks.** Deleting a word from the strip blocks it, so it is
 *     never quietly relearned; only an explicit "add" brings it back.
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

        // ---- learning levels (see the class comment)
        /** From this level a learned word is offered on the strip. */
        const val VISIBLE = 2
        /** From this level it is a dictionary word: never autocorrected away,
         *  and allowed to be the target of a correction. */
        const val TRUSTED = 3
        private const val MAX_LEVEL = 15
        /** Hand-added words sit above every typed level and never decay. */
        const val EXPLICIT = 100
        /** Removed by the user: never relearned from typing. */
        private const val BLOCKED = -1

        /** Repeats closer together than this are one occasion. */
        private const val OCCASION_SECONDS = 10 * 60
        /** An unused word drops one level per this much time. */
        private const val DECAY_SECONDS = 15 * 24 * 60 * 60
        /** A level-0/1 entry that saw no use for this long is dropped. */
        private const val DISCARD_SECONDS = 14 * 24 * 60 * 60
        /** Bound on the learned store. */
        private const val MAX_ENTRIES = 20000

        private const val FLAG_SUSPECT = 1

        /** Only words at least this common count as typo neighbours. */
        private const val TYPO_NEIGHBOUR_FREQ = 200

        private const val HEADER = "#hk-words v2"

        fun now(): Int = (System.currentTimeMillis() / 1000L).toInt()

        /**
         * Number of words a user would see as "learned" in a dict file,
         * without loading the language. Understands both file formats.
         */
        fun countVisible(f: File): Int {
            var n = 0
            var v2 = false
            try {
                f.forEachLine { line ->
                    if (line.startsWith("#")) { if (line == HEADER) v2 = true; return@forEachLine }
                    val p = line.split('\t')
                    if (p.isEmpty() || p[0].isBlank()) return@forEachLine
                    val lvl = if (v2) p.getOrNull(1)?.toIntOrNull() ?: 0
                    else legacyLevel(p.getOrNull(1)?.trim()?.toIntOrNull() ?: 1)
                    if (lvl >= VISIBLE) n++
                }
            } catch (_: Exception) {
            }
            return n
        }

        /** Map an old repeat count onto the level scale. */
        private fun legacyLevel(count: Int): Int = when {
            count >= 50 -> EXPLICIT
            count >= 6 -> 4
            count >= 3 -> VISIBLE
            else -> 1
        }
    }

    /** What the user did that is evidence for a word. */
    enum class Signal {
        /** Typed and kept: committed with a space or punctuation and still
         *  in the text after the user moved on. */
        TYPED,
        /** Tapped the typed word itself on the suggestion strip. */
        PICKED_TYPED,
        /** Undid an autocorrection to get this word back. */
        REVERTED,
        /** A word already learned was used again (picked or typed). */
        USED
    }

    /** A learned word's history. [ts] is the last time it was seen, in
     *  seconds; [hits] counts occasions toward the next level for suspects. */
    private class Entry(var level: Int, var hits: Int, var ts: Int, var flags: Int) {
        val suspect get() = (flags and FLAG_SUSPECT) != 0
        val blocked get() = level == BLOCKED
    }

    /** A scored suggestion candidate. */
    data class Cand(
        val word: String, val score: Int, val exact: Boolean, val sameLen: Boolean,
        /** Edit cost of the match; about 1.0 is one clear slip. */
        val cost: Float = 0f
    )

    private val learned = HashMap<String, Entry>()
    private var seeds: List<String> = emptyList()   // frequency order, high → low
    private var seedFreq: IntArray = IntArray(0)    // parallel corpus frequencies
    private var seedSet: Lexicon = Lexicon(emptyArray(), IntArray(0), IntArray(0))

    /**
     * The dictionary in code-unit order with each word's frequency. Doubles
     * as the membership test (binary search: with a quarter of a million
     * words per language, a HashSet's per-entry nodes cost tens of megabytes
     * across the enabled languages) and as the flat trie [suggestSmart]
     * walks.
     */
    private class Lexicon(val words: Array<String>, val freq: IntArray, val rank: IntArray) {
        val size get() = words.size
        fun contains(w: String): Boolean = java.util.Arrays.binarySearch(words, w) >= 0

        /** First index in [from, to) whose word is >= [key]. */
        fun lowerBound(key: String, from: Int, to: Int): Int {
            var a = from
            var b = to
            while (a < b) {
                val mid = (a + b) ushr 1
                if (words[mid] < key) a = mid + 1 else b = mid
            }
            return a
        }
    }
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var loaded = false
    private var dirty = false

    private fun wordFile(): File = File(context.filesDir, "dict_$langCode.txt")
    private fun bigramFile(): File = File(context.filesDir, "bigram_$langCode.txt")

    /** Parse everything up front (call from a background thread) so the
     *  first keystroke doesn't pay the decompression cost. */
    @Synchronized
    fun preload() {
        ensureLoaded()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val stream = try {
                XZInputStream(context.assets.open("dict/$langCode.txt.xz"))
            } catch (_: Exception) {
                context.assets.open("dict/$langCode.txt")
            }
            val ws = ArrayList<String>(250000)
            var fs = IntArray(250000)
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val idx = line.indexOf('\t')
                    if (idx > 0) {
                        if (ws.size == fs.size) fs = fs.copyOf(fs.size * 2)
                        fs[ws.size] = line.substring(idx + 1).toIntOrNull() ?: 1
                        ws.add(line.substring(0, idx))
                    } else {
                        val w = line.trim()
                        if (w.isNotEmpty()) {
                            if (ws.size == fs.size) fs = fs.copyOf(fs.size * 2)
                            fs[ws.size] = 1
                            ws.add(w)
                        }
                    }
                }
            }
            ws.trimToSize()
            seeds = ws
            seedFreq = fs.copyOf(ws.size)
            val order = ws.indices.sortedWith { a, b -> ws[a].compareTo(ws[b]) }
            seedSet = Lexicon(Array(order.size) { ws[order[it]] }, IntArray(order.size) { fs[order[it]] },
                IntArray(order.size) { order[it] })
        } catch (_: Exception) {
            seeds = emptyList()
            seedFreq = IntArray(0)
            seedSet = Lexicon(emptyArray(), IntArray(0), IntArray(0))
        }
        // the learned file is read after the dictionary: migrating an old
        // file needs the typo test, which needs the dictionary
        try {
            val f = wordFile()
            if (f.exists()) readWordFile(f)
        } catch (_: Exception) {
        }
        if (forgetOld()) dirty = true
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

    private fun readWordFile(f: File) {
        var v2 = false
        val t = now()
        f.forEachLine { line ->
            if (line.startsWith("#")) {
                if (line == HEADER) v2 = true
                return@forEachLine
            }
            val p = line.split('\t')
            val w = p[0].trim()
            if (w.isEmpty()) return@forEachLine
            if (v2) {
                val lvl = p.getOrNull(1)?.toIntOrNull() ?: 1
                val hits = p.getOrNull(2)?.toIntOrNull() ?: 0
                val ts = p.getOrNull(3)?.toIntOrNull() ?: t
                val fl = p.getOrNull(4)?.toIntOrNull() ?: 0
                learned[w] = Entry(lvl, hits, ts, fl)
            } else {
                // old "word<TAB>count" file: keep what was trusted, and give
                // everything a fresh timestamp so nothing vanishes at once
                val lvl = legacyLevel(p.getOrNull(1)?.trim()?.toIntOrNull() ?: 1)
                val fl = if (lvl < EXPLICIT && looksLikeTypoRaw(w)) FLAG_SUSPECT else 0
                // a suspect from the old count-only days starts over at the
                // bottom: it has to earn its place under the new rules
                learned[w] = Entry(if (fl != 0) minOf(lvl, 1) else lvl, 0, t, fl)
                dirty = true
            }
        }
    }

    /**
     * The forgetting curve: lower unused words one level per [DECAY_SECONDS]
     * and drop entries that never got off the ground. Returns true if
     * anything changed.
     */
    private fun forgetOld(): Boolean {
        val t = now()
        var changed = false
        val it = learned.entries.iterator()
        while (it.hasNext()) {
            val (_, e) = it.next()
            if (e.level >= EXPLICIT || e.blocked) continue
            val idle = t - e.ts
            if (idle <= 0) continue
            if (e.level <= 1 && idle > DISCARD_SECONDS) {
                it.remove(); changed = true; continue
            }
            val steps = idle / DECAY_SECONDS
            if (steps > 0) {
                e.level -= steps
                e.ts += steps * DECAY_SECONDS
                e.hits = 0
                changed = true
                if (e.level <= 0) it.remove()
            }
        }
        return changed
    }

    private fun isVisible(e: Entry?): Boolean = e != null && e.level >= VISIBLE
    private fun isTrusted(e: Entry?): Boolean = e != null && e.level >= TRUSTED

    /**
     * Words not worth learning from typing at all: digits mixed in, letters
     * from two scripts, or one letter held down ("هههههه", "nooooo").
     */
    private fun learnable(w: String): Boolean {
        if (w.length < 2 || w.length > 32) return false
        if (w.any { it.isDigit() }) return false
        if (!w.all { it.isLetter() || it == '‌' || it == '\'' || it == '-' }) return false
        var latin = false
        var other = false
        for (ch in w) if (ch.isLetter()) { if (ch < 'ɐ') latin = true else other = true }
        if (latin && other) return false
        var run = 1
        for (i in 1 until w.length) {
            run = if (w[i] == w[i - 1]) run + 1 else 1
            if (run >= 3) return false
        }
        return true
    }

    /**
     * Record evidence for a word the user kept — see the class comment.
     *
     * @param suspect the decoder had a confident correction for it when it
     *   was committed, i.e. the keyboard thought it was a typo.
     */
    @Synchronized
    fun observe(word: String, signal: Signal, suspect: Boolean = false) {
        if (!learnable(word)) return
        ensureLoaded()
        val t = now()
        val e = learned[word]
        if (e != null && e.blocked) return            // the user removed it
        if (e != null && e.level >= EXPLICIT) { e.ts = t; dirty = true; return }
        // a dictionary word needs no learning; the strip already knows it
        if (e == null && seedSet.contains(word)) return

        val entry = e ?: Entry(0, 0, t - OCCASION_SECONDS - 1,
            if (suspect || looksLikeTypo(word)) FLAG_SUSPECT else 0).also { learned[word] = it }
        when (signal) {
            Signal.PICKED_TYPED, Signal.REVERTED -> {
                // the user said in so many words that this is what they mean
                entry.flags = entry.flags and FLAG_SUSPECT.inv()
                entry.level = maxOf(entry.level + 1, VISIBLE).coerceAtMost(MAX_LEVEL)
                entry.hits = 0
            }
            Signal.TYPED, Signal.USED -> {
                if (t - entry.ts >= OCCASION_SECONDS) {
                    if (entry.suspect) {
                        entry.hits++
                        if (entry.hits >= 2) { entry.level++; entry.hits = 0 }
                    } else {
                        entry.level++
                    }
                    entry.level = entry.level.coerceAtMost(MAX_LEVEL)
                }
            }
        }
        entry.ts = t
        dirty = true
    }

    /** The user hand-added this word; trust it immediately and for good. */
    @Synchronized
    fun learnExplicit(word: String) {
        if (word.isEmpty() || word.length > 32) return
        ensureLoaded()
        learned[word] = Entry(EXPLICIT, 0, now(), 0)
        dirty = true
        save()
    }

    /**
     * Withdraw evidence: the user deleted the word they just typed, or
     * swapped it for a suggestion. One step down, and it becomes a suspect,
     * so a word that keeps getting fixed can never climb back easily.
     */
    @Synchronized
    fun penalize(word: String) {
        ensureLoaded()
        val e = learned[word] ?: return
        if (e.level >= EXPLICIT || e.blocked) return
        e.level -= 1
        e.hits = 0
        e.flags = e.flags or FLAG_SUSPECT
        if (e.level <= 0) learned.remove(word)
        dirty = true
    }

    /** Kept for callers of the old API. */
    @Synchronized
    fun unlearnRecent(word: String) = penalize(word)

    /** True when the decoder would show this learned word. */
    @Synchronized
    fun isLearnedVisible(word: String): Boolean {
        ensureLoaded()
        return isVisible(learned[word])
    }

    /**
     * True when a single slip explains the word as a common dictionary word.
     * Deliberately strict — one substitution, insertion, deletion or swap
     * against a word of real corpus frequency.
     */
    @Synchronized
    fun looksLikeTypo(word: String): Boolean {
        ensureLoaded()
        return looksLikeTypoRaw(word)
    }

    private fun looksLikeTypoRaw(word: String): Boolean {
        if (word.length < 3) return false
        val lw = word.lowercase()
        if (seedSet.contains(lw)) return false
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
     * Learned words that are one slip away from a common dictionary word and
     * were never confirmed by hand. The settings screen offers to remove
     * them in bulk.
     */
    @Synchronized
    fun suspiciousLearned(): List<String> {
        ensureLoaded()
        return learned.entries
            .filter { (w, e) -> !e.blocked && e.level < EXPLICIT && (e.suspect || looksLikeTypo(w)) }
            .map { it.key }
            .sorted()
    }

    /** Learned words the user can see, with their level, strongest first. */
    @Synchronized
    fun learnedWords(): List<Pair<String, Int>> {
        ensureLoaded()
        return learned.entries
            .filter { isVisible(it.value) }
            .sortedByDescending { it.value.level }
            .map { it.key to it.value.level }
    }

    @Synchronized
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

    @Synchronized
    fun learnBigram(prev: String, next: String) {
        if (prev.length < 2 || next.length < 2) return
        ensureLoaded()
        val m = bigrams.getOrPut(prev) { HashMap() }
        m[next] = (m[next] ?: 0) + 1
        dirty = true
    }

    /**
     * The user removed this word from the strip. It is blocked rather than
     * just deleted, so typing it again does not quietly bring it back.
     */
    @Synchronized
    fun forget(word: String) {
        ensureLoaded()
        learned[word] = Entry(BLOCKED, 0, now(), 0)
        dirty = true
    }

    @Synchronized
    fun clearLearned() {
        ensureLoaded()
        learned.clear()
        bigrams.clear()
        dirty = true
        save()
    }

    /** Ranking weight of a learned word, on the corpus-frequency scale. */
    private fun learnedFreq(e: Entry): Int =
        if (e.level >= EXPLICIT) 30000 else 200 * e.level * e.level

    /**
     * Prefix completions: learned words first (by level), then seeds —
     * which are already ordered by corpus frequency, so the most common
     * words of the language come first.
     */
    @Synchronized
    fun suggest(prefix: String, max: Int, useSeeds: Boolean): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensureLoaded()
        val out = ArrayList<String>()
        learned.entries
            .asSequence()
            .filter { isVisible(it.value) && it.key.startsWith(prefix) && it.key != prefix }
            .sortedByDescending { it.value.level }
            .take(max)
            .forEach { out.add(it.key) }
        if (useSeeds && out.size < max) {
            for (w in seeds) {
                if (out.size >= max) break
                if (w.startsWith(prefix) && w != prefix && !out.contains(w) &&
                    learned[w]?.blocked != true) out.add(w)
            }
        }
        return out
    }

    /** True when the word is an established dictionary word (any case). */
    @Synchronized
    fun contains(word: String): Boolean {
        ensureLoaded()
        if (isTrusted(learned[word]) || seedSet.contains(word)) return true
        val lc = word.lowercase()
        return lc != word && (isTrusted(learned[lc]) || seedSet.contains(lc))
    }

    /**
     * Layout-aware correction and completion.
     *
     * A weighted edit distance (substitute / insert / delete / transpose)
     * scored against a spatial model, then combined with word frequency —
     * how Gboard and Samsung rank candidates. Costs come from where the
     * finger ACTUALLY landed when we have it ([taps]), so a letter next to
     * the intended key is cheap and one across the keyboard is not.
     *
     * ## Why it stays fast with a quarter of a million words
     *
     * AOSP's keyboard walks a trie: words that share a beginning share the
     * work for it, and a whole branch is dropped the moment its beginning is
     * already too far from what was typed. The dictionary here is kept as a
     * sorted array, which is the same trie laid out flat — neighbours share
     * prefixes — so the walk keeps one DP column per character of the
     * current path, recomputes only the columns past the prefix it shares
     * with the previous word, and jumps over every word under a dead prefix
     * with a binary search. Most of the dictionary is never touched.
     *
     * @param taps per-typed-character alternatives with costs, from
     *   [SpatialModel.alternativesFor]; may be shorter than [typed] (or
     *   empty), in which case [confusable] supplies a flat fallback.
     */
    @Synchronized
    fun suggestSmart(
        typed: String,
        max: Int,
        useSeeds: Boolean,
        confusable: Map<Char, Set<Char>>,
        taps: List<List<Pair<Char, Float>>> = emptyList()
    ): List<Cand> {
        if (typed.isEmpty()) return emptyList()
        ensureLoaded()
        val q = Query(typed.lowercase(), confusable, taps)
        val top = TopK(max * 2)
        val starts = q.startChars()

        for ((w, e) in learned) {
            if (!isVisible(e)) continue
            if (!q.plausible(w) || w[0] !in starts) continue
            q.walkSingle(w)
            q.finalFor(w)?.let { (cost, bestJ) ->
                // learned words lead their ties, as they did when listed first
                top.offer(q.scoreOf(w, freqScore(learnedFreq(e)), cost, bestJ), Int.MAX_VALUE, w, cost, w.length == q.n)
            }
        }
        if (useSeeds) {
            val lex = seedSet
            for (ch in starts) {
                val lo = lex.lowerBound(ch.toString(), 0, lex.size)
                val hi = lex.lowerBound((ch + 1).toString(), lo, lex.size)
                q.walkRange(lex, lo, hi) { k, cost, bestJ ->
                    val w = lex.words[k]
                    if (learned[w]?.blocked != true) {
                        top.offer(q.scoreOf(w, freqScore(lex.freq[k]), cost, bestJ),
                            Int.MAX_VALUE - 1 - lex.rank[k], w, cost, w.length == q.n)
                    }
                }
            }
        }
        return top.sorted()
            .distinctBy { it.word }
            .take(max)
            .map { it.copy(word = recase(typed, it.word)) }
    }

    /**
     * Keeps the best [cap] candidates without allocating for the rest.
     * Ranked by score, then by [order] (higher first), so equal scores still
     * come out in dictionary-frequency order whatever order the walk found
     * them in.
     */
    private class TopK(private val cap: Int) {
        private val keys = LongArray(cap)
        private val scores = IntArray(cap)
        private val words = arrayOfNulls<String>(cap)
        private val costs = FloatArray(cap)
        private val same = BooleanArray(cap)
        private var size = 0
        private var minAt = 0

        fun offer(score: Int, order: Int, w: String, cost: Float, sameLen: Boolean) {
            val key = (score.toLong() shl 32) or (order.toLong() and 0xFFFFFFFFL)
            val at = if (size < cap) size++ else {
                if (key <= keys[minAt]) return
                minAt
            }
            keys[at] = key; scores[at] = score; words[at] = w; costs[at] = cost; same[at] = sameLen
            if (size == cap) {
                var m = 0
                for (i in 1 until size) if (keys[i] < keys[m]) m = i
                minAt = m
            }
        }

        fun sorted(): List<Cand> = (0 until size)
            .sortedByDescending { keys[it] }
            .map { Cand(words[it]!!, scores[it], costs[it] < 0.001f, same[it], costs[it]) }
    }

    /**
     * One typed word, ready to be matched: per-position substitution costs
     * as flat arrays (a Map<Char, Float> boxed a Character for every Arabic-
     * script cell), and the DP columns of the current dictionary path.
     *
     * Column j holds, for every i, the cost of aligning the first i typed
     * characters with the first j characters of the word. Only a diagonal
     * band |i - j| <= slack is live: every length mismatch costs an insert
     * or a delete, so cells outside it can never come in under budget.
     */
    private class Query(
        val typed: String,
        confusable: Map<Char, Set<Char>>,
        taps: List<List<Pair<Char, Float>>>
    ) {
        val n = typed.length
        val budget = when {
            n <= 2 -> 0
            n <= 4 -> 1
            n <= 7 -> 2
            else -> 3
        }
        private val maxCost = budget.toFloat()
        private val slack = budget + 1
        /** The walk never goes deeper than the typed text can reach. */
        private val depth = n + slack
        private val subChars = arrayOfNulls<CharArray>(n)
        private val subVals = arrayOfNulls<FloatArray>(n)
        private val cols = Array(depth + 1) { FloatArray(n + 1) }
        private val colMin = FloatArray(depth + 1)
        private val bestAt = FloatArray(depth + 1)
        private val bestJAt = IntArray(depth + 1)
        /** First typed position whose column counts as "the word may end here". */
        private val lo = if (n - slack > 1) n - slack else 1

        // the path currently held in [cols]
        private var path: String = ""
        private var pathDepth = 0
        /** Depth where the path stopped: nothing deeper can help. */
        private var frozen = Int.MAX_VALUE

        init {
            for (i in 0 until n) {
                val m = LinkedHashMap<Char, Float>()
                m[typed[i]] = 0f
                val alts = taps.getOrNull(i)
                if (alts != null) {
                    for ((ch, c) in alts) {
                        val lc = ch.lowercaseChar()
                        val prev = m[lc]
                        if (prev == null || c < prev) m[lc] = c
                    }
                } else {
                    confusable[typed[i]]?.forEach { ch ->
                        val lc = ch.lowercaseChar()
                        if (!m.containsKey(lc)) m[lc] = FALLBACK_SUB
                    }
                }
                subChars[i] = m.keys.toCharArray()
                subVals[i] = m.values.toFloatArray()
            }
            for (i in 0..n) cols[0][i] = if (i <= slack + 1) i * INS_COST else INF
            colMin[0] = 0f
            bestAt[0] = INF
            bestJAt[0] = 0
        }

        /** Letters a match may start with: the typed first letter and its
         *  neighbours, or the second one (an extra letter typed in front),
         *  in both cases. */
        fun startChars(): Set<Char> {
            val s = HashSet<Char>()
            for (c in subChars[0]!!) { s.add(c); s.add(c.uppercaseChar()) }
            if (n >= 2) for (c in subChars[1]!!) { s.add(c); s.add(c.uppercaseChar()) }
            return s
        }

        fun plausible(w: String): Boolean {
            val m = w.length
            if (m == 0 || m + budget < n || m > n + 12) return false
            return !w.equals(typed, ignoreCase = true)
        }

        private fun sub(i: Int, c: Char): Float {
            val cs = subChars[i]!!
            for (k in cs.indices) if (cs[k] == c) return subVals[i]!![k]
            return MISMATCH
        }

        /** Fill column j for character [c] (and [cp] before it) of the path. */
        private fun column(j: Int, c: Char, cp: Char) {
            val col = cols[j]
            val pc = cols[j - 1]
            val ppc = if (j >= 2) cols[j - 2] else null
            col[0] = j * DEL_COST
            var mn = col[0]
            for (i in 1..n) {
                if (i - j > slack || j - i > slack) { col[i] = INF; continue }
                var best = pc[i - 1] + sub(i - 1, c)
                val ins = col[i - 1] + INS_COST   // typed char not in the word
                if (ins < best) best = ins
                val del = pc[i] + DEL_COST        // word char the user missed
                if (del < best) best = del
                if (ppc != null && i > 1 && typed[i - 1] == cp && typed[i - 2] == c) {
                    val tr = ppc[i - 2] + TRANSPOSE_COST
                    if (tr < best) best = tr
                }
                col[i] = best
                if (best < mn) mn = best
            }
            colMin[j] = mn
            // the typed text may stop anywhere in the word (completion)
            val end = if (j >= lo) col[n] else INF
            if (end < bestAt[j - 1]) { bestAt[j] = end; bestJAt[j] = j }
            else { bestAt[j] = bestAt[j - 1]; bestJAt[j] = bestJAt[j - 1] }
        }

        /**
         * Extend the held path to [w], reusing the columns of the prefix it
         * shares with the previous word. Returns the depth reached.
         */
        private fun extend(w: String): Int {
            var d = 0
            val lim = minOf(pathDepth, w.length)
            while (d < lim && w[d] == path[d]) d++
            if (frozen <= d) return frozen   // shares a prefix that already ended the walk
            frozen = Int.MAX_VALUE
            val target = minOf(w.length, depth)
            var j = d
            while (j < target) {
                j++
                column(j, w[j - 1].lowercaseChar(), if (j >= 2) w[j - 2].lowercaseChar() else ' ')
                if (colMin[j] > maxCost) { frozen = j; break }
            }
            path = w
            pathDepth = j
            return j
        }

        /** Walk one word on its own (learned words: no shared order). */
        fun walkSingle(w: String) {
            pathDepth = 0
            frozen = Int.MAX_VALUE
            extend(w)
        }

        /** Cost and alignment end for the word just walked, or null. */
        fun finalFor(w: String): Pair<Float, Int>? {
            val j = minOf(pathDepth, minOf(w.length, depth))
            val c = bestAt[j]
            return if (c > maxCost) null else c to bestJAt[j]
        }

        /**
         * Walk every word in [lo, hi) of the sorted dictionary, calling
         * [hit] for each one within budget.
         */
        fun walkRange(lex: Lexicon, lo: Int, hi: Int, hit: (Int, Float, Int) -> Unit) {
            pathDepth = 0
            path = ""
            frozen = Int.MAX_VALUE
            var k = lo
            while (k < hi) {
                val w = lex.words[k]
                val j = extend(w)
                val stopped = frozen == j
                val end = minOf(j, minOf(w.length, depth))
                val cost = bestAt[end]
                if (stopped && cost > maxCost) {
                    // nothing under this prefix can match: jump past it
                    k = lex.lowerBound(w.substring(0, j) + '￿', k + 1, hi)
                    continue
                }
                if (cost <= maxCost && plausible(w)) hit(k, cost, bestJAt[end])
                k++
            }
        }

        fun scoreOf(w: String, freq: Int, cost: Float, bestJ: Int): Int {
            val m = w.length
            var s = freq
            // an error-free match must always beat a corrected one
            s += if (cost < 0.001f) 10000 else 5000 - (cost * 1600f).toInt()
            // prefer finishing the word soon over a long completion
            s -= (m - bestJ) * 40
            s -= (m - n).coerceAtLeast(0) * 8
            return s
        }
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
     * Next-word prediction: learned bigrams first (repeated pairs only),
     * then the most frequent words of the language fill the empty slots.
     */
    @Synchronized
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
     * Merge an imported plain-text word list ("word" or "word<TAB>count" per
     * line). Imported words are trusted at once — the user chose to bring
     * them in. Returns how many words were added.
     */
    @Synchronized
    fun importText(text: String): Int {
        ensureLoaded()
        val before = learned.size
        val t = now()
        for (line in text.lineSequence()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#") || l.length > 48) continue
            val w = l.substringBefore('\t').trim()
            if (w.isEmpty() || w.length > 32) continue
            val n = l.substringAfter('\t', "").trim().toIntOrNull() ?: 0
            val lvl = if (n >= EXPLICIT || n >= 50) EXPLICIT else TRUSTED
            val e = learned[w]
            if (e == null || e.blocked || e.level < lvl) learned[w] = Entry(lvl, 0, t, 0)
        }
        dirty = true
        save()
        return learned.size - before
    }

    /** The learned words a user would recognise, as "word<TAB>level" lines
     *  that [importText] reads back. */
    @Synchronized
    fun exportText(): String {
        ensureLoaded()
        val sb = StringBuilder()
        for ((w, lvl) in learnedWords()) sb.append(w).append('\t').append(lvl).append('\n')
        return sb.toString()
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        // snapshot on the caller's thread, write on the background thread so
        // saving never stalls the keyboard
        val words = try {
            val sb = StringBuilder(HEADER).append('\n')
            // keep the store bounded: strongest and most recent words first
            val entries = learned.entries
                .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.level }
                    .thenByDescending { it.value.ts })
                .take(MAX_ENTRIES)
            for ((w, e) in entries) {
                sb.append(w).append('\t').append(e.level).append('\t').append(e.hits)
                    .append('\t').append(e.ts).append('\t').append(e.flags).append('\n')
            }
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
