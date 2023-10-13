package com.sollian.ipcdemo

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sollian.ipc.IpcCallback
import com.sollian.ipc.IpcClient

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        IpcClient.INSTANCE.doBindService(this, DebugIpcProcessHandler.PROCESS_NAME)

        findViewById<View>(R.id.button).setOnClickListener {
            val editor = findViewById<EditText>(R.id.edit_text)
            val message = editor.text.toString()

            IpcClient.INSTANCE.sendToService(DebugIpcProcessHandler.MSG_TEST_1,
                Bundle().apply {
                    putString(DebugIpcProcessHandler.KEY, message)
                },
                object : IpcCallback {
                    override fun onIpcCallback(bundle: Bundle) {
                        val textView = findViewById<TextView>(R.id.text)
                        textView.text = bundle.getString(DebugIpcProcessHandler.KEY, "error")
                    }
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IpcClient.INSTANCE.doUnbindService()
    }
}