package com.sollian.ipcdemo

import android.os.Bundle
import android.os.Message
import com.sollian.ipc.IpcProcessHandler

class DebugIpcProcessHandler : IpcProcessHandler {
    companion object {
        const val PROCESS_NAME = "debug"
        const val KEY = "key"

        /**
         * 开发中消息的值最好写到 [IpcConstant] 类中，防止重复‼️
         */
        const val MSG_TEST_1 = 100
    }

    override fun handleMessage(msg: Message): Bundle? {
        return when (msg.what) {
            MSG_TEST_1 -> {
                val data = msg.data
                data.putString(KEY, (data.getString(KEY) ?: "") + "---收到")
                data
            }

            else -> null
        }
    }
}