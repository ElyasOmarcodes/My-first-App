package com.elyas.multiling

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Watches MediaStore for freshly captured screenshots and reports their
 * content URI, like Samsung keyboard's screenshot-to-clipboard. Fully
 * on-device: only reads the newest image row and only if it looks like a
 * screenshot taken in the last few seconds. Needs media-read permission;
 * without it the queries return nothing and this simply stays silent.
 */
class ScreenshotObserver(
    private val context: Context,
    private val onScreenshot: (Uri) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastHandledId = -1L

    fun register() {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, this
            )
        } catch (_: Exception) {
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
        } catch (_: Exception) {
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        checkNewest()
    }

    private fun checkNewest() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.RELATIVE_PATH
            else MediaStore.Images.Media.DATA
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
            )?.use { c ->
                if (!c.moveToFirst()) return
                val id = c.getLong(0)
                if (id == lastHandledId) return
                val name = c.getString(1) ?: ""
                val addedSec = c.getLong(2)
                val pathCol = c.getString(3) ?: ""
                val looksLikeShot =
                    name.contains("screenshot", true) ||
                    pathCol.contains("screenshot", true) ||
                    pathCol.contains("screencapture", true)
                val recent = (System.currentTimeMillis() / 1000 - addedSec) < 8
                if (looksLikeShot && recent) {
                    lastHandledId = id
                    val itemUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )
                    onScreenshot(itemUri)
                }
            }
        } catch (_: Exception) {
        }
    }
}
