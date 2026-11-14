package com.zeno.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.util.Locale

/** Runs safe, deterministic Android actions locally before an AI request. */
object LocalCommandRouter {
    fun execute(context: Context, raw: String): String? {
        val text = raw.trim()
        val normalized = text.lowercase(Locale.ROOT)
        return try {
            when {
                normalized.startsWith("internetdan qidir") || normalized.startsWith("google dan qidir") || normalized.startsWith("qidir:") || normalized.startsWith("search:") -> {
                    val query = text.substringAfter(":", text.substringAfter("qidir", "")).trim()
                    if (query.isBlank()) return "Nimani qidiray? Masalan: Internetdan qidir Android dasturlash."
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Google qidiruvi ochildi: " + query
                }
                normalized.contains("budilnik") || normalized.contains("alarm") -> {
                    val match = Regex("(\\d{1,2})[:.](\\d{2})").find(normalized)
                    val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
                    val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    if (hour !in 0..23 || minute !in 0..59) return "Vaqtni 24 soat formatida yozing. Masalan: 07:30."
                    context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, "ZENO budilnik").putExtra(AlarmClock.EXTRA_SKIP_UI, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Budilnik " + hour + ":" + "%02d".format(minute) + " uchun tayyorlandi. Saqlashni tasdiqlang."
                }
                normalized.contains("taymer") || normalized.contains("timer") -> {
                    val minutes = Regex("(\\d+)\\s*(daqiqa|minut|minute|min)").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 10
                    val seconds = Regex("(\\d+)\\s*(soniya|sekund|second|sec)").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val total = (minutes * 60 + seconds).coerceIn(1, 86400)
                    context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, total).putExtra(AlarmClock.EXTRA_MESSAGE, "ZENO taymer").putExtra(AlarmClock.EXTRA_SKIP_UI, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Taymer " + (total / 60) + " daqiqa " + (total % 60) + " soniyaga sozlandi. Tasdiqlash uchun soat ilovasi ochildi."
                }
                normalized.contains("wi-fi") || normalized.contains("wifi") -> {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Wi-Fi sozlamalari ochildi."
                }
                normalized.contains("bluetooth") -> {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Bluetooth sozlamalari ochildi."
                }
                normalized.contains("xarita") || normalized.contains("maps") || normalized.startsWith("yo'l ko'rsat") || normalized.startsWith("manzilga bor") -> {
                    val destination = text.substringAfter(":", "").trim()
                    val uri = if (destination.isNotBlank()) Uri.parse("google.navigation:q=" + Uri.encode(destination)) else Uri.parse("geo:0,0?q=")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Xarita ochildi" + if (destination.isNotBlank()) ": " + destination else "."
                }
                normalized.startsWith("telefon qil") || normalized.startsWith("qo'ng'iroq") || normalized.startsWith("raqam ter") -> {
                    val number = text.filter { it.isDigit() || it == '+' }
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Telefon terish oynasi ochildi. Qo'ng'iroqni o'zingiz tasdiqlaysiz."
                }
                normalized.startsWith("sms") || normalized.startsWith("xabar yoz") -> {
                    val body = text.substringAfter(":", "")
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "SMS yozish oynasi ochildi. Yuborishdan oldin tekshiring."
                }
                normalized.startsWith("kalendar") || normalized.startsWith("calendar") || normalized.startsWith("tadbir qo'sh") -> {
                    context.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Kalendar tadbir qo'shish oynasi ochildi."
                }
                normalized.startsWith("och ") || normalized.startsWith("open ") || normalized.startsWith("ilovani och") -> {
                    val app = text.substringAfter(" ").removePrefix("ilovani ").removePrefix("och").trim()
                    if (app.isBlank()) return "Qaysi ilovani ochay?"
                    if (launchByLabel(context, app)) app + " ilovasi ochildi." else "“" + app + "” ilovasi topilmadi."
                }
                else -> null
            }
        } catch (e: Exception) {
            "Bu amalni bajarib bo'lmadi: " + (e.localizedMessage ?: "Android ruxsati yoki mos ilova kerak")
        }
    }

    private fun launchByLabel(context: Context, query: String): Boolean {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= 33) context.packageManager.queryIntentActivities(launcher, android.content.pm.PackageManager.ResolveInfoFlags.of(0)) else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(launcher, 0)
        }
        val match = activities.firstOrNull { it.loadLabel(context.packageManager).toString().contains(query, ignoreCase = true) } ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }
}