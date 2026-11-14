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

/**
 * Supabase-backed identity, preferences and chat persistence.
 * The embedded key is a publishable client key; RLS is the security boundary.
 * Never add a service-role or secret key to this Android app.
 */
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
    }

    private suspend fun syncProfile(userId: String, email: String?) {
        client.from("profiles").upsert(
            ProfileRow(user_id = userId, display_name = email?.substringBefore("@"))
        )
    }

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
