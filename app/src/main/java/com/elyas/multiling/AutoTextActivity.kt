package com.elyas.multiling

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Manage AutoText shortcuts: list, add, delete (long-press). */
class AutoTextActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }


    private lateinit var store: AutoTextStore
    private lateinit var adapter: ArrayAdapter<String>
    private var entries: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autotext)
        applyEdgePadding()
        store = AutoTextStore(this)
        supportActionBar?.setTitle(R.string.autotext_title)

        val list = findViewById<ListView>(R.id.autotextList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val short = findViewById<EditText>(R.id.editShortcut).text.toString().trim()
            val full = findViewById<EditText>(R.id.editExpansion).text.toString().trim()
            if (short.isEmpty() || full.isEmpty()) {
                Toast.makeText(this, R.string.autotext_fill_both, Toast.LENGTH_SHORT).show()
            } else if (short.contains(' ')) {
                Toast.makeText(this, R.string.autotext_no_space, Toast.LENGTH_SHORT).show()
            } else {
                store.put(short, full)
                findViewById<EditText>(R.id.editShortcut).setText("")
                findViewById<EditText>(R.id.editExpansion).setText("")
                refresh()
            }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
            showEditDialog(entry.first, entry.second)
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(entry.first)
                .setMessage(R.string.autotext_delete_q)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // a shortcut can carry several phrases: delete this one
                    store.remove(entry.first, entry.second)
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        refresh()
    }

    /** Edit an existing shortcut/expansion pair in place. */
    private fun showEditDialog(oldShortcut: String, oldExpansion: String) {
        val density = resources.displayMetrics.density
        val box = android.widget.LinearLayout(this)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.setPadding((20 * density).toInt(), (10 * density).toInt(),
            (20 * density).toInt(), 0)
        val shortIn = EditText(this)
        shortIn.setText(oldShortcut)
        shortIn.hint = getString(R.string.autotext_shortcut_hint)
        val fullIn = EditText(this)
        fullIn.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        fullIn.maxLines = 8
        fullIn.setText(oldExpansion)
        fullIn.hint = getString(R.string.autotext_expansion_hint)
        box.addView(shortIn)
        box.addView(fullIn)
        AlertDialog.Builder(this)
            .setTitle(R.string.autotext_edit)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ns = shortIn.text.toString().trim()
                val ne = fullIn.text.toString().trim()
                if (ns.isEmpty() || ne.isEmpty()) {
                    Toast.makeText(this, R.string.autotext_fill_both, Toast.LENGTH_SHORT).show()
                } else if (ns.contains(' ')) {
                    Toast.makeText(this, R.string.autotext_no_space, Toast.LENGTH_SHORT).show()
                } else {
                    store.remove(oldShortcut, oldExpansion)
                    store.put(ns, ne)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        AutoTextStore.bumpDataVersion(this)
        entries = store.all()
        adapter.clear()
        for ((s, e) in entries) adapter.add("$s  ←  ${e.replace("\n", " ⏎ ")}")
        adapter.notifyDataSetChanged()
    }
}
