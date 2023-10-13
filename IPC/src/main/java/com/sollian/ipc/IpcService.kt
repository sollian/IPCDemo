package com.sollian.ipc

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class IpcService : Service() {

    companion object {
        private const val TAG = "IpcService"
        private val processHandlers = mutableMapOf<String, MutableList<IpcProcessHandler>>()

        @JvmStatic
        fun registerProcessHandler(processName: String, handler: IpcProcessHandler) {
            var handlerList: MutableList<IpcProcessHandler>? = processHandlers[processName]
            if (handlerList == null) {
                handlerList = mutableListOf()
                processHandlers[processName] = handlerList
            }
            if (!handlerList.contains(handler)) {
                handlerList.add(handler)
            }
        }

        @JvmStatic
        fun unregisterProcessHandler(processName: String, handler: IpcProcessHandler) {
            val handlerList: MutableList<IpcProcessHandler>? = processHandlers[processName]
            if (!handlerList.isNullOrEmpty()) {
                handlerList.remove(handler)
            }
        }
    }

    private val messenger: Messenger = Messenger(IncomingHandler(this))
    private val clientMessengers = ConcurrentHashMap<String, Messenger>()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "#onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "#onDestroy")
        clientMessengers.clear()
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "#onBind")
        return messenger.binder
    }

    private fun sendToClient(type: Int, data: Bundle = Bundle()) {
        Log.i(TAG, "#sendToClient: type=$type")
        val msg: Message = Message.obtain(null, type)
        var client: Messenger? = null
        val processName = data.getString(IpcConstant.PROCESS_NAME)
        if (processName != null && clientMessengers.get(processName) != null) {
            client = clientMessengers[processName]
        }
        msg.data = data
        if (client != null) {
            Log.i(TAG, "#sendToClient: send message to client")
            try {
                client.send(msg)
            } catch (e: RemoteException) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    class IncomingHandler constructor(ipcService: IpcService) : Handler(Looper.getMainLooper()) {
        private val serviceRef: WeakReference<IpcService> = WeakReference(ipcService)

        override fun handleMessage(msg: Message) {
            Log.i(TAG, "#handleMessage: begin")
            val ipcService = serviceRef.get() ?: return
            val data = msg.data ?: return
            data.classLoader = IpcClient::class.java.classLoader
            val processName = data.getString(IpcConstant.PROCESS_NAME)
            if (processName == null) {
                IpcUtil.throwOnDebug(TAG, NullPointerException("processName is null"))
                return
            }

            Log.i(TAG, "#handleMessage: processName=$processName")

            val handled = when (msg.what) {
                IpcConstant.MSG_REGISTER_CLIENT -> onRegisterClient(ipcService, msg)
                IpcConstant.MSG_UNREGISTER_CLIENT -> onUnregisterClient(ipcService, msg)
                else -> false
            }

            if (!handled) {
                Log.i(TAG, "#handleMessage: call custom message handler")
                val handlers = processHandlers[processName]
                if (handlers.isNullOrEmpty()) {
                    IpcUtil.throwOnDebug(TAG, NullPointerException("no process handler for process $processName found"))
                } else {
                    handlers.forEach { handler ->
                        val resultData = handler.handleMessage(msg)
                        if (resultData != null) {
                            ipcService.sendToClient(msg.what, resultData)
                            return@forEach
                        }
                    }
                }
            }
        }

        private fun onRegisterClient(ipcService: IpcService, msg: Message): Boolean {
            Log.i(TAG, "#onRegisterClient: begin")
            val data = msg.data!!
            val processName = data.getString(IpcConstant.PROCESS_NAME)!!
            if (ipcService.clientMessengers.containsKey(processName)) {
                IpcUtil.throwOnDebug(TAG, IllegalStateException("client cannot register twice"))
            } else {
                ipcService.clientMessengers[processName] = msg.replyTo
            }
            return true
        }

        private fun onUnregisterClient(ipcService: IpcService, msg: Message): Boolean {
            Log.i(TAG, "#onUnregisterClient: begin")
            val data = msg.data!!
            val processName = data.getString(IpcConstant.PROCESS_NAME)!!
            ipcService.clientMessengers.remove(processName)
            return true
        }
    }
}