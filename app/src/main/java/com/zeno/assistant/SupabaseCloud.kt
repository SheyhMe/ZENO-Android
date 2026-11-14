package com.zeno.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Supabase Data API client. Only the public publishable key is embedded in the APK. */
object SupabaseCloud {
    private const val BASE_URL = "https://qpqryqncsvmrtlsosois.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_lT0x2RGGZglbKl2jLYZF6A_OD4wTaeY"
    private const val PREFS = "zeno_cloud_session"
    private const val TOKEN = "access_token"
    private const val REFRESH_TOKEN = "refresh_token"
    private const val USER_ID = "user_id"
    private const val CONVERSATION_ID = "conversation_id"

    fun isSignedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null) != null

    fun currentUserId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(USER_ID, null)

    fun signIn(context: Context, email: String, password: String): String {
        val response = request(context, "/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email).put("password", password), authenticated = false)
        saveSession(context, response)
        return "Hisobga muvaffaqiyatli kirdingiz."
    }

    fun signUp(context: Context, email: String, password: String): String {
        val response = request(context, "/auth/v1/signup", "POST",
            JSONObject().put("email", email).put("password", password), authenticated = false)
        if (response.optString("access_token").isNotBlank()) {
            saveSession(context, response)
            return "Hisob yaratildi va tizimga kirdingiz."
        }
        return "Hisob yaratildi. Emailingizga yuborilgan tasdiqlash havolasini oching, so'ng tizimga kiring."
    }

    /** Clear local credentials immediately; revoke the server session in the background. */
    fun signOut(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN, null)
        prefs.edit().clear().apply()
        if (!token.isNullOrBlank()) {
            Thread {
                val connection = try {
                    (URL("$BASE_URL/auth/v1/logout").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("apikey", PUBLISHABLE_KEY)
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Content-Type", "application/json")
                    }
                } catch (_: Exception) { null }
                try { connection?.responseCode } catch (_: Exception) { } finally { connection?.disconnect() }
            }.start()
        }
    }

    @Synchronized
    fun saveMessage(context: Context, sender: String, content: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(TOKEN, null) == null || prefs.getString(USER_ID, null) == null || content.isBlank()) return
        try {
            var conversationId = prefs.getString(CONVERSATION_ID, null)
            if (conversationId.isNullOrBlank()) {
                val userId = prefs.getString(USER_ID, null) ?: return
                val created = request(context, "/rest/v1/conversations", "POST",
                    JSONObject().put("user_id", userId).put("title", "ZENO chat"),
                    authenticated = true, preferRepresentation = true)
                conversationId = created.optJSONArrayOrSelf()?.optJSONObject(0)?.optString("id")
                if (conversationId.isNullOrBlank()) return
                prefs.edit().putString(CONVERSATION_ID, conversationId).apply()
            }
            val role = if (sender.equals("Siz", true) || sender.equals("You", true)) "user" else "assistant"
            val body = JSONObject().put("conversation_id", conversationId)
                .put("user_id", prefs.getString(USER_ID, null)).put("role", role).put("content", content)
            request(context, "/rest/v1/messages", "POST", body, authenticated = true)
        } catch (_: Exception) {
            // Local history remains available offline.
        }
    }

    fun syncHistory(context: Context, chatText: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(CONVERSATION_ID, null) != null || !isSignedIn(context)) return
        chatText.split("\n\n").forEach { block ->
            val parts = block.split("\n", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) saveMessage(context, parts[0].trim(), parts[1].trim())
        }
    }

    fun loadMessages(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val conversationId = prefs.getString(CONVERSATION_ID, null) ?: return emptyList()
        if (!isSignedIn(context)) return emptyList()
        return try {
            val encodedId = java.net.URLEncoder.encode(conversationId, "UTF-8")
            val response = request(context, "/rest/v1/messages?select=role,content&conversation_id=eq.$encodedId&order=created_at.asc",
                "GET", null, authenticated = true)
            val rows = response.optJSONArrayOrSelf() ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val content = row.optString("content")
                    if (content.isNotBlank()) add((if (row.optString("role") == "user") "Siz" else "ZENO") to content)
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveSession(context: Context, response: JSONObject) {
        val access = response.optString("access_token")
        val refresh = response.optString("refresh_token")
        val userId = response.optJSONObject("user")?.optString("id").orEmpty()
        if (access.isBlank() || userId.isBlank()) throw IllegalStateException("Sessiya olinmadi. Email tasdiqlanganini tekshiring.")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldUser = prefs.getString(USER_ID, null)
        val editor = prefs.edit().putString(TOKEN, access).putString(USER_ID, userId)
        if (refresh.isNotBlank()) editor.putString(REFRESH_TOKEN, refresh)
        if (oldUser != userId) editor.remove(CONVERSATION_ID)
        editor.apply()
    }

    @Synchronized
    private fun refreshSession(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refresh = prefs.getString(REFRESH_TOKEN, null) ?: return false
        return try {
            val response = request(context, "/auth/v1/token?grant_type=refresh_token", "POST",
                JSONObject().put("refresh_token", refresh), authenticated = false, allowRefresh = false)
            saveSession(context, response)
            true
        } catch (_: Exception) {
            prefs.edit().remove(TOKEN).remove(REFRESH_TOKEN).remove(USER_ID).remove(CONVERSATION_ID).apply()
            false
        }
    }

    private fun request(
        context: Context,
        path: String,
        method: String,
        body: JSONObject?,
        authenticated: Boolean,
        preferRepresentation: Boolean = false,
        allowRefresh: Boolean = true
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
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 && authenticated && allowRefresh && refreshSession(context)) {
                return request(context, path, method, body, authenticated, preferRepresentation, allowRefresh = false)
            }
            if (code !in 200..299) {
                val message = try {
                    JSONObject(responseText).optString("msg").ifBlank { JSONObject(responseText).optString("message") }
                } catch (_: Exception) { responseText }
                throw IllegalStateException(if (message.isBlank()) "Server xatosi ($code)" else message)
            }
            if (responseText.isBlank()) return JSONObject()
            return if (responseText.trimStart().startsWith("[")) JSONObject().put("_rows", JSONArray(responseText)) else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.optJSONArrayOrSelf(): JSONArray? = optJSONArray("_rows")
}