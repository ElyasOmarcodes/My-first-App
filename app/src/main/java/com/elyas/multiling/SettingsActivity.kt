package com.elyas.multiling

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
        supportActionBar?.setTitle(R.string.settings_title)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private var pendingLang = "ps"

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)

            findPreference<Preference>("autotext_manage")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AutoTextActivity::class.java))
                true
            }
            findPreference<Preference>("dict_import")?.setOnPreferenceClickListener {
                chooseLanguage { openDocument(REQ_DICT_IMPORT) }
                true
            }
            findPreference<Preference>("dict_export")?.setOnPreferenceClickListener {
                chooseLanguage { createDocument(REQ_DICT_EXPORT, "dict_$pendingLang.txt") }
                true
            }
            findPreference<Preference>("autotext_import")?.setOnPreferenceClickListener {
                openDocument(REQ_AUTOTEXT_IMPORT)
                true
            }
            findPreference<Preference>("autotext_export")?.setOnPreferenceClickListener {
                createDocument(REQ_AUTOTEXT_EXPORT, "autotext.txt")
                true
            }
            findPreference<Preference>("clear_learned")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pref_clear_learned)
                    .setMessage(R.string.clear_learned_q)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        for (l in Layouts.ALL) {
                            WordStore(requireContext(), l.code).clearLearned()
                        }
                        toast(getString(R.string.done))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }

        private fun chooseLanguage(then: () -> Unit) {
            val names = Layouts.ALL.map { it.nativeName }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_languages)
                .setItems(names) { _, which ->
                    pendingLang = Layouts.ALL[which].code
                    then()
                }
                .show()
        }

        @Suppress("DEPRECATION")
        private fun openDocument(requestCode: Int) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "text/*"
                startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        @Suppress("DEPRECATION")
        private fun createDocument(requestCode: Int, name: String) {
            try {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TITLE, name)
                startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode != Activity.RESULT_OK) return
            val uri = data?.data ?: return
            val ctx = requireContext()
            try {
                when (requestCode) {
                    REQ_DICT_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = WordStore(ctx, pendingLang).importText(text)
                        toast(getString(R.string.imported_n_words, n))
                    }
                    REQ_DICT_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(WordStore(ctx, pendingLang).exportText())
                        }
                        toast(getString(R.string.done))
                    }
                    REQ_AUTOTEXT_IMPORT -> {
                        val text = ctx.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: return
                        val n = AutoTextStore(ctx).importText(text)
                        toast(getString(R.string.imported_n_words, n))
                    }
                    REQ_AUTOTEXT_EXPORT -> {
                        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(AutoTextStore(ctx).exportText())
                        }
                        toast(getString(R.string.done))
                    }
                }
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }

        private fun toast(msg: String) {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        companion object {
            private const val REQ_DICT_IMPORT = 11
            private const val REQ_DICT_EXPORT = 12
            private const val REQ_AUTOTEXT_IMPORT = 13
            private const val REQ_AUTOTEXT_EXPORT = 14
        }
    }
}
