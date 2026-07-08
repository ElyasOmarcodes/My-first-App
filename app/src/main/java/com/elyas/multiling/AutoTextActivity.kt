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

    private lateinit var store: AutoTextStore
    private lateinit var adapter: ArrayAdapter<String>
    private var entries: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autotext)
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

        list.setOnItemLongClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(entry.first)
                .setMessage(R.string.autotext_delete_q)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    store.remove(entry.first)
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        refresh()
    }

    private fun refresh() {
        entries = store.all()
        adapter.clear()
        for ((s, e) in entries) adapter.add("$s  ←  $e")
        adapter.notifyDataSetChanged()
    }
}
