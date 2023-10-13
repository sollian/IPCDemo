package com.sollian.ipcdemo

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sollian.ipc.IpcService

class DebugIpcHelper : LifecycleObserver {
    private val processHandler = DebugIpcProcessHandler()

    fun init(activity: FragmentActivity) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                IpcService.registerProcessHandler(DebugIpcProcessHandler.PROCESS_NAME, processHandler)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                IpcService.unregisterProcessHandler(DebugIpcProcessHandler.PROCESS_NAME, processHandler)
            }
        })
    }
}