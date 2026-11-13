package com.zeno.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var chatLog: TextView
    private lateinit var messageInput: EditText
    private var session: ConversationSession? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingMessage: String? = null
    private val permissionRequest = 102
    private val agentId = "agent_1001m784hww0ftstymy60zkrtx6b"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)

        findViewById<Button>(R.id.listenButton).setOnClickListener { toggleVoiceSession() }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.appsButton).setOnClickListener { fill("YouTube ilovasini och") }
        findViewById<Button>(R.id.searchButton).setOnClickListener { fill("Internetdan qidir: ") }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { fill("Ertalab soat 07:00 ga budilnik qo‘y") }
        findViewById<Button>(R.id.timerButton).setOnClickListener { fill("10 daqiqaga taymer qo‘y") }
        appendChat("ZENO", "Assalomu alaykum. Men ZENOman. O‘zbekcha yoki inglizcha yozing yoki ovozli suhbatni boshlang.")
    }

    private fun fill(value: String) {
        messageInput.setText(value)
        messageInput.setSelection(messageInput.text.length)
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return
        appendChat("Siz", message)
        messageInput.text.clear()
        val active = session
        if (active != null) {
            active.sendUserMessage(message)
        } else {
            pendingMessage = message
            startVoiceSession()
        }
    }

    private fun toggleVoiceSession() {
        if (session != null) {
            val active = session
            session = null
            mainScope.launch { active?.endSession() }
            status.text = "Ulandi uzildi"
            findViewById<Button>(R.id.listenButton).text = "OVOZLI SUHBAT"
        } else {
            startVoiceSession()
        }
    }

    private fun startVoiceSession() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest)
            return
        }
        if (session != null) return

        status.text = "ZENO ulanmoqda..."
        findViewById<Button>(R.id.listenButton).text = "ULANMOQDA..."
        try {
            val config = ConversationConfig(
                agentId = agentId,
                onConnect = { _ ->
                    runOnUiThread {
                        status.text = "ZENO tinglayapti"
                        findViewById<Button>(R.id.listenButton).text = "SUHBATNI TUGATISH"
                        pendingMessage?.let {
                            session?.sendUserMessage(it)
                            pendingMessage = null
                        }
                    }
                },
                onDisconnect = { reason ->
                    runOnUiThread {
                        session = null
                        status.text = "Aloqa uzildi"
                        findViewById<Button>(R.id.listenButton).text = "OVOZLI SUHBAT"
                        if (reason.toString().contains("Error", ignoreCase = true)) {
                            Toast.makeText(this, "Ulanishda xatolik. Internetni tekshiring.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onUserTranscriptEvent = { text, _ ->
                    runOnUiThread { if (text.isNotBlank()) appendChat("Siz (ovoz)", text) }
                },
                onAgentResponseEvent = { text, _ ->
                    runOnUiThread {
                        if (text.isNotBlank()) {
                            appendChat("ZENO", text)
                            status.text = "ZENO tinglayapti"
                        }
                    }
                },
                onModeChange = { mode ->
                    runOnUiThread { status.text = if (mode.toString().contains("SPEAKING")) "ZENO gapiryapti" else "ZENO tinglayapti" }
                }
            )
            mainScope.launch {
                try {
                    session = ConversationClient.startSession(config, this@MainActivity)
                } catch (e: Exception) {
                    session = null
                    status.text = "Ulanish amalga oshmadi"
                    findViewById<Button>(R.id.listenButton).text = "QAYTA URINISH"
                    Toast.makeText(this@MainActivity, e.localizedMessage ?: "ZENO serveriga ulanib bo‘lmadi", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            session = null
            status.text = "Ulanish amalga oshmadi"
            findViewById<Button>(R.id.listenButton).text = "QAYTA URINISH"
            Toast.makeText(this, e.localizedMessage ?: "ZENO serveriga ulanib bo‘lmadi", Toast.LENGTH_LONG).show()
        }
    }

    private fun appendChat(sender: String, message: String) {
        val current = chatLog.text.toString()
        chatLog.text = (if (current.isBlank()) "" else "$current\n\n") + "$sender\n$message"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceSession()
        } else if (requestCode == permissionRequest) {
            status.text = "Mikrofon ruxsati kerak"
            Toast.makeText(this, "Ovozli suhbat uchun mikrofon ruxsatini bering.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        val active = session
        session = null
        mainScope.launch { active?.endSession() }
        super.onDestroy()
    }
}
