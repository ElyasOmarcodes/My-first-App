package com.elyas.multiling

import android.content.Context
import java.io.File

/**
 * Clipboard history: keeps the last 20 copied items (text or image), full
 * length so long copies are never cut short. Items are separated by a
 * control character that never appears in normal text; each item carries a
 * one-character type tag ("T" text, "I" image-uri) after a second control
 * character.
 */
class ClipboardStore(private val context: Context) {

    /** One clipboard entry: exactly one of [text]/[imageUri] is non-null. */
    data class Item(val text: String?, val imageUri: String?) {
        val isImage: Boolean get() = imageUri != null
    }

    companion object {
        private const val SEP = '\u0001'
        private const val TAG = '\u0002'
        private const val MAX_ITEMS = 20
        private const val MAX_LEN = 100_000
    }

    private var items: ArrayList<Item>? = null

    private fun file(): File = File(context.filesDir, "clipboard.bin")

    private fun ensureLoaded(): ArrayList<Item> {
        items?.let { return it }
        val list = ArrayList<Item>()
        try {
            val f = file()
            if (f.exists()) {
                for (part in f.readText().split(SEP)) {
                    if (part.isEmpty()) continue
                    val tagIdx = part.indexOf(TAG)
                    if (tagIdx == 1 && part[0] == 'I') {
                        list.add(Item(null, part.substring(2)))
                    } else if (tagIdx == 1 && part[0] == 'T') {
                        list.add(Item(part.substring(2), null))
                    } else {
                        // legacy plain-text entry
                        list.add(Item(part, null))
                    }
                }
            }
        } catch (_: Exception) {
        }
        items = list
        return list
    }

    fun add(text: String) {
        if (text.isEmpty()) return
        addItem(Item(text.take(MAX_LEN), null))
    }

    fun addImage(uri: String) {
        if (uri.isEmpty()) return
        addItem(Item(null, uri))
    }

    private fun addItem(item: Item) {
        val list = ensureLoaded()
        list.removeAll {
            if (item.isImage) it.imageUri == item.imageUri else it.text == item.text
        }
        list.add(0, item)
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save()
    }

    fun all(): List<Item> = ensureLoaded().toList()

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
            val text = ensureLoaded().joinToString(SEP.toString()) { item ->
                if (item.isImage) "I$TAG${item.imageUri}" else "T$TAG${item.text}"
            }
            file().writeText(text)
        } catch (_: Exception) {
        }
    }
}
