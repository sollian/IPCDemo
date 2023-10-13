package com.sollian.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import java.util.concurrent.atomic.AtomicInteger

class IpcClient {
    companion object {
        private const val TAG = "IpcClient"
        val INSTANCE: IpcClient = IpcClient()
    }

    private val messenger = Messenger(IncomingHandler(this))

    @Volatile
    private var serviceMessenger: Messenger? = null

    private lateinit var processName: String

    private var messageCacheBeforeInit = mutableListOf<Message>()
    private lateinit var application: Context

    //序列号生成器
    private val seqGenerator = AtomicInteger(0)
    private val callbacks = ConcurrentHashMap<Int, IpcCallback>()
    private var refCount = AtomicInteger(0)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "#onServiceConnected: name=$name")
            try {
                val messenger = Messenger(service)
                val msg: Message = Message.obtain(null, IpcConstant.MSG_REGISTER_CLIENT)
                msg.replyTo = this@IpcClient.messenger
                val bundle = Bundle()
                bundle.putString(IpcConstant.PROCESS_NAME, processName)
                msg.data = bundle
                messenger.send(msg)
                var messageQueue: List<Message>
                synchronized(this@IpcClient) {
                    serviceMessenger = messenger
                    messageQueue = ArrayList(messageCacheBeforeInit)
                    messageCacheBeforeInit.clear()
                }
                if (messageQueue.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "#onServiceConnected: exec messageQueue, messageQueue size=${messageQueue.size}"
                    )
                    for (message in messageQueue) {
                        messenger.send(message)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, ex.message, ex)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "#onServiceDisconnected: name=$name")
            serviceMessenger = null
            callbacks.clear()
        }
    }

    /**
     * @param context: 任意context，不会内存泄漏
     * @param processName: 每个进程必须保证processName唯一
     */
    fun doBindService(context: Context, processName: String) {
        Log.i(TAG, "#doBindService: processName=$processName")
        if (this::processName.isInitialized && processName != this.processName) {
            IpcUtil.throwOnDebug(
                TAG, IllegalStateException(
                    "name of process must keep unique,"
                            + "currentProcessName=${this.processName}, newProcessName=$processName}"
                )
            )
            return
        }

        this.processName = processName
        if (refCount.getAndAdd(1) == 0) {
            application = context.applicationContext
            application.bindService(
                Intent(application, IpcService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun doUnbindService() {
        Log.i(TAG, "#doUnbindService: processName=$processName")
        if (refCount.get() == 0) {
            return
        }
        if (refCount.addAndGet(-1) == 0) {
            // 解绑定Service
            val messenger = serviceMessenger
            if (messenger != null) {
                try {
                    val msg: Message = Message.obtain(null, IpcConstant.MSG_UNREGISTER_CLIENT)
                    msg.replyTo = messenger
                    val bundle = Bundle()
                    bundle.putString(IpcConstant.PROCESS_NAME, processName)
                    msg.data = bundle
                    messenger.send(msg)
                } catch (e: RemoteException) {
                    Log.e(TAG, e.message, e)
                }
            }

            Log.i(TAG, "#doUnbindService: do unbind")
            application.unbindService(serviceConnection)
            serviceMessenger = null

            // unbind了就把queue清空
            synchronized(this) {
                messageCacheBeforeInit.clear()
                callbacks.clear()
            }
        }
        if (refCount.get() < 0) {
            refCount.set(0)
        }
    }

    fun sendToService(type: Int, data: Bundle = Bundle(), callback: IpcCallback? = null) {
        if (!this::processName.isInitialized) {
            IpcUtil.throwOnDebug(TAG, IllegalStateException("you can only use ipc after doBindService method called"))
            return
        }

        Log.i(TAG, "#sendToService: type=$type, processName=$processName")

        if (callback != null) {
            val seqNum = addCallback(callback)
            data.putInt(IpcConstant.SEQ_NUMBER, seqNum)
        }
        data.putString(IpcConstant.PROCESS_NAME, processName)

        val message = Message.obtain(null, type)
        message.data = data
        message.replyTo = messenger

        val messenger = serviceMessenger
        if (messenger == null) {
            Log.i(TAG, "#sendToService: wait for service to connect")
            synchronized(this) {
                messageCacheBeforeInit.add(message)
            }
        } else {
            try {
                Log.i(TAG, "#sendToService: send message to service")
                messenger.send(message)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    private fun addCallback(callback: IpcCallback): Int {
        val seqNum = seqGenerator.addAndGet(1)
        if (seqGenerator.get() >= Int.MAX_VALUE) {
            seqGenerator.set(0)
        }
        callbacks[seqNum] = callback
        return seqNum
    }

    class IncomingHandler constructor(ipcClient: IpcClient) : Handler(Looper.getMainLooper()) {
        private var clienRef: WeakReference<IpcClient> = WeakReference(ipcClient)

        override fun handleMessage(msg: Message) {
            Log.i(TAG, "#handleMessage: begin")
            val ipcClient = clienRef.get() ?: return
            val data = msg.data ?: return

            data.classLoader = IpcClient::class.java.classLoader
            val seqNum = data.getInt(IpcConstant.SEQ_NUMBER, -1)

            if (seqNum != -1) {
                val callback = ipcClient.callbacks.remove(seqNum) ?: return
                Log.i(TAG, "#handleMessage: begin invoke callback $callback")
                callback.onIpcCallback(data)
            }

            Log.i(TAG, "#handleMessage: end")
        }
    }
}