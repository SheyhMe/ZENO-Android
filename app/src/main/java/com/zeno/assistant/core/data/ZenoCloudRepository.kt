package com.zeno.assistant.core.data

import android.content.Context
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase-backed identity, preferences and chat persistence.
 * The embedded key is a publishable client key; RLS is the security boundary.
 * Never add a service-role or secret key to this Android app.
 */
data class CloudPreferenceSnapshot(
    val language: String,
    val theme: String,
    val voiceEnabled: Boolean,
    val wakePhraseEnabled: Boolean
)

data class ZenoAiAction(
    val callId: String,
    val name: String,
    val arguments: Map<String, Any>
)

data class ZenoAiToolResult(val callId: String, val output: String)

data class ZenoAiResponse(val reply: String, val actions: List<ZenoAiAction>)

data class CloudMemory(
    val id: String,
    val category: String,
    val content: String,
    val importance: Int
)

class ZenoCloudRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("zeno_cloud", Context.MODE_PRIVATE)

    private val client by lazy {
        createSupabaseClient(
            supabaseUrl = com.zeno.assistant.BuildConfig.SUPABASE_URL,
            supabaseKey = com.zeno.assistant.BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)
        }
    }

    suspend fun awaitSession() {
        client.auth.awaitInitialization()
    }

    fun signedInEmail(): String? = client.auth.currentUserOrNull()?.email

    fun isSignedIn(): Boolean = client.auth.currentUserOrNull() != null

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        client.auth.awaitInitialization()
        val user = client.auth.currentUserOrNull() ?: error("Kirish yakunlanmadi. Email tasdiqlanganini tekshiring.")
        syncProfile(user.id, user.email)
        restoreLatestConversation()
        startConversation()
    }

    suspend fun signUp(email: String, password: String): Boolean {
        require(password.length >= 8) { "Parol kamida 8 ta belgidan iborat bo‘lishi kerak." }
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        client.auth.awaitInitialization()
        val user = client.auth.currentUserOrNull() ?: return false
        syncProfile(user.id, user.email)
        restoreLatestConversation()
        startConversation()
        return true
    }

    suspend fun signOut() {
        client.auth.signOut()
        preferences.edit().remove("conversation_id").remove("conversation_owner").apply()
    }

    suspend fun syncSettings(language: String, theme: String, voiceEnabled: Boolean, wakePhraseEnabled: Boolean) {
        val user = client.auth.currentUserOrNull() ?: return
        client.from("assistant_preferences").upsert(
            PreferenceRow(
                user_id = user.id,
                language = if (language == "en") "en" else "uz",
                theme = theme,
                voice_enabled = voiceEnabled,
                wake_phrase_enabled = wakePhraseEnabled
            )
        )
    }

    suspend fun loadSettings(): CloudPreferenceSnapshot? {
        val user = client.auth.currentUserOrNull() ?: return null
        return client.from("assistant_preferences")
            .select(columns = Columns.list("user_id, language, theme, voice_enabled, wake_phrase_enabled")) {
                filter { eq("user_id", user.id) }
            }
            .decodeList<PreferenceRow>()
            .firstOrNull()
            ?.let {
                CloudPreferenceSnapshot(it.language, it.theme, it.voice_enabled, it.wake_phrase_enabled)
            }
    }

    suspend fun restoreLatestConversation(): List<Pair<String, String>> {
        val user = client.auth.currentUserOrNull() ?: return emptyList()
        val latest = client.from("conversations")
            .select(columns = Columns.list("id, updated_at")) {
                filter { eq("user_id", user.id) }
            }
            .decodeList<ConversationLookupRow>()
            .maxByOrNull { it.updated_at }

        if (latest == null) {
            preferences.edit().remove("conversation_id").remove("conversation_owner").apply()
            return emptyList()
        }

        preferences.edit().putString("conversation_owner", user.id).putString("conversation_id", latest.id).apply()
        return client.from("messages")
            .select(columns = Columns.list("role, content, created_at")) {
                filter { eq("conversation_id", latest.id) }
            }
            .decodeList<StoredMessageRow>()
            .sortedBy { it.created_at }
            .map { it.role to it.content }
    }

    suspend fun startConversation(): String? {
        val user = client.auth.currentUserOrNull() ?: return null
        val savedOwner = preferences.getString("conversation_owner", null)
        val savedId = preferences.getString("conversation_id", null)
        if (savedOwner == user.id && !savedId.isNullOrBlank()) return savedId

        val id = UUID.randomUUID().toString()
        client.from("conversations").insert(
            ConversationRow(id = id, user_id = user.id, title = "ZENO")
        )
        preferences.edit()
            .putString("conversation_owner", user.id)
            .putString("conversation_id", id)
            .apply()
        return id
    }

    suspend fun loadMemories(): List<CloudMemory> {
        val user = client.auth.currentUserOrNull() ?: return emptyList()
        return client.from("user_memories")
            .select(columns = Columns.list("id, category, content, importance")) {
                filter {
                    eq("user_id", user.id)
                    eq("is_active", true)
                }
            }
            .decodeList<MemoryRow>()
            .sortedByDescending { it.importance }
            .map { CloudMemory(it.id, it.category, it.content, it.importance) }
    }

    suspend fun addMemory(content: String, category: String = "general", importance: Int = 3) {
        val user = client.auth.currentUserOrNull() ?: error("Xotira saqlash uchun ZENO Cloud hisobiga kiring.")
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank() && cleanContent.length <= 10000) { "Xotira 1–10000 belgidan iborat bo‘lishi kerak." }
        client.from("user_memories").insert(
            MemoryInsertRow(
                user_id = user.id,
                category = category.take(80),
                content = cleanContent,
                source = "user",
                importance = importance.coerceIn(1, 5)
            )
        )
    }

    suspend fun updateMemory(id: String, content: String, category: String = "general") {
        val user = client.auth.currentUserOrNull() ?: error("Xotira tahrirlash uchun ZENO Cloud hisobiga kiring.")
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank() && cleanContent.length <= 10000) { "Xotira 1–10000 belgidan iborat bo‘lishi kerak." }
        client.from("user_memories").update(
            MemoryUpdateRow(category = category.take(80), content = cleanContent, updated_at = Instant.now().toString())
        ) {
            filter {
                eq("id", id)
                eq("user_id", user.id)
            }
        }
    }

    suspend fun deleteMemory(id: String) {
        val user = client.auth.currentUserOrNull() ?: return
        client.from("user_memories").delete {
            filter {
                eq("id", id)
                eq("user_id", user.id)
            }
        }
    }

    suspend fun sendAiMessage(
        messages: List<Pair<String, String>>,
        previousActions: List<ZenoAiAction> = emptyList(),
        toolResults: List<ZenoAiToolResult> = emptyList()
    ): ZenoAiResponse {
        awaitSession()
        val accessToken = client.auth.currentAccessTokenOrNull()
            ?: error("ZENO AI uchun avval ZENO Cloud hisobiga kiring.")

        return withContext(Dispatchers.IO) {
            val connection = (URL("https://zeno-private-backend-git-zeno-full-upgrade-seyhme-5727.vercel.app/api/chat").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 45000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val payload = JSONObject()
                    .put("messages", JSONArray().apply {
                        messages.takeLast(20).forEach { (role, content) ->
                            if (content.isNotBlank() && (role == "user" || role == "assistant")) {
                                put(JSONObject().put("role", role).put("content", content.take(6000)))
                            }
                        }
                    })
                    .put("toolCalls", JSONArray().apply {
                        previousActions.forEach { action ->
                            put(JSONObject()
                                .put("call_id", action.callId)
                                .put("name", action.name)
                                .put("arguments", JSONObject(action.arguments)))
                        }
                    })
                    .put("toolResults", JSONArray().apply {
                        toolResults.forEach { result ->
                            put(JSONObject().put("call_id", result.callId).put("output", result.output))
                        }
                    })
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = if (response.isBlank()) JSONObject() else JSONObject(response)
                if (status !in 200..299) {
                    error(json.optString("error").ifBlank { "ZENO AI serverida xatolik ($status)." })
                }

                val actions = mutableListOf<ZenoAiAction>()
                val actionArray = json.optJSONArray("actions") ?: JSONArray()
                for (index in 0 until actionArray.length()) {
                    val item = actionArray.optJSONObject(index) ?: continue
                    val args = item.optJSONObject("arguments") ?: JSONObject()
                    val values = mutableMapOf<String, Any>()
                    val keys = args.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = args.opt(key)
                        if (value != null && value != JSONObject.NULL) values[key] = value
                    }
                    val callId = item.optString("call_id")
                    val name = item.optString("name")
                    if (callId.isNotBlank() && name.isNotBlank()) {
                        actions.add(ZenoAiAction(callId, name, values))
                    }
                }

                ZenoAiResponse(json.optString("reply"), actions)
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun saveMessage(role: String, content: String) {
        if (content.isBlank()) return
        val user = client.auth.currentUserOrNull() ?: return
        val conversationId = startConversation() ?: return
        client.from("messages").insert(
            MessageRow(
                conversation_id = conversationId,
                user_id = user.id,
                role = when (role) {
                    "user" -> "user"
                    "system" -> "system"
                    "tool" -> "tool"
                    else -> "assistant"
                },
                content = content.take(100000)
            )
        )
        client.from("conversations").update(
            ConversationTimestampUpdate(updated_at = Instant.now().toString())
        ) {
            filter { eq("id", conversationId) }
        }
    }

    private suspend fun syncProfile(userId: String, email: String?) {
        client.from("profiles").upsert(
            ProfileRow(user_id = userId, display_name = email?.substringBefore("@"))
        )
    }

    @Serializable
    private data class MemoryRow(
        val id: String,
        val category: String,
        val content: String,
        val importance: Int
    )

    @Serializable
    private data class MemoryInsertRow(
        val user_id: String,
        val category: String,
        val content: String,
        val source: String,
        val importance: Int
    )

    @Serializable
    private data class MemoryUpdateRow(
        val category: String,
        val content: String,
        val updated_at: String
    )

    @Serializable
    private data class ProfileRow(
        val user_id: String,
        val display_name: String? = null
    )

    @Serializable
    private data class PreferenceRow(
        val user_id: String,
        val language: String,
        val theme: String,
        val voice_enabled: Boolean,
        val wake_phrase_enabled: Boolean
    )

    @Serializable
    private data class ConversationTimestampUpdate(val updated_at: String)

    @Serializable
    private data class ConversationRow(
        val id: String,
        val user_id: String,
        val title: String
    )

    @Serializable
    private data class ConversationLookupRow(val id: String, val updated_at: String)

    @Serializable
    private data class StoredMessageRow(val role: String, val content: String, val created_at: String)

    @Serializable
    private data class MessageRow(
        val conversation_id: String,
        val user_id: String,
        val role: String,
        val content: String
    )
}
