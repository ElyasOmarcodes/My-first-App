package com.elyas.multiling

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/**
 * The one typeface of the app and the keyboard: Vazirmatn. Screens get it
 * from the theme; anything drawn or built in code (key labels, popups, the
 * suggestion strip, emoji tabs) asks here, so older Android versions that
 * ignore font resources in themes still show the same letters.
 */
object Fonts {
    @Volatile private var regular: Typeface? = null

    fun get(c: Context): Typeface {
        regular?.let { return it }
        val t = try {
            ResourcesCompat.getFont(c.applicationContext ?: c, R.font.vazirmatn)
        } catch (_: Exception) { null } ?: Typeface.DEFAULT
        regular = t
        return t
    }

    fun bold(c: Context): Typeface = Typeface.create(get(c), Typeface.BOLD)
}
