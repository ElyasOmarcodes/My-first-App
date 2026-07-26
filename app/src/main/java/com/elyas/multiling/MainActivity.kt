package com.elyas.multiling

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge so the glass gradient fills behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false // dark backdrop -> light icons
        setContentView(R.layout.activity_main)

        // pad only the scrolling content for the bars; the gradient stays full-bleed
        val scroll = findViewById<View>(R.id.homeScroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

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

        startOrgTyping()
    }

    // ------------------------------------------------- typing animation
    private val typeHandler = Handler(Looper.getMainLooper())
    private var typeRunnable: Runnable? = null

    /**
     * Types the organisation intro character by character; once the whole
     * text is shown it waits one minute and starts over.
     */
    private fun startOrgTyping() {
        val tv = findViewById<TextView>(R.id.orgIntro) ?: return
        val full = getString(R.string.org_intro)
        var i = 0

        val step = object : Runnable {
            override fun run() {
                if (i <= full.length) {
                    tv.text = full.substring(0, i)
                    i++
                    typeHandler.postDelayed(this, 22L)
                } else {
                    // full text shown — restart after a one-minute pause
                    typeHandler.postDelayed({
                        i = 0
                        typeHandler.post(this)
                    }, 60_000L)
                }
            }
        }
        typeRunnable = step
        typeHandler.post(step)
    }

    override fun onDestroy() {
        typeRunnable?.let { typeHandler.removeCallbacks(it) }
        typeHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
