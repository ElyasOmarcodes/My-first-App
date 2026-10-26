package com.elyas.multiling

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout

/** DEBUG ONLY: a full-screen text field that opens the keyboard at once, for
 *  CI screenshots of the keyboard (portrait and landscape). */
class KeyboardTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF3A6B4A.toInt())
        val et = EditText(this)
        et.hint = "Keyflux"
        et.setBackgroundColor(0xFFFFFFFF.toInt())
        root.addView(et, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        setContentView(root)
        et.requestFocus()
    }
}
