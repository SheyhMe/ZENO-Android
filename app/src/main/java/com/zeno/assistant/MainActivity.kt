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
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    private var aiInput = JSONArray()

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
                ZenoVoiceService.ACTION_USER_COMMAND -> {
                    val spoken = intent.getStringExtra(ZenoVoiceService.EXTRA_TEXT).orEmpty().trim()
                    if (spoken.isNotBlank()) {
                        messageInput.setText(spoken)
                        sendMessage()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Remove legacy account tokens left by older ZENO builds; this edition is local-only.
        getSharedPreferences("zeno_cloud_session", MODE_PRIVATE).edit().clear().apply()
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
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.cyberButton).setOnClickListener { showPage(R.id.cyberPage) }
        findViewById<Button>(R.id.cyberAskButton).setOnClickListener { fillAndOpenChat("Menga kiberxavfsizlikni noldan o‘rgat. Birinchi darsni boshlaymiz.") }


        val savedContext = historyPrefs.getString("ai_context", "[]").orEmpty()
        try {
            val restored = JSONArray(savedContext)
            for (i in 0 until restored.length()) aiInput.put(restored.getJSONObject(i))
        } catch (_: Exception) { aiInput = JSONArray() }

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
            addAction(ZenoVoiceService.ACTION_USER_COMMAND)
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
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "“Hey ZENO” faollashtirilmoqda..."
        } catch (e: Exception) {
            status.text = "Ovozli boshqaruvni yoqib bo‘lmadi: " + (e.message ?: e.javaClass.simpleName)
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return
        appendChat("Siz", message)
        messageInput.text.clear()

        // Deterministic commands stay local; natural-language questions go to the AI.
        val localReply = LocalCommandRouter.execute(this, message)
        if (localReply != null) {
            appendChat("ZENO", localReply)
            return
        }

        aiInput.put(JSONObject().put("role", "user").put("content", message))
        saveAiContext()
        requestAiResponse(0)
    }

    private fun saveAiContext() {
        val trimmed = JSONArray()
        val start = (aiInput.length() - 24).coerceAtLeast(0)
        for (i in start until aiInput.length()) trimmed.put(aiInput.get(i))
        aiInput = trimmed
        historyPrefs.edit().putString("ai_context", aiInput.toString()).apply()
    }

    private fun requestAiResponse(toolDepth: Int) {
        status.text = "ZENO fikrlayapti..."
        textScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ZenoAiClient.send(aiInput) }
                val output = response.optJSONArray("output") ?: JSONArray()
                val calls = mutableListOf<JSONObject>()
                for (i in 0 until output.length()) {
                    val item = output.optJSONObject(i) ?: continue
                    aiInput.put(item)
                    if (item.optString("type") == "function_call") calls.add(item)
                }

                if (calls.isNotEmpty() && toolDepth < 4) {
                    for (call in calls) {
                        val name = call.optString("name")
                        val callId = call.optString("call_id")
                        val args = try { JSONObject(call.optString("arguments", "{}")) } catch (_: Exception) { JSONObject() }
                        val result = executeAiTool(name, args)
                        aiInput.put(JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", callId)
                            .put("output", result))
                    }
                    saveAiContext()
                    requestAiResponse(toolDepth + 1)
                    return@launch
                }

                saveAiContext()
                val answer = extractAiText(output)
                if (answer.isNotBlank()) appendChat("ZENO", answer)
                else appendChat("ZENO", if (calls.isNotEmpty()) "Amal bajarildi, lekin javob matni kelmadi." else "Javob bo‘sh qaytdi. Qayta urinib ko‘ring.")
                status.text = "ZENO tayyor"
            } catch (error: Exception) {
                val message = error.localizedMessage ?: "Aloqa xatosi"
                appendChat("ZENO", "AI ulanishida xatolik: $message")
                status.text = "AI ulanish xatosi"
            }
        }
    }

    private fun extractAiText(output: JSONArray): String {
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") {
                    val text = part.optString("text")
                    if (text.isNotBlank()) parts.add(text)
                }
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun executeAiTool(name: String, args: JSONObject): String {
        val command = when (name) {
            "open_app" -> "och " + args.optString("app_name")
            "go_home" -> "bosh ekran"
            "go_back" -> "orqaga"
            "show_notifications" -> "bildirishnomalarni och"
            "show_quick_settings" -> "tez sozlamalarni och"
            "scroll_screen" -> if (args.optString("direction") == "up") "yuqoriga aylantir" else "pastga aylantir"
            "tap_visible_text" -> "bos " + args.optString("text")
            "type_text" -> "yoz " + args.optString("text")
            "search_google" -> "Internetdan qidir " + args.optString("query")
            "set_alarm" -> "budilnik %02d:%02d".format(args.optInt("hour"), args.optInt("minute"))
            "set_timer" -> "taymer ${args.optInt("duration_seconds", 600)} soniya"
            "open_wifi_settings" -> "wifi"
            "open_bluetooth_settings" -> "bluetooth"
            "open_maps" -> "xaritada: " + args.optString("destination")
            "open_dialer" -> "qo'ng'iroq " + args.optString("phone_number")
            "compose_sms" -> "sms " + args.optString("phone_number") + ": " + args.optString("message")
            "open_calendar" -> "kalendar"
            else -> return "Bu amal ZENO tomonidan qo‘llab-quvvatlanmaydi."
        }
        return LocalCommandRouter.execute(this, command) ?: "Buyruqni bajarish uchun mos Android amali topilmadi."
    }

    private fun createTextClientTools(): Map<String, ClientTool> {
        fun tool(action: (Map<String, Any>) -> String?): ClientTool = object : ClientTool {
            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                return try {
                    val result = action(parameters)
                    if (result.isNullOrBlank()) ClientToolResult.failure("Buyruqni bajarib bo‘lmadi")
                    else ClientToolResult.success(result)
                } catch (error: Exception) {
                    ClientToolResult.failure(error.localizedMessage ?: "Android amali bajarilmadi")
                }
            }
        }
        fun local(command: String) = LocalCommandRouter.execute(this@MainActivity, command)
        fun launch(name: String) = tool { local("och " + name) }

        val tools = mutableMapOf<String, ClientTool>()
        tools["open_youtube"] = launch("YouTube")
        tools["open_telegram"] = launch("Telegram")
        tools["open_instagram"] = launch("Instagram")
        tools["open_chrome"] = launch("Chrome")
        tools["open_camera"] = launch("Kamera")
        tools["open_app"] = tool { p -> local("och " + (p["app_name"] ?: p["name"] ?: "").toString()) }
        tools["open_maps"] = tool {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Google Maps ochildi."
        }
        tools["open_maps_search"] = tool { p ->
            local("xaritada " + (p["query"] ?: "").toString())
        }
        tools["open_wifi_settings"] = tool { local("wifi") }
        tools["open_bluetooth_settings"] = tool { local("bluetooth") }
        tools["search_web"] = tool { p ->
            local("Internetdan qidir: " + (p["query"] ?: "").toString())
        }
        tools["set_alarm"] = tool { p ->
            val hour = (p["hour"]?.toString()?.toIntOrNull() ?: 7).coerceIn(0, 23)
            val minute = (p["minute"]?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 59)
            local("budilnik %02d:%02d".format(hour, minute))
        }
        tools["set_timer"] = tool { p ->
            val seconds = (p["duration_seconds"]?.toString()?.toIntOrNull() ?: 600).coerceIn(1, 86400)
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "ZENO taymer")
                .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Taymer sozlash oynasi ochildi; tasdiqlashni Clock ilovasida bajaring."
        }
        tools["open_dialer"] = tool { p ->
            local("qo'ng'iroq " + (p["phone_number"] ?: p["number"] ?: "").toString())
        }
        tools["compose_sms"] = tool { p ->
            val number = (p["phone_number"] ?: p["number"] ?: "").toString()
            val body = (p["message"] ?: "").toString()
            local("sms " + number + ": " + body)
        }
        tools["open_calendar"] = tool { local("kalendar") }
        tools["go_home"] = tool {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Telefonning asosiy ekrani ochildi."
        }
        tools["close_current_app"] = tools["go_home"]!!
        return tools
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
                    clientTools = createTextClientTools(),
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