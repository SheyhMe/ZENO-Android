package com.zeno.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase Data API client. Only the public publishable key is embedded;
 * service-role/secret keys are never shipped in the APK.
 */
object SupabaseCloud {
    private const val BASE_URL = "https://qpqryqncsvmrtlsosois.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_lT0x2RGGZglbKl2jLYZF6A_OD4wTaeY"
    private const val PREFS = "zeno_cloud_session"
    private const val TOKEN = "access_token"
    private const val USER_ID = "user_id"
    private const val CONVERSATION_ID = "conversation_id"

    fun isSignedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null) != null

    fun signIn(context: Context, email: String, password: String): String {
        val json = JSONObject().put("email", email).put("password", password)
        val response = request(context, "/auth/v1/token?grant_type=password", "POST", json, authenticated = false)
        saveSession(context, response)
        return "Hisobga muvaffaqiyatli kirdingiz."
    }

    fun signUp(context: Context, email: String, password: String): String {
        val json = JSONObject().put("email", email).put("password", password)
        val response = request(context, "/auth/v1/signup", "POST", json, authenticated = false)
        val access = response.optString("access_token")
        if (access.isNotBlank()) {
            saveSession(context, response)
            return "Hisob yaratildi va tizimga kirdingiz."
        }
        return "Hisob yaratildi. Emailingizga yuborilgan tasdiqlash havolasini oching, so'ng tizimga kiring."
    }

    fun signOut(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN, null)
        if (!token.isNullOrBlank()) {
            try { request(context, "/auth/v1/logout", "POST", JSONObject(), authenticated = true) } catch (_: Exception) {}
        }
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun saveMessage(context: Context, sender: String, content: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN, null) ?: return
        val userId = prefs.getString(USER_ID, null) ?: return
        if (content.isBlank()) return
        try {
            var conversationId = prefs.getString(CONVERSATION_ID, null)
            if (conversationId.isNullOrBlank()) {
                val created = request(context, "/rest/v1/conversations", "POST",
                    JSONObject().put("user_id", userId).put("title", "ZENO chat"), authenticated = true, preferRepresentation = true)
                conversationId = created.optJSONArrayOrSelf()?.optJSONObject(0)?.optString("id")
                if (conversationId.isNullOrBlank()) return
                prefs.edit().putString(CONVERSATION_ID, conversationId).apply()
            }
            val role = if (sender.equals("Siz", true) || sender.equals("You", true)) "user" else "assistant"
            val body = JSONObject().put("conversation_id", conversationId)
                .put("user_id", userId).put("role", role).put("content", content)
            request(context, "/rest/v1/messages", "POST", body, authenticated = true)
        } catch (_: Exception) {
            // Local chat remains available when offline; a later message retries the cloud connection.
        }
    }

    fun loadMessages(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val conversationId = prefs.getString(CONVERSATION_ID, null) ?: return emptyList()
        if (!isSignedIn(context)) return emptyList()
        return try {
            val encodedId = java.net.URLEncoder.encode(conversationId, "UTF-8")
            val response = request(context,
                "/rest/v1/messages?select=role,content&conversation_id=eq.$encodedId&order=created_at.asc",
                "GET", null, authenticated = true)
            val rows = response.optJSONArrayOrSelf() ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val role = row.optString("role")
                    val content = row.optString("content")
                    if (content.isNotBlank()) add((if (role == "user") "Siz" else "ZENO") to content)
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveSession(context: Context, response: JSONObject) {
        val access = response.optString("access_token")
        val userId = response.optJSONObject("user")?.optString("id").orEmpty()
        if (access.isBlank() || userId.isBlank()) {
            throw IllegalStateException("Sessiya olinmadi. Email tasdiqlanganini tekshiring.")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, access).putString(USER_ID, userId).apply()
    }

    private fun request(
        context: Context,
        path: String,
        method: String,
        body: JSONObject?,
        authenticated: Boolean,
        preferRepresentation: Boolean = false
    ): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            val token = if (authenticated) prefs.getString(TOKEN, null) else null
            setRequestProperty("Authorization", "Bearer " + (token ?: PUBLISHABLE_KEY))
            if (preferRepresentation) setRequestProperty("Prefer", "return=representation")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try { JSONObject(text).optString("msg").ifBlank { JSONObject(text).optString("message") } } catch (_: Exception) { text }
                throw IllegalStateException(if (message.isBlank()) "Server xatosi ($code)" else message)
            }
            if (text.isBlank()) return JSONObject()
            return if (text.trimStart().startsWith("[")) JSONObject().put("_rows", JSONArray(text)) else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.optJSONArrayOrSelf(): JSONArray? = optJSONArray("_rows")
}