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

    private fun file(): File = File(context.filesDir, "clipboard.bin")

    private fun ensureLoaded(): ArrayList<String> {
        items?.let { return it }
        val list = ArrayList<String>()
        try {
            val f = file()
            if (f.exists()) {
                for (part in f.readText().split(SEP)) {
                    if (part.isNotEmpty()) list.add(part)
                }
            }
        } catch (_: Exception) {
        }
        items = list
        return list
    }

    fun add(text: String) {
        if (text.isEmpty()) return
        val t = text.take(MAX_LEN)
        val list = ensureLoaded()
        list.remove(t)
        list.add(0, t)
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save()
    }

    fun all(): List<String> = ensureLoaded().toList()

    fun removeAt(index: Int) {
        val list = ensureLoaded()
        if (index in list.indices) {
            list.removeAt(index)
            save()
        }
    }

    fun clear() {
        ensureLoaded().clear()
        save()
    }

    private fun save() {
        try {
            file().writeText(ensureLoaded().joinToString(SEP.toString()))
        } catch (_: Exception) {
        }
    }
}
