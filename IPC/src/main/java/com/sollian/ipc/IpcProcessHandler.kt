package com.sollian.ipc

import android.os.Bundle
import android.os.Message

interface IpcProcessHandler {
    /**
     * 返回值不是null时，会将结果回传给client端
     */
    fun handleMessage(msg: Message): Bundle?
}