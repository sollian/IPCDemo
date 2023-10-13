package com.sollian.ipc

import android.util.Log

internal object IpcUtil {
    var debug = true
    fun throwOnDebug(tag: String, e: Exception) {
        if (debug) {
            throw e
        } else {
            Log.e(tag, e.message, e)
        }
    }
}