package com.zeno.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private val speechRequest = 101
    private val permissionRequest = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        tts = TextToSpeech(this, this)
        findViewById<Button>(R.id.listenButton).setOnClickListener { listen() }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val uz = tts.setLanguage(Locale("uz", "UZ"))
            if (uz == TextToSpeech.LANG_MISSING_DATA || uz == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale("ru", "RU")
            }
        }
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "ZENO sizni tinglayapti")
        }
        try { startActivityForResult(intent, speechRequest) }
        catch (_: Exception) { say("Ovoz tanish xizmati topilmadi") }
    }

    @Deprecated("Legacy speech activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val phrase = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            status.text = phrase
            handleCommand(phrase.lowercase(Locale("uz", "UZ")))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
        else say("Mikrofon ruxsati berilmadi")
    }

    private fun handleCommand(raw: String) {
        when {
            raw.contains("mobile legends") || raw.contains("mlbb") -> openApp("Mobile Legends")
            raw.contains("budilnik") || raw.contains("alarm") -> setAlarm(raw)
            raw.contains("taymer") || raw.contains("timer") -> setTimer(raw)
            raw.startsWith("qo'ng'iroq") || raw.startsWith("qo‘ng‘iroq") || raw.startsWith("telefon qil") -> {
                val number = raw.filter { it.isDigit() || it == '+' }
                if (number.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                else say("Raqamni ayting yoki kontaktni telefon ilovasidan tanlang")
            }
            raw.startsWith("sms") || raw.contains("xabar yoz") -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                startActivity(intent)
                say("Xabar oynasini ochdim. Matnni tekshirib, o‘zingiz yuboring")
            }
            raw.startsWith("qidir") || raw.contains("google") || raw.contains("internetdan") -> {
                val query = raw.replace("google dan", "").replace("google'dan", "").replace("qidir", "").replace("internetdan", "").trim()
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query.ifBlank { raw }))
            }
            raw.contains("och") -> openApp(raw.removeSuffix("ni och").removeSuffix("ni ochib ber").trim())
            else -> say("Bu buyruqni hali tushunmadim. Ilovani ochish, budilnik, taymer yoki qidiruvni sinab ko‘ring")
        }
    }

    private fun openApp(label: String) {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(launcher, 0)
        val target = matches.firstOrNull {
            it.loadLabel(pm).toString().contains(label, ignoreCase = true) ||
                (label.contains("mobile", true) && it.activityInfo.packageName.contains("mobile", true))
        }
        if (target != null) {
            val launch = pm.getLaunchIntentForPackage(target.activityInfo.packageName)
            if (launch != null) { startActivity(launch); say("Ilovani ochyapman"); return }
        }
        say("Ilovani topa olmadim")
    }

    private fun setAlarm(raw: String) {
        val match = Regex("(\\d{1,2})[:.](\\d{2})").find(raw)
        if (match == null) { say("Vaqtni masalan, yetti nol-nol deb ayting"); return }
        val hour = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues[2].toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) { say("Vaqt noto‘g‘ri"); return }
        startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, "ZENO"))
        say("Budilnik sozlash oynasini ochdim")
    }

    private fun setTimer(raw: String) {
        val amount = Regex("(\\d+)").find(raw)?.value?.toIntOrNull()
        if (amount == null || amount <= 0) { say("Necha daqiqalik taymer kerak?"); return }
        val seconds = if (raw.contains("soniya")) amount else amount * 60
        startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "ZENO taymeri").putExtra(AlarmClock.EXTRA_SKIP_UI, false))
        say("Taymer oynasini ochdim")
    }

    private fun say(message: String) {
        status.text = message
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zeno_reply")
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
