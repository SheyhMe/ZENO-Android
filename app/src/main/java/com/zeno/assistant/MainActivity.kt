package com.zeno.assistant

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var chatLog: TextView
    private lateinit var messageInput: EditText
    private var voiceActive = false
    private val permissionRequest = 102

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ZenoVoiceService.ACTION_STATUS -> {
                    status.text = intent.getStringExtra(ZenoVoiceService.EXTRA_TEXT) ?: "ZENO"
                    voiceActive = !status.text.toString().contains("to'xtatdi") &&
                        !status.text.toString().contains("uzildi") &&
                        !status.text.toString().contains("xatosi")
                    findViewById<Button>(R.id.listenButton).text =
                        if (voiceActive) "TINGLASHNI TO'XTATISH" else "FONDA TINGLASHNI YOQISH"
                }
                ZenoVoiceService.ACTION_TRANSCRIPT -> {
                    appendChat(intent.getStringExtra("sender") ?: "ZENO", intent.getStringExtra(ZenoVoiceService.EXTRA_TEXT) ?: "")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)

        findViewById<Button>(R.id.listenButton).setOnClickListener { toggleBackgroundVoice() }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.appsButton).setOnClickListener { fill("YouTube ilovasini och") }
        findViewById<Button>(R.id.searchButton).setOnClickListener { fill("Internetdan qidir: ") }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { fill("Ertalab soat 07:00 ga budilnik qo'y") }
        findViewById<Button>(R.id.timerButton).setOnClickListener { fill("10 daqiqaga taymer qo'y") }
        appendChat("ZENO", "Assalomu alaykum. Ovozli suhbatni boshlang. Ekran o'chganda ham faol suhbat davom etadi.")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ZenoVoiceService.ACTION_STATUS)
            addAction(ZenoVoiceService.ACTION_TRANSCRIPT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(voiceReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(voiceReceiver, filter)
    }

    override fun onStop() {
        try { unregisterReceiver(voiceReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun toggleBackgroundVoice() {
        if (voiceActive) {
            startService(Intent(this, ZenoVoiceService::class.java).setAction(ZenoVoiceService.ACTION_STOP))
            voiceActive = false
            status.text = "Tinglash to'xtatilmoqda..."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest)
            return
        }
        val intent = Intent(this, ZenoVoiceService::class.java).setAction(ZenoVoiceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "ZENO ulanmoqda..."
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return
        appendChat("Siz", message)
        messageInput.text.clear()
        if (!voiceActive) {
            Toast.makeText(this, "Avval fon ovozli boshqaruvini yoqing, so'ng buyruq yuboring.", Toast.LENGTH_LONG).show()
            return
        }
        startService(Intent(this, ZenoVoiceService::class.java)
            .setAction(ZenoVoiceService.ACTION_SEND)
            .putExtra(ZenoVoiceService.EXTRA_TEXT, message))
    }

    private fun fill(value: String) {
        messageInput.setText(value)
        messageInput.setSelection(messageInput.text.length)
    }

    private fun appendChat(sender: String, message: String) {
        if (message.isBlank()) return
        val current = chatLog.text.toString()
        chatLog.text = (if (current.isBlank()) "" else "$current\n\n") + "$sender\n$message"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            toggleBackgroundVoice()
        } else if (requestCode == permissionRequest) {
            status.text = "Mikrofon ruxsati kerak"
            Toast.makeText(this, "Fon rejimida ovozli boshqaruv uchun mikrofon ruxsati kerak.", Toast.LENGTH_LONG).show()
        }
    }
}
