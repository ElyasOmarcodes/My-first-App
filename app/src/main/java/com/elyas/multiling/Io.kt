package com.elyas.multiling

import java.util.concurrent.Executors

/**
 * Single background writer thread for all store files. Saving clipboard
 * history (up to ~2 MB of text) or dictionaries on the UI thread froze the
 * keyboard for seconds after copy/select-all/paste — every save now snapshots
 * its data on the caller's thread and performs the actual disk write here.
 * One thread keeps writes to the same file ordered.
 */
internal object Io {
    val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "multiling-io").apply { isDaemon = true }
    }
}
