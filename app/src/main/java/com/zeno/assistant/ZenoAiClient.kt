package com.zeno.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ZenoAiClient {
    private const val FUNCTION_URL = "https://qpqryqncsvmrtlsosois.supabase.co/functions/v1/zeno-chat"
    // This is the project's public anon key, not a secret. The OpenAI key stays on the server.
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFwcXJ5cW5jc3ZtcnRsc29zb2lzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ2Mjg4NzksImV4cCI6MjExMDIwNDg3OX0.1iorFyc8U38ZNQsvfcI_De1iLQFz_t_zRZ_We6AgqKA"

    fun send(input: JSONArray): JSONObject {
        val connection = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 55000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
        }
        try {
            val body = JSONObject().put("input", input).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.optString("error", "Server error ${connection.responseCode}"))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
