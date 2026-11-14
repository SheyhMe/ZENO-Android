package com.zeno.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import java.util.Locale

/**
 * Permission-gated Android actions exposed to the ElevenLabs agent in text mode.
 * Every action opens a user-visible Android activity; this class does not perform
 * hidden device changes.
 */
object AndroidActionTools {
    fun create(context: Context): Map<String, ClientTool> {
        val app = context.applicationContext
        return mapOf(
            "open_youtube" to openApp(app, "YouTube", "com.google.android.youtube"),
            "open_telegram" to openApp(app, "Telegram", "org.telegram.messenger"),
            "open_instagram" to openApp(app, "Instagram", "com.instagram.android"),
            "open_chrome" to openApp(app, "Chrome", "com.android.chrome"),
            "open_camera" to openApp(app, "Camera", "com.android.camera"),
            "open_maps" to openApp(app, "Maps", "com.google.android.apps.maps"),
            "open_app" to genericOpenApp(app),
            "go_home" to openHome(app),
            "close_current_app" to openHome(app),
            "exit_app" to openHome(app),
            "open_wifi_settings" to openSettings(app, Settings.ACTION_WIFI_SETTINGS),
            "open_bluetooth_settings" to openSettings(app, Settings.ACTION_BLUETOOTH_SETTINGS),
            "set_alarm" to setAlarm(app),
            "set_timer" to setTimer(app),
            "search_web" to searchGoogle(app),
            "open_maps_search" to searchMaps(app)
        )
    }

    private fun openApp(context: Context, label: String, packageName: String) =
        object : ClientTool {
            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: findByLabel(context, label)
                    ?: return ClientToolResult.failure("$label ilovasi topilmadi")
                return launch(context, intent, "$label ochildi")
            }
        }

    private fun genericOpenApp(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val name = (parameters["app_name"] ?: parameters["name"] ?: "").toString().trim()
            if (name.isBlank()) return ClientToolResult.failure("Ilova nomini ayting")
            val intent = findByLabel(context, name)
                ?: return ClientToolResult.failure("$name ilovasi topilmadi")
            return launch(context, intent, "$name ochildi")
        }
    }

    private fun findByLabel(context: Context, name: String): Intent? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                query, android.content.pm.PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(query, 0)
        }
        val found = matches.firstOrNull {
            it.loadLabel(context.packageManager).toString()
                .lowercase(Locale.ROOT).contains(name.lowercase(Locale.ROOT))
        } ?: return null
        return context.packageManager.getLaunchIntentForPackage(found.activityInfo.packageName)
    }

    private fun openHome(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return launch(context, intent, "Telefon bosh ekrani ochildi")
        }
    }

    private fun openSettings(context: Context, action: String) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? =
            launch(context, Intent(action), "Sozlamalar ochildi")
    }

    private fun setAlarm(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val hour = number(parameters["hour"]) ?: return ClientToolResult.failure("Soatni kiriting")
            val minute = number(parameters["minute"]) ?: 0
            if (hour !in 0..23 || minute !in 0..59) {
                return ClientToolResult.failure("Vaqt noto‘g‘ri. Soat 0–23, daqiqa 0–59 bo‘lishi kerak")
            }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, parameters["label"]?.toString() ?: "ZENO eslatmasi")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            return launch(context, intent, "Budilnik sozlash oynasi ochildi")
        }
    }

    private fun setTimer(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val seconds = number(parameters["duration_seconds"])
                ?: number(parameters["minutes"])?.times(60)
                ?: return ClientToolResult.failure("Taymer davomiyligini kiriting")
            if (seconds !in 1..86400) return ClientToolResult.failure("Taymer 1 soniyadan 24 soatgacha bo‘lishi kerak")
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            return launch(context, intent, "Taymer sozlash oynasi ochildi")
        }
    }

    private fun searchGoogle(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val query = (parameters["query"] ?: parameters["search_query"] ?: "").toString().trim()
            if (query.isBlank()) return ClientToolResult.failure("Qidiruv matni bo‘sh")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            ).setPackage("com.android.chrome")
            return launch(context, intent, "Google natijalari Chrome’da ochildi")
        }
    }

    private fun searchMaps(context: Context) = object : ClientTool {
        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
            val query = (parameters["query"] ?: parameters["place"] ?: "").toString().trim()
            if (query.isBlank()) return ClientToolResult.failure("Joy nomini kiriting")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
            return launch(context, intent, "Xarita qidiruvi ochildi")
        }
    }

    private fun launch(context: Context, intent: Intent, success: String): ClientToolResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ClientToolResult.success(success)
        } catch (error: Exception) {
            ClientToolResult.failure("Amal bajarilmadi: ${error.localizedMessage ?: "xatolik"}")
        }
    }

    private fun number(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        else -> value?.toString()?.toIntOrNull()
    }
}
