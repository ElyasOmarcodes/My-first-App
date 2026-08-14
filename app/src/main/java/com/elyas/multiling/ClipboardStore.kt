package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * Clipboard history: keeps the last 20 copied texts (full length, so long
 * copies are never cut short). Items are separated by a control character
 * that never appears in normal text.
 */
class ClipboardStore(private val context: Context) {

    companion object {
        private const val SEP = '\u0001'
        private const val MAX_ITEMS = 20
        private const val MAX_LEN = 100_000
    }

    private var items: ArrayList<String>? = null
    private var pins: ArrayList<String>? = null

    private fun file(): File = File(context.filesDir, "clipboard.bin")
    private fun pinFile(): File = File(context.filesDir, "clipboard_pins.bin")

    /** Read the history file up front (from a background thread) so the
     *  first copy doesn't block the keyboard. */
    fun preload() {
        ensureLoaded()
    }

    @Synchronized
    private fun ensureLoaded(): ArrayList<String> {
        items?.let { return it }
        fun read(f: File): ArrayList<String> {
            val list = ArrayList<String>()
            try {
                if (f.exists()) {
                    for (part in f.readText().split(SEP)) {
                        if (part.isNotEmpty()) list.add(part)
                    }
                }
            } catch (_: Exception) {
            }
            return list
        }
        pins = read(pinFile())
        val list = read(file())
        items = list
        return list
    }

    fun add(text: String) {
        if (text.isEmpty()) return
        val t = text.take(MAX_LEN)
        val list = ensureLoaded()
        if (pins?.contains(t) == true) return // already kept as a pin
        list.remove(t)
        list.add(0, t)
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save()
    }

    /** Pinned first, then the recent history. */
    fun all(): List<String> {
        val recent = ensureLoaded()
        return (pins ?: emptyList<String>()) + recent
    }

    fun newest(): String? = ensureLoaded().firstOrNull()

    fun isPinned(text: String): Boolean {
        ensureLoaded()
        return pins?.contains(text) == true
    }

    /** Pin keeps an item forever (survives eviction and clear-all). */
    fun togglePin(text: String) {
        val recent = ensureLoaded()
        val p = pins ?: return
        if (p.remove(text)) {
            recent.remove(text)
            recent.add(0, text)
        } else {
            recent.remove(text)
            p.add(0, text)
            while (p.size > MAX_ITEMS) p.removeAt(p.size - 1)
        }
        save()
    }

    fun remove(text: String) {
        val recent = ensureLoaded()
        val a = recent.remove(text)
        val b = pins?.remove(text) == true
        if (a || b) save()
    }

    /** Clear the history — pinned items survive. */
    fun clear() {
        ensureLoaded().clear()
        save()
    }

    private fun save() {
        // snapshot on the caller's thread, write on the background thread —
        // clipboard items can total megabytes and froze the UI when written
        // inline
        val data = ensureLoaded().joinToString(SEP.toString())
        val pinData = (pins ?: emptyList<String>()).joinToString(SEP.toString())
        Io.writer.execute {
            try {
                file().writeText(data)
            } catch (_: Exception) {
            }
            try {
                pinFile().writeText(pinData)
            } catch (_: Exception) {
            }
        }
    }
}
