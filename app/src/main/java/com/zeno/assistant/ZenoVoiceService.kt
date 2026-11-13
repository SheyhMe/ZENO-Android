package com.zeno.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class ZenoVoiceService : Service() {
    companion object {
        const val ACTION_START = "com.zeno.assistant.START_VOICE"
        const val ACTION_STOP = "com.zeno.assistant.STOP_VOICE"
        const val ACTION_SEND = "com.zeno.assistant.SEND_MESSAGE"
        const val ACTION_STATUS = "com.zeno.assistant.VOICE_STATUS"
        const val ACTION_TRANSCRIPT = "com.zeno.assistant.VOICE_TRANSCRIPT"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "zeno_voice"
        private const val NOTIFICATION_ID = 701
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var session: ConversationSession? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeListening = false
    private var voiceTurnActive = false
    private var hasAgentResponded = false
    private var turnStarting = false
    private val agentId = "agent_1001m784hww0ftstymy60zkrtx6b"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVoice()
            ACTION_SEND -> intent.getStringExtra(EXTRA_TEXT)?.let { session?.sendUserMessage(it) }
            ACTION_START -> startWakeWordMode()
        }
        return START_NOT_STICKY
    }

    private fun startWakeWordMode() {
        if (wakeListening || session != null) return
        val notification = buildNotification("“Hey ZENO” deb chaqiring")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        broadcastStatus("“Hey ZENO” buyrug'ini kutyapman")
        startWakeRecognizer()
    }

    private fun startWakeRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            broadcastStatus("Bu telefonda ovoz tanish xizmati mavjud emas")
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                wakeListening = true
                broadcastStatus("ZENO “Hey ZENO” buyrug'ini tinglayapti")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                wakeListening = false
                restartWakeRecognizer()
            }
            override fun onResults(results: android.os.Bundle?) {
                wakeListening = false
                val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val heardWakeWord = phrases.any { phrase ->
                    val normalized = phrase.lowercase(Locale.ROOT)
                        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                        .replace(Regex("\\s+"), " ").trim()
                    normalized.contains("hey zeno") ||
                        normalized.contains("эй зено") ||
                        normalized.contains("hey zino") ||
                        normalized.contains("эй зино")
                }
                if (heardWakeWord) {
                    broadcastText("Siz", phrases.firstOrNull() ?: "Hey ZENO")
                    startAssistantTurn()
                } else restartWakeRecognizer()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (phrases.any { it.lowercase(Locale.ROOT).contains("hey zeno") || it.lowercase(Locale.ROOT).contains("hey zino") }) {
                    wakeListening = false
                    stopWakeRecognizer()
                    startAssistantTurn()
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        restartWakeRecognizer()
    }

    private fun restartWakeRecognizer() {
        if (session != null || turnStarting || !isForegroundServiceActive()) return
        if (speechRecognizer == null) {
            startWakeRecognizer()
            return
        }
        if (wakeListening) return
        wakeListening = true
        handler.postDelayed({
            if (session == null && !isFinishingService()) {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    }
                    speechRecognizer?.startListening(intent)
                } catch (_: Exception) {
                    wakeListening = false
                    handler.postDelayed({ restartWakeRecognizer() }, 1500)
                }
            }
        }, 500)
    }

    private var stopping = false
    private fun isForegroundServiceActive() = !stopping
    private fun isFinishingService() = stopping

    private fun stopWakeRecognizer() {
        wakeListening = false
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun startAssistantTurn() {
        if (session != null || turnStarting) return
        turnStarting = true
        stopWakeRecognizer()
        voiceTurnActive = true
        hasAgentResponded = false
        broadcastStatus("ZENO uyg'ondi — gapiring")
        val tools = mapOf(
            "open_youtube" to openTool("YouTube"),
            "open_telegram" to openTool("Telegram"),
            "open_instagram" to openTool("Instagram"),
            "open_chrome" to openTool("Chrome"),
            "open_camera" to openTool("Camera"),
            "open_maps" to openTool("Maps"),
            "open_app" to genericOpenTool(),
            "go_home" to homeTool(),
            "close_current_app" to homeTool(),
            "exit_app" to homeTool(),
            "open_wifi_settings" to settingsTool(Settings.ACTION_WIFI_SETTINGS),
            "open_bluetooth_settings" to settingsTool(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        val config = ConversationConfig(
            agentId = agentId,
            clientTools = tools,
            onConnect = {
                broadcastStatus("ZENO faol — buyruqni ayting")
                updateNotification("ZENO bilan gaplashing")
            },
            onDisconnect = {
                session = null
                voiceTurnActive = false
                if (!stopping) {
                    broadcastStatus("ZENO yana “Hey ZENO” buyrug'ini kutyapti")
                    updateNotification("“Hey ZENO” deb chaqiring")
                    restartWakeRecognizer()
                }
            },
            onUserTranscriptEvent = { text, _ -> broadcastText("Siz", text) },
            onAgentResponseEvent = { text, _ ->
                hasAgentResponded = true
                broadcastText("ZENO", text)
            },
            onModeChange = { mode ->
                if (mode.toString().contains("SPEAKING")) {
                    broadcastStatus("ZENO javob beryapti")
                } else if (mode.toString().contains("LISTENING") && hasAgentResponded) {
                    finishAssistantTurn()
                }
            }
        )
        scope.launch {
            try {
                session = ConversationClient.startSession(config, this@ZenoVoiceService)
                turnStarting = false
            } catch (e: Exception) {
                broadcastStatus("Ulanish xatosi: " + (e.localizedMessage ?: "noma'lum xato"))
                session = null
                turnStarting = false
                voiceTurnActive = false
                restartWakeRecognizer()
            }
        }
    }

    private fun finishAssistantTurn() {
        if (!voiceTurnActive || !hasAgentResponded) return
        voiceTurnActive = false
        hasAgentResponded = false
        val active = session
        session = null
        scope.launch { active?.endSession() }
        broadcastStatus("ZENO yana “Hey ZENO” buyrug'ini kutyapti")
        updateNotification("“Hey ZENO” deb chaqiring")
        restartWakeRecognizer()
    }

    private fun openTool(appName: String) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val opened = openApp(appName)
            return if (opened) ClientToolResult.success("$appName ochildi") else ClientToolResult.failure("$appName topilmadi")
        }
    }

    private fun genericOpenTool() = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val name = (parameters["app_name"] ?: parameters["name"] ?: "").toString()
            val opened = openApp(name)
            return if (opened) ClientToolResult.success("$name ochildi") else ClientToolResult.failure("$name ilovasi topilmadi")
        }
    }

    private fun homeTool() = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
            return ClientToolResult.success("Telefonning asosiy ekrani ochildi")
        }
    }

    private fun settingsTool(action: String) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return ClientToolResult.success("Sozlamalar ochildi")
        }
    }

    private fun openApp(raw: String): Boolean {
        val key = raw.lowercase(Locale.ROOT).trim()
        val known = when {
            "youtube" in key -> "com.google.android.youtube"
            "telegram" in key -> "org.telegram.messenger"
            "instagram" in key -> "com.instagram.android"
            "chrome" in key || "browser" in key || "brauzer" in key -> "com.android.chrome"
            "camera" in key || "kamera" in key -> "com.android.camera"
            "maps" in key || "xarita" in key -> "com.google.android.apps.maps"
            "music" in key || "musiqa" in key -> "com.google.android.apps.youtube.music"
            else -> null
        }
        val target = known?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: findLaunchIntentByLabel(key)
        if (target != null) {
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(target)
            return true
        }
        return false
    }

    private fun findLaunchIntentByLabel(appName: String): Intent? {
        if (appName.isBlank()) return null
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(query, 0)
        }
        val found = matches.firstOrNull {
            it.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(appName)
        } ?: return null
        return packageManager.getLaunchIntentForPackage(found.activityInfo.packageName)
    }

    private fun stopVoice() {
        stopping = true
        stopWakeRecognizer()
        val active = session
        session = null
        scope.launch { active?.endSession() }
        broadcastStatus("ZENO tinglashni to'xtatdi")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, ZenoVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.zeno_icon_foreground)
            .setContentTitle("ZENO")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "To'xtatish", stopIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "ZENO ovozli boshqaruv", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun broadcastStatus(value: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_TEXT, value))
    }

    private fun broadcastText(sender: String, value: String) {
        sendBroadcast(Intent(ACTION_TRANSCRIPT).setPackage(packageName).putExtra("sender", sender).putExtra(EXTRA_TEXT, value))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopping = true
        stopWakeRecognizer()
        handler.removeCallbacksAndMessages(null)
        val active = session
        session = null
        scope.launch { active?.endSession() }
        super.onDestroy()
    }
}
