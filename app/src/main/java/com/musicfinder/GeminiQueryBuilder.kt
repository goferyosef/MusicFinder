package com.musicfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiQueryBuilder {

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private const val TIMEOUT_MS = 6000

    /**
     * Asks Gemini to identify a music mention in [text] and return the best
     * YouTube search query for it.  Returns null if nothing found or on any error,
     * so callers can fall back to regex-based detection.
     */
    suspend fun buildQuery(text: String): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_KEY_HERE") return@withContext null

        try {
            val prompt = """
                Identify the song or musical piece mentioned in this text.
                Reply in this EXACT format, nothing else:
                TITLE: <title>
                ARTIST: <composer or performer, or UNKNOWN>
                QUERY: <best YouTube search query to find this piece>

                If no music is mentioned, reply exactly: NONE

                Text: """
                ${text.take(500)}
                """
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 80)
                    put("temperature", 0)
                })
            }

            val url = URL("$ENDPOINT?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val reply = JSONObject(response)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            if (reply.equals("NONE", ignoreCase = true)) return@withContext null

            reply.lineSequence()
                .firstOrNull { it.startsWith("QUERY:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.ifBlank { null }

        } catch (_: Exception) {
            null
        }
    }
}
