package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * Very small on-device word-frequency store used for suggestions.
 * One plain-text file per language: "word<TAB>count" lines.
 * Fully offline; nothing ever leaves the device.
 */
class WordStore(private val context: Context, private val langCode: String) {

    private val words = HashMap<String, Int>()
    private var loaded = false
    private var dirty = false

    private fun file(): File = File(context.filesDir, "dict_$langCode.txt")

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val f = file()
            if (f.exists()) {
                f.forEachLine { line ->
                    val idx = line.indexOf('\t')
                    if (idx > 0) {
                        val w = line.substring(0, idx)
                        val c = line.substring(idx + 1).toIntOrNull() ?: 1
                        words[w] = c
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun learn(word: String) {
        if (word.length < 2 || word.length > 32) return
        ensureLoaded()
        words[word] = (words[word] ?: 0) + 1
        dirty = true
    }

    fun forget(word: String) {
        ensureLoaded()
        if (words.remove(word) != null) dirty = true
    }

    fun suggest(prefix: String, max: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensureLoaded()
        return words.entries
            .asSequence()
            .filter { it.key.startsWith(prefix) && it.key != prefix }
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key }
            .toList()
    }

    fun save() {
        if (!dirty) return
        dirty = false
        try {
            val sb = StringBuilder()
            // keep the store bounded: drop least-used words beyond 5000
            val entries = words.entries.sortedByDescending { it.value }.take(5000)
            for ((w, c) in entries) sb.append(w).append('\t').append(c).append('\n')
            file().writeText(sb.toString())
        } catch (_: Exception) {
        }
    }
}
