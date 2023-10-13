package com.sollian.ipc

import android.os.Bundle

interface IpcCallback {
    fun onIpcCallback(bundle: Bundle)
}