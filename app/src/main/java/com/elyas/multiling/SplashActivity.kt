package com.elyas.multiling

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Brief opening screen on the app's own surface, in light or dark: the icon
 * settles in, name and tagline follow, an M3 progress bar runs underneath.
 * Short on purpose — a doorway, not a destination.
 */
class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(R.layout.activity_splash)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        findViewById<TextView>(R.id.splash_version).text = "v$version"

        val icon = findViewById<View>(R.id.splash_icon)
        icon.scaleX = 0.7f; icon.scaleY = 0.7f; icon.alpha = 0f
        icon.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(520).setInterpolator(OvershootInterpolator(1.2f)).start()
        for ((i, id) in listOf(R.id.splash_name, R.id.splash_sub, R.id.splash_progress).withIndex()) {
            Ui.rise(findViewById(id), i + 3)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed
            val next = if (IntroActivity.needed(this)) IntroActivity::class.java else MainActivity::class.java
            startActivity(Intent(this, next))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1100)
    }
}
