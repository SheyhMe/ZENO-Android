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
import android.view.View
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import android.view.inputmethod.EditorInfo
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
    private var voiceActive = false
    private var textSession: ConversationSession? = null
    private var textConnecting = false
    private val pendingTextMessages = ArrayDeque<String>()
    private val textScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val agentId = "agent_1001m784hww0ftstymy60zkrtx6b"
    private var orbPulse: ObjectAnimator? = null
    private val permissionRequest = 102
    private val historyPrefs by lazy { getSharedPreferences("zeno_history", MODE_PRIVATE) }

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ZenoVoiceService.ACTION_STATUS -> {
                    status.text = intent.getStringExtra(ZenoVoiceService.EXTRA_TEXT) ?: "ZENO"
                    voiceActive = !status.text.toString().contains("to'xtatdi") &&
                        !status.text.toString().contains("uzildi") &&
                        !status.text.toString().contains("xatosi")
                    findViewById<Button>(R.id.listenButton).text =
                        if (voiceActive) "HEY ZENO — O'CHIRISH" else "HEY ZENO — YOQISH"
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
        findViewById<TextView>(R.id.cyberContent).text = CyberSecurityKnowledge.overview
        val orb = findViewById<View>(R.id.orbContainer)
        orbPulse = ObjectAnimator.ofPropertyValuesHolder(
            orb,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f)
        ).apply {
            duration = 2200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)
        val appSearchInput = findViewById<EditText>(R.id.appSearchInput)
        appSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = appSearchInput.text.toString().trim()
                if (query.isNotBlank()) {
                    val result = LocalCommandRouter.execute(this, "och " + query)
                    if (result != null) Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                }
                true
            } else false
        }
        val chatScroll = findViewById<ScrollView>(R.id.chatScroll)
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) chatScroll.postDelayed({
                chatScroll.smoothScrollTo(0, chatScroll.getChildAt(0).height)
            }, 180)
        }
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        findViewById<Button>(R.id.listenButton).setOnClickListener { toggleBackgroundVoice() }
        findViewById<Button>(R.id.homeVoiceButton).setOnClickListener { showPage(R.id.voicePage) }
        findViewById<View>(R.id.homeChatTile).setOnClickListener { showPage(R.id.chatPage); messageInput.requestFocus() }
        findViewById<View>(R.id.homeAppsTile).setOnClickListener { showPage(R.id.appsPage) }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.appsButton).setOnClickListener { runQuickCommand("och YouTube") }
        findViewById<Button>(R.id.searchButton).setOnClickListener { runQuickCommand("och Chrome") }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { runQuickCommand("budilnik 07:00") }
        findViewById<Button>(R.id.timerButton).setOnClickListener { runQuickCommand("10 daqiqaga taymer qo‘y") }
        findViewById<Button>(R.id.telegramButton).setOnClickListener { runQuickCommand("och Telegram") }
        findViewById<Button>(R.id.instagramButton).setOnClickListener { runQuickCommand("och Instagram") }
        findViewById<Button>(R.id.chromeButton).setOnClickListener { runQuickCommand("och Chrome") }
        findViewById<Button>(R.id.cameraButton).setOnClickListener { runQuickCommand("och Kamera") }

        findViewById<Button>(R.id.navHome).setOnClickListener { showPage(R.id.homePage) }
        findViewById<Button>(R.id.navChat).setOnClickListener { showPage(R.id.chatPage) }
        findViewById<Button>(R.id.navApps).setOnClickListener { showPage(R.id.appsPage) }
        findViewById<Button>(R.id.navVoice).setOnClickListener { showPage(R.id.voicePage) }
        findViewById<Button>(R.id.navProfile).setOnClickListener { showPage(R.id.profilePage) }
        findViewById<Button>(R.id.cyberButton).setOnClickListener { showPage(R.id.cyberPage) }
        findViewById<Button>(R.id.cyberAskButton).setOnClickListener { fillAndOpenChat("Menga kiberxavfsizlikni noldan o‘rgat. Birinchi darsni boshlaymiz.") }


        val savedHistory = historyPrefs.getString("chat", "").orEmpty()
        if (savedHistory.isNotBlank()) chatLog.text = savedHistory else appendChat("ZENO", "Assalomu alaykum. Men ZENOman. Sizga qanday yordam bera olaman?")
    }


    private fun showPage(pageId: Int) {
        val pages = listOf(R.id.homePage, R.id.chatPage, R.id.appsPage, R.id.voicePage, R.id.profilePage, R.id.cyberPage)
        pages.forEach { findViewById<View>(it).visibility = if (it == pageId) View.VISIBLE else View.GONE }
    }

    private fun fillAndOpenChat(value: String) {
        fill(value)
        showPage(R.id.chatPage)
    }

    private fun runQuickCommand(command: String) {
        val result = LocalCommandRouter.execute(this, command)
        if (result != null) {
            appendChat("ZENO", result)
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        } else {
            appendChat("ZENO", "Buyruqni bajarib bo‘lmadi: " + command)
        }
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
            status.text = "Hey ZENO tinglashi to'xtatilmoqda..."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest)
            return
        }
        val intent = Intent(this, ZenoVoiceService::class.java).setAction(ZenoVoiceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "“Hey ZENO” faollashtirilmoqda..."
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return
        appendChat("Siz", message)
        messageInput.text.clear()
        val localReply = LocalCommandRouter.execute(this, message)
        if (localReply != null) {
            appendChat("ZENO", localReply)
            return
        }
        val cyberReply = CyberSecurityKnowledge.answer(message)
        if (cyberReply != null) {
            appendChat("ZENO • Kiberxavfsizlik", cyberReply)
            return
        }
        if (voiceActive) {
            startService(Intent(this, ZenoVoiceService::class.java)
                .setAction(ZenoVoiceService.ACTION_SEND)
                .putExtra(ZenoVoiceService.EXTRA_TEXT, message))
        } else {
            sendTextMessage(message)
        }
    }

    private fun sendTextMessage(message: String) {
        val active = textSession
        if (active != null) {
            active.sendUserMessage(message)
            return
        }
        pendingTextMessages.addLast(message)
        if (textConnecting) return
        textConnecting = true
        status.text = "ZENO matnli rejimga ulanmoqda..."
        textScope.launch {
            try {
                val config = ConversationConfig(
                    agentId = agentId,
                    textOnly = true,
                    onConnect = {
                        runOnUiThread { status.text = "ZENO matnli rejimda tayyor" }
                    },
                    onAgentResponseEvent = { reply, _ ->
                        runOnUiThread { appendChat("ZENO", reply) }
                    },
                    onDisconnect = {
                        textSession = null
                        textConnecting = false
                        runOnUiThread { status.text = "Matnli aloqa uzildi" }
                    }
                )
                textSession = ConversationClient.startSession(config, this@MainActivity)
                textConnecting = false
                while (pendingTextMessages.isNotEmpty()) {
                    textSession?.sendUserMessage(pendingTextMessages.removeFirst())
                }
            } catch (e: Exception) {
                textConnecting = false
                pendingTextMessages.clear()
                runOnUiThread {
                    appendChat("ZENO", "Ulanishda xatolik: ${e.localizedMessage ?: "noma'lum xato"}")
                    status.text = "Matnli aloqa xatosi"
                }
            }
        }
    }

    private fun fill(value: String) {
        messageInput.setText(value)
        messageInput.setSelection(messageInput.text.length)
    }

    private fun appendChat(sender: String, message: String) {
        if (message.isBlank()) return
        val current = chatLog.text.toString()
        val updated = (if (current.isBlank()) "" else "$current\n\n") + "$sender\n$message"
        chatLog.text = if (updated.length > 12000) updated.takeLast(12000) else updated
        historyPrefs.edit().putString("chat", chatLog.text.toString()).apply()
    }

    override fun onDestroy() {
        orbPulse?.cancel()
        textScope.launch { textSession?.endSession(); textSession = null }
        orbPulse = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            toggleBackgroundVoice()
        } else if (requestCode == permissionRequest) {
            status.text = "Mikrofon ruxsati kerak"
            Toast.makeText(this, "Ovozli boshqaruv uchun mikrofon ruxsati kerak.", Toast.LENGTH_LONG).show()
        }
    }
}