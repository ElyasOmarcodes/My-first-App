package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestMedia =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Toast.makeText(
                this,
                if (granted) R.string.done else R.string.media_denied,
                Toast.LENGTH_SHORT
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgePadding()

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnMedia).setOnClickListener {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.READ_MEDIA_IMAGES
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.media_already, Toast.LENGTH_SHORT).show()
            } else {
                requestMedia.launch(perm)
            }
        }
    }
}
