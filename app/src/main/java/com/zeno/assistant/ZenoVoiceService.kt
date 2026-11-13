package com.zeno.assistant

import android.app.AlarmClock
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
    private var session: ConversationSession? = null
    private val agentId = "agent_1001m784hww0ftstymy60zkrtx6b"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVoice()
            ACTION_SEND -> intent.getStringExtra(EXTRA_TEXT)?.let { session?.sendUserMessage(it) }
            ACTION_START -> startVoice()
        }
        return START_NOT_STICKY
    }

    private fun startVoice() {
        if (session != null) return
        val notification = buildNotification("ZENO ovozingizni tinglayapti")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        broadcastStatus("ZENO ulanmoqda...")
        val tools = mapOf(
            "open_app" to object : ClientTool {
                override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                    val app = parameters["app_name"]?.toString().orEmpty()
                    val result = openApp(app)
                    return if (result) ClientToolResult.success("$" + "{app} ilovasi ochildi") else ClientToolResult.failure("$" + "{app} ilovasi topilmadi")
                }
            },
            "set_alarm" to object : ClientTool {
                override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                    val hour = (parameters["hour"] as? Number)?.toInt() ?: 7
                    val minute = (parameters["minute"] as? Number)?.toInt() ?: 0
                    val label = parameters["label"]?.toString() ?: "ZENO eslatmasi"
                    val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(i)
                    return ClientToolResult.success("Budilnik " + hour + ":" + minute.toString().padStart(2, '0') + " ga sozlash oynasi ochildi")
                }
            },
            "set_timer" to object : ClientTool {
                override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                    val seconds = (parameters["seconds"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 600
                    val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "ZENO taymeri")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(i)
                    return ClientToolResult.success("Taymer ishga tushirish oynasi ochildi")
                }
            },
            "web_search" to object : ClientTool {
                override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                    val query = parameters["query"]?.toString().orEmpty()
                    if (query.isBlank()) return ClientToolResult.failure("Qidiruv matni bo'sh")
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                    return ClientToolResult.success("Internet qidiruvi ochildi: " + query)
                }
            },
            "open_settings" to object : ClientTool {
                override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                    val section = parameters["section"]?.toString()?.lowercase().orEmpty()
                    val action = when {
                        "wifi" in section || "internet" in section -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" in section -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" in section || "ekran" in section -> Settings.ACTION_DISPLAY_SETTINGS
                        "sound" in section || "ovoz" in section -> Settings.ACTION_SOUND_SETTINGS
                        "battery" in section || "batareya" in section -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return ClientToolResult.success("Sozlamalar ochildi")
                }
            }
        )
        val config = ConversationConfig(
            agentId = agentId,
            clientTools = tools,
            onConnect = {
                broadcastStatus("ZENO fon rejimida tinglayapti")
                updateNotification("ZENO faol — fon rejimida tinglayapti")
            },
            onDisconnect = {
                broadcastStatus("Ovozli aloqa uzildi")
                stopSelf()
            },
            onUserTranscriptEvent = { text, _ -> broadcastText("Siz", text) },
            onAgentResponseEvent = { text, _ -> broadcastText("ZENO", text) },
            onModeChange = { mode ->
                broadcastStatus(if (mode.toString().contains("SPEAKING")) "ZENO gapiryapti" else "ZENO tinglayapti")
            }
        )
        scope.launch {
            try {
                session = ConversationClient.startSession(config, this@ZenoVoiceService)
            } catch (e: Exception) {
                broadcastStatus("Ulanish xatosi: " + (e.localizedMessage ?: "noma'lum xato"))
                stopSelf()
            }
        }
    }

    private fun openApp(raw: String): Boolean {
        val key = raw.lowercase().trim()
        val candidates = when {
            "youtube" in key -> listOf("com.google.android.youtube")
            "telegram" in key -> listOf("org.telegram.messenger")
            "instagram" in key -> listOf("com.instagram.android")
            "chrome" in key || "browser" in key || "brauzer" in key -> listOf("com.android.chrome")
            "camera" in key || "kamera" in key -> listOf("com.android.camera", "com.google.android.GoogleCamera")
            "maps" in key || "xarita" in key -> listOf("com.google.android.apps.maps")
            "music" in key || "musiqa" in key -> listOf("com.google.android.apps.youtube.music")
            else -> emptyList()
        }
        for (pkg in candidates) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                return true
            }
        }
        return false
    }

    private fun stopVoice() {
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
        val active = session
        session = null
        scope.launch { active?.endSession() }
        super.onDestroy()
    }
}
