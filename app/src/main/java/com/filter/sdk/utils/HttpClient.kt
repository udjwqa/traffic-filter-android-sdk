package com.filter.sdk.utils

import com.filter.sdk.FilterConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HttpClient(private val config: FilterConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun get(path: String, extraHeaders: Map<String, String> = emptyMap()): HttpResult {
        val url = "${config.serverUrl}$path"
        val builder = Request.Builder().url(url).get()
        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build())
    }

    fun post(path: String, body: JSONObject, extraHeaders: Map<String, String> = emptyMap()): HttpResult {
        val url = "${config.serverUrl}$path"
        val requestBody = body.toString().toRequestBody(jsonType)
        val builder = Request.Builder().url(url).post(requestBody)
        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build())
    }

    private fun execute(request: Request): HttpResult {
        return try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            val json = if (bodyStr.startsWith("{")) {
                try { JSONObject(bodyStr) } catch (_: Exception) { null }
            } else null

            HttpResult(
                code = response.code,
                body = bodyStr,
                json = json,
                redirectUrl = response.header("Location"),
                success = response.isSuccessful || response.isRedirect,
            )
        } catch (e: Exception) {
            HttpResult(
                code = -1,
                body = e.message ?: "Unknown error",
                json = null,
                redirectUrl = null,
                success = false,
            )
        }
    }
}

data class HttpResult(
    val code: Int,
    val body: String,
    val json: JSONObject?,
    val redirectUrl: String?,
    val success: Boolean,
)
