package com.example.wolremote

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {

    data class PollResult(val pending: Boolean, val id: String?)

    private const val TIMEOUT_MS = 10000

    fun poll(baseUrl: String): PollResult {
        val url = baseUrl.trimEnd('/') + "/api/poll"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val code = connection.responseCode
            if (code != 200) throw IOException("Poll failed: HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val pending = json.optBoolean("pending")
            val id = json.optJSONObject("request")?.optString("id")
            return PollResult(pending, id)
        } finally {
            connection.disconnect()
        }
    }

    fun ack(baseUrl: String) {
        val url = baseUrl.trimEnd('/') + "/api/ack"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val code = connection.responseCode
            if (code != 200) throw IOException("Ack failed: HTTP $code")
        } finally {
            connection.disconnect()
        }
    }
}