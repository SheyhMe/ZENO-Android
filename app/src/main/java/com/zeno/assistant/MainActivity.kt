package com.zeno.assistant

import android.Manifest
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
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
import android.widget.LinearLayout
import android.view.inputmethod.EditorInfo
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import java.util.Locale
import com.zeno.assistant.core.data.ZenoAiAction
import com.zeno.assistant.core.data.ZenoAiToolResult
import com.zeno.assistant.core.data.CloudMemory

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var chatLog: TextView
    private lateinit var messageInput: EditText
    private var voiceActive = false
    private var textSession: ConversationSession? = null
    private var textConnecting = false
    private val pendingTextMessages = ArrayDeque<String>()
    private val chatContext = mutableListOf<Pair<String, String>>()
    private val pendingCloudContexts = ArrayDeque<List<Pair<String, String>>>()
    private var cloudAiBusy = false
    private val textScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val agentId = "agent_1001m784hww0ftstymy60zkrtx6b"
    private var orbPulse: ObjectAnimator? = null
    private val permissionRequest = 102
    private val notificationPermissionRequest = 103
    private val preferences by lazy { getSharedPreferences("zeno_preferences", MODE_PRIVATE) }
    private val cloudRepository by lazy { com.zeno.assistant.core.data.ZenoCloudRepository(applicationContext) }

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
        val savedTheme = getSharedPreferences("zeno_preferences", MODE_PRIVATE).getString("theme_mode", "system")
        AppCompatDelegate.setDefaultNightMode(when (savedTheme) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
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
        findViewById<View>(R.id.gearSettings).setOnClickListener { showPage(R.id.profilePage) }
        findViewById<Button>(R.id.themeButton).setOnClickListener { cycleTheme() }
        findViewById<Button>(R.id.languageButton).setOnClickListener {
            val next = if (preferences.getString("language", "uz") == "uz") "en" else "uz"
            preferences.edit().putString("language", next).apply()
            applyLanguage()
            syncCloudSettings()
        }
        findViewById<Button>(R.id.permissionsButton).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.accountButton).setOnClickListener { showAccountDialog() }
        findViewById<Button>(R.id.memoryButton).setOnClickListener { showMemoriesDialog() }
        val appsSearch = findViewById<EditText>(R.id.appsSearchInput)
        appsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderInstalledApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderInstalledApps("")
        applyLanguage()
        findViewById<TextView>(R.id.statusIndicator).text = "● LOCAL READY"
        findViewById<Button>(R.id.homeVoiceButton).setOnClickListener { showPage(R.id.voicePage) }
        findViewById<View>(R.id.homeChatTile).setOnClickListener { showPage(R.id.chatPage); messageInput.requestFocus() }
        findViewById<View>(R.id.homeAppsTile).setOnClickListener { showPage(R.id.appsPage) }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.appsButton).setOnClickListener { fillAndOpenChat("YouTube ilovasini och") }
        findViewById<Button>(R.id.searchButton).setOnClickListener { fillAndOpenChat("Internetdan qidir: ") }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { fillAndOpenChat("Ertalab soat 07:00 ga budilnik qo'y") }
        findViewById<Button>(R.id.timerButton).setOnClickListener { fillAndOpenChat("10 daqiqaga taymer qo'y") }
        findViewById<Button>(R.id.telegramButton).setOnClickListener { fillAndOpenChat("Telegramni och") }
        findViewById<Button>(R.id.instagramButton).setOnClickListener { fillAndOpenChat("Instagramni och") }
        findViewById<Button>(R.id.chromeButton).setOnClickListener { fillAndOpenChat("Chromeni och") }
        findViewById<Button>(R.id.cameraButton).setOnClickListener { fillAndOpenChat("Kamerani och") }

        findViewById<Button>(R.id.navHome).setOnClickListener { showPage(R.id.homePage) }
        findViewById<Button>(R.id.navChat).setOnClickListener { showPage(R.id.chatPage) }
        findViewById<Button>(R.id.navApps).setOnClickListener { showPage(R.id.appsPage) }
        findViewById<Button>(R.id.navVoice).setOnClickListener { showPage(R.id.voicePage) }
        findViewById<Button>(R.id.navProfile).setOnClickListener { showPage(R.id.profilePage) }

        chatLog.text = ""
        val savedChat = preferences.getString("chat_history", "").orEmpty()
        if (savedChat.isNotBlank()) chatLog.text = savedChat
        else appendChat("ZENO", "Assalomu alaykum. Men ZENOman. Avval yozishib tanishamiz — xohlasangiz keyin ovozli suhbatni yoqishingiz mumkin.")
        textScope.launch {
            try {
                cloudRepository.awaitSession()
                if (cloudRepository.isSignedIn()) {
                    restoreCloudState()
                    syncCloudSettings()
                }
            } catch (_: Exception) {
                // The app remains usable in local mode when cloud access is unavailable.
            }
            refreshAccountButton()
        }
    }

    private fun showPage(pageId: Int) {
        val pages = listOf(R.id.homePage, R.id.chatPage, R.id.appsPage, R.id.voicePage, R.id.profilePage)
        pages.forEach { findViewById<View>(it).visibility = if (it == pageId) View.VISIBLE else View.GONE }
    }

    private fun fillAndOpenChat(value: String) {
        fill(value)
        showPage(R.id.chatPage)
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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notification_prompted", false)) {
            preferences.edit().putBoolean("notification_prompted", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequest)
            return
        }
        startVoiceService()
    }

    private fun startVoiceService() {
        val intent = Intent(this, ZenoVoiceService::class.java).setAction(ZenoVoiceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "“Hey ZENO” faollashtirilmoqda..."
    }

    private fun renderInstalledApps(query: String) {
        val container = findViewById<LinearLayout>(R.id.installedAppsContainer)
        container.removeAllViews()
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) {
            val hint = TextView(this).apply {
                text = "Ilovalarni nomi bo‘yicha qidiring"
                setTextColor(getColor(R.color.zeno_muted))
                textSize = 12f
                setPadding(4, 8, 4, 8)
            }
            container.addView(hint)
            return
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcher, 0)
        }
        val results = matches.mapNotNull { info ->
            val label = info.loadLabel(packageManager).toString()
            if (label.lowercase(Locale.ROOT).contains(normalized)) {
                label to info.activityInfo.packageName
            } else null
        }.distinctBy { it.second }.sortedBy { it.first.lowercase(Locale.ROOT) }.take(30)
        if (results.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (preferences.getString("language", "uz") == "uz") "Ilova topilmadi" else "No apps found"
                setTextColor(getColor(R.color.zeno_muted))
                textSize = 13f
                setPadding(4, 12, 4, 12)
            }
            container.addView(empty)
            return
        }
        results.forEach { (label, packageName) ->
            val item = Button(this).apply {
                text = "▣   $label"
                isAllCaps = false
                textSize = 13f
                setTextColor(getColor(R.color.zeno_text))
                setBackgroundResource(R.drawable.zeno_surface)
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage(packageName)
                    if (launch != null) startActivity(launch)
                    else Toast.makeText(this@MainActivity, "Ilovani ochib bo‘lmadi", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 })
        }
    }

    private fun cycleTheme() {
        val current = preferences.getString("theme_mode", "system")
        val next = when (current) {
            "system" -> "dark"
            "dark" -> "light"
            else -> "system"
        }
        preferences.edit().putString("theme_mode", next).apply()
        syncCloudSettings()
        AppCompatDelegate.setDefaultNightMode(when (next) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    private suspend fun restoreCloudState() {
        val cloudSettings = cloudRepository.loadSettings()
        if (cloudSettings != null) {
            preferences.edit()
                .putString("language", cloudSettings.language)
                .putString("theme_mode", cloudSettings.theme)
                .apply()
            AppCompatDelegate.setDefaultNightMode(when (cloudSettings.theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
            applyLanguage()
        }

        val messages = cloudRepository.restoreLatestConversation()
        chatContext.clear()
        chatContext.addAll(messages.filter { it.first == "user" || it.first == "assistant" })
        val restored = messages.joinToString("\n\n") { (role, content) ->
            val sender = if (role == "user") {
                if (preferences.getString("language", "uz") == "en") "You" else "Siz"
            } else "ZENO"
            "$sender\n$content"
        }
        chatLog.text = restored
        if (restored.isBlank()) preferences.edit().remove("chat_history").apply()
        else preferences.edit().putString("chat_history", restored).apply()
        cloudRepository.startConversation()
    }

    private fun refreshAccountButton() {
        val button = findViewById<Button>(R.id.accountButton) ?: return
        val email = cloudRepository.signedInEmail()
        button.text = if (email.isNullOrBlank()) {
            if (preferences.getString("language", "uz") == "en") "☁  Sign in to sync" else "☁  ZENO Cloud — Kirish"
        } else {
            "☁  $email"
        }
        findViewById<TextView>(R.id.statusIndicator).text = if (email.isNullOrBlank()) {
            if (preferences.getString("language", "uz") == "en") "● LOCAL READY" else "● MAHALLIY TAYYOR"
        } else {
            if (preferences.getString("language", "uz") == "en") "● ZENO AI READY" else "● ZENO AI TAYYOR"
        }
    }

    private fun syncCloudSettings() {
        textScope.launch {
            try {
                cloudRepository.syncSettings(
                    language = preferences.getString("language", "uz") ?: "uz",
                    theme = preferences.getString("theme_mode", "system") ?: "system",
                    voiceEnabled = voiceActive,
                    wakePhraseEnabled = voiceActive
                )
            } catch (_: Exception) {
                // Cloud sync is best-effort; local settings remain available.
            }
        }
    }

    private fun showAccountDialog() {
        if (cloudRepository.isSignedIn()) {
            AlertDialog.Builder(this)
                .setTitle("ZENO Cloud")
                .setMessage(cloudRepository.signedInEmail() ?: "Hisob ulangan")
                .setPositiveButton("Chiqish") { _, _ ->
                    textScope.launch {
                        try {
                            cloudRepository.signOut()
                            chatLog.text = ""
                            chatContext.clear()
                            pendingCloudContexts.clear()
                            preferences.edit().remove("chat_history").apply()
                            refreshAccountButton()
                            Toast.makeText(this@MainActivity, "Hisobdan chiqildi", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, e.localizedMessage ?: "Xatolik", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Yopish", null)
                .show()
            return
        }

        val emailInput = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
        }
        val passwordInput = EditText(this).apply {
            hint = "Parol (kamida 8 belgi)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(emailInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(passwordInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("ZENO Cloud")
            .setMessage("Suhbatlar va sozlamalarni hisobingiz bilan sinxronlang.")
            .setView(form)
            .setPositiveButton("Kirish", null)
            .setNeutralButton("Ro‘yxatdan o‘tish", null)
            .setNegativeButton("Bekor", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "Email va parolni kiriting", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                textScope.launch {
                    try {
                        cloudRepository.signIn(email, password)
                        restoreCloudState()
                        syncCloudSettings()
                        refreshAccountButton()
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "ZENO Cloud ulandi", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.localizedMessage ?: "Kirish amalga oshmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "Email va parolni kiriting", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                textScope.launch {
                    try {
                        val signedIn = cloudRepository.signUp(email, password)
                        if (signedIn) {
                            restoreCloudState()
                            syncCloudSettings()
                            refreshAccountButton()
                            dialog.dismiss()
                            Toast.makeText(this@MainActivity, "Hisob yaratildi va ulandi", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Emailingizga tasdiqlash havolasi yuborildi. Tasdiqlagach, tizimga kiring.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.localizedMessage ?: "Ro‘yxatdan o‘tish amalga oshmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showMemoriesDialog() {
        if (!cloudRepository.isSignedIn()) {
            AlertDialog.Builder(this)
                .setTitle("ZENO Xotiralar")
                .setMessage("Xotiralarni saqlash va boshqarish uchun avval ZENO Cloud hisobiga kiring.")
                .setPositiveButton("Kirish") { _, _ -> showAccountDialog() }
                .setNegativeButton("Yopish", null)
                .show()
            return
        }
        textScope.launch {
            try {
                val memories = cloudRepository.loadMemories()
                val english = preferences.getString("language", "uz") == "en"
                val labels = memories.map { "${it.category}: ${it.content}" }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (english) "ZENO Memories" else "ZENO Xotiralar")
                    .setItems(labels) { _, which ->
                        if (which in memories.indices) showMemoryActions(memories[which])
                    }
                    .setPositiveButton(if (english) "Add memory" else "Xotira qo‘shish") { _, _ ->
                        showAddMemoryDialog()
                    }
                    .setNegativeButton(if (english) "Close" else "Yopish", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.localizedMessage ?: "Xotiralarni yuklab bo‘lmadi", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMemoryActions(memory: CloudMemory) {
        val english = preferences.getString("language", "uz") == "en"
        AlertDialog.Builder(this)
            .setTitle(memory.category)
            .setMessage(memory.content)
            .setPositiveButton(if (english) "Edit" else "Tahrirlash") { _, _ ->
                showEditMemoryDialog(memory)
            }
            .setNegativeButton(if (english) "Delete" else "O‘chirish") { _, _ ->
                textScope.launch {
                    try {
                        cloudRepository.deleteMemory(memory.id)
                        Toast.makeText(this@MainActivity, if (english) "Memory deleted" else "Xotira o‘chirildi", Toast.LENGTH_SHORT).show()
                        showMemoriesDialog()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.localizedMessage ?: "Xotirani o‘chirib bo‘lmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNeutralButton(if (english) "Close" else "Yopish", null)
            .show()
    }

    private fun showAddMemoryDialog() {
        val english = preferences.getString("language", "uz") == "en"
        val input = EditText(this).apply {
            hint = if (english) "What should ZENO remember?" else "ZENO nimani eslab qolsin?"
            minLines = 2
            maxLines = 5
            setPadding(36, 24, 36, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(if (english) "Add memory" else "Xotira qo‘shish")
            .setView(input)
            .setPositiveButton(if (english) "Save" else "Saqlash") { _, _ ->
                textScope.launch {
                    try {
                        cloudRepository.addMemory(input.text.toString())
                        Toast.makeText(this@MainActivity, if (english) "Memory saved" else "Xotira saqlandi", Toast.LENGTH_SHORT).show()
                        showMemoriesDialog()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.localizedMessage ?: "Xotirani saqlab bo‘lmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(if (english) "Cancel" else "Bekor qilish", null)
            .show()
    }

    private fun showEditMemoryDialog(memory: CloudMemory) {
        val english = preferences.getString("language", "uz") == "en"
        val input = EditText(this).apply {
            setText(memory.content)
            minLines = 2
            maxLines = 5
            setPadding(36, 24, 36, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(if (english) "Edit memory" else "Xotirani tahrirlash")
            .setView(input)
            .setPositiveButton(if (english) "Save" else "Saqlash") { _, _ ->
                textScope.launch {
                    try {
                        cloudRepository.updateMemory(memory.id, input.text.toString(), memory.category)
                        Toast.makeText(this@MainActivity, if (english) "Memory updated" else "Xotira yangilandi", Toast.LENGTH_SHORT).show()
                        showMemoriesDialog()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.localizedMessage ?: "Xotirani yangilab bo‘lmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(if (english) "Cancel" else "Bekor qilish", null)
            .show()
    }

    private fun applyLanguage() {
        val english = preferences.getString("language", "uz") == "en"
        findViewById<Button>(R.id.navHome).text = if (english) "⌂\nHome" else "⌂\nBosh sahifa"
        findViewById<Button>(R.id.navChat).text = if (english) "▢\nChat" else "▢\nChat"
        findViewById<Button>(R.id.navApps).text = if (english) "▦\nApps" else "▦\nIlovalar"
        findViewById<Button>(R.id.navVoice).text = if (english) "🎙\nVoice" else "🎙\nOvoz"
        findViewById<Button>(R.id.navProfile).text = if (english) "⚙\nSettings" else "⚙\nSozlamalar"
        findViewById<EditText>(R.id.messageInput).hint = if (english) "Write a message..." else "Xabar yozing..."
        findViewById<EditText>(R.id.appsSearchInput).hint = if (english) "⌕   Search apps..." else "⌕   Ilova qidirish..."
        findViewById<Button>(R.id.languageButton).text =
            if (english) "Language: English (tap to use Uzbek)" else "Til: O‘zbekcha (English uchun bosing)"
        findViewById<Button>(R.id.permissionsButton).text = if (english) "App permissions" else "Ilova ruxsatlari"
        findViewById<Button>(R.id.memoryButton).text = if (english) "🧠  Memories" else "🧠  Xotiralar"
        val themeMode = preferences.getString("theme_mode", "system")
        findViewById<Button>(R.id.themeButton).text = when (themeMode) {
            "dark" -> if (english) "Theme: Dark (tap to cycle)" else "Mavzu: Tungi (almashtirish)"
            "light" -> if (english) "Theme: Light (tap to cycle)" else "Mavzu: Kunduzgi (almashtirish)"
            else -> if (english) "Theme: System (tap to cycle)" else "Mavzu: Avtomatik (almashtirish)"
        }
        findViewById<TextView>(R.id.statusIndicator).text = when {
            cloudRepository.isSignedIn() && english -> "● ZENO AI READY"
            cloudRepository.isSignedIn() -> "● ZENO AI TAYYOR"
            english -> "● LOCAL READY"
            else -> "● MAHALLIY TAYYOR"
        }
        findViewById<TextView>(R.id.settingsInfo).text = if (english) {
            "ZENO — your personal AI assistant\n\nLanguage: Uzbek / English\nTheme: System / Light / Dark\nText AI: messages are sent to GPT-6 Luna through Vercel AI Gateway\nCloud data: chats and memories are stored in your Supabase account; manage memories here\nAccount security: session tokens are encrypted with Android Keystore\nMicrophone: used only after you enable voice mode\nWake phrase: Android speech recognition, on-device where supported\nVoice processing: active voice conversations send audio to the configured voice service\nApp search: processed locally on this device\nNo SMS, contacts, call-log or accessibility permissions"
        } else {
            "ZENO — shaxsiy AI yordamchingiz\n\nTil: O‘zbek / English\nMavzu: Avtomatik / Kunduzgi / Tungi\nMatnli AI: xabarlar Vercel AI Gateway orqali GPT-6 Luna modeliga yuboriladi\nBulutdagi ma’lumotlar: chat va xotiralar Supabase hisobingizda saqlanadi; xotiralarni shu yerdan boshqarasiz\nHisob xavfsizligi: sessiya tokenlari Android Keystore orqali shifrlanadi\nMikrofon: faqat ovozli rejimni yoqqaningizda ishlaydi\nUyg‘otuvchi so‘z: Android ovoz tanishi, qo‘llab-quvvatlansa telefonda qayta ishlanadi\nOvozli suhbat: faol suhbat paytida audio sozlangan ovoz xizmatiga yuboriladi\nIlova qidiruvi: telefonda mahalliy bajariladi\nSMS, kontaktlar, qo‘ng‘iroqlar tarixi va Accessibility ruxsati so‘ralmaydi"
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return
        appendChat("Siz", message)
        messageInput.text.clear()
        if (voiceActive) {
            startService(Intent(this, ZenoVoiceService::class.java)
                .setAction(ZenoVoiceService.ACTION_SEND)
                .putExtra(ZenoVoiceService.EXTRA_TEXT, message))
        } else {
            sendTextMessage(message)
        }
    }

    private fun sendTextMessage(message: String) {
        if (cloudRepository.isSignedIn()) {
            pendingCloudContexts.addLast(chatContext.toList())
            processNextCloudMessage()
            return
        }
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
                    clientTools = AndroidActionTools.create(this@MainActivity),
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

    private fun processNextCloudMessage() {
        if (cloudAiBusy || pendingCloudContexts.isEmpty()) return
        val context = pendingCloudContexts.removeFirst()
        cloudAiBusy = true
        status.text = "ZENO javob tayyorlamoqda..."
        textScope.launch {
            try {
                var response = cloudRepository.sendAiMessage(context)
                if (response.actions.isNotEmpty()) {
                    val actionsToRun = response.actions.take(6)
                    val actionResults = actionsToRun.map { action ->
                        val tool = AndroidActionTools.create(this@MainActivity)[action.name]
                        val result = try {
                            tool?.execute(action.arguments)
                        } catch (error: Exception) {
                            null
                        }
                        val output = when {
                            tool == null -> "Bu amal qurilmada qo‘llab-quvvatlanmaydi."
                            result == null -> "Amal qurilmaga yuborildi."
                            result.success -> result.result
                            else -> result.error ?: "Amal bajarilmadi."
                        }
                        ZenoAiToolResult(action.callId, output)
                    }
                    response = cloudRepository.sendAiMessage(context, actionsToRun, actionResults)
                }
                appendChat("ZENO", response.reply.ifBlank { "Amal bajarildi." })
                status.text = "ZENO AI tayyor"
            } catch (e: Exception) {
                appendChat("ZENO", e.localizedMessage ?: "AI bilan bog‘lanib bo‘lmadi.")
                status.text = "ZENO AI ulanish xatosi"
            } finally {
                cloudAiBusy = false
                processNextCloudMessage()
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
        chatLog.text = if (updated.length > 6000) updated.takeLast(6000) else updated
        preferences.edit().putString("chat_history", chatLog.text.toString()).apply()
        val role = if (sender == "Siz" || sender == "You") "user" else "assistant"
        if (role == "user" || role == "assistant") {
            chatContext.add(role to message)
            while (chatContext.size > 20) chatContext.removeAt(0)
        }
        textScope.launch {
            try {
                cloudRepository.saveMessage(role, message)
            } catch (_: Exception) {
                // Keep local chat responsive if the cloud is offline.
            }
        }
    }

    override fun onDestroy() {
        orbPulse?.cancel()
        textScope.launch { textSession?.endSession(); textSession = null }
        orbPulse = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == notificationPermissionRequest) {
            startVoiceService()
        } else if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            toggleBackgroundVoice()
        } else if (requestCode == permissionRequest) {
            status.text = "Mikrofon ruxsati kerak"
            Toast.makeText(this, "Ovozli boshqaruv uchun mikrofon ruxsati kerak.", Toast.LENGTH_LONG).show()
        }
    }
}