package com.app.core.analytics

import android.content.Context
import android.util.Log
import com.app.core.analytics.models.ConfigData
import com.app.core.analytics.models.CheckDetail
import com.app.core.analytics.models.Status
import com.app.core.analytics.utils.DeviceInfo
import com.app.core.analytics.utils.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfigProvider(
    private val config: AnalyticsConfig,
    private val context: Context,
) {
    private val http = HttpClient(config)

    suspend fun fetch(): ConfigData = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("X-App-Token", config.appToken)
            put("X-App-Id", DeviceInfo.appId(context))
            put("User-Agent", DeviceInfo.userAgent())
            put("Accept-Language", DeviceInfo.locale(context))
            put("X-Device-Info", DeviceInfo.model())
            put("X-Device-Codename", DeviceInfo.codename())
            put("X-Build-Product", DeviceInfo.product())
            put("X-OS-Version", DeviceInfo.osVersion())
            val gpu = DeviceInfo.graphicsRenderer()
            if (gpu.isNotBlank()) put("X-Graphics-Info", gpu)
        }

        val result = http.get("/analytics/config", headers)

        if (!result.success || result.json == null) {
            log("Config request failed: code=${result.code}")
            return@withContext ConfigData(0, Status.ACTIVE, null, null, emptyList())
        }

        val json = result.json
        val details = mutableListOf<CheckDetail>()
        val arr = json.optJSONArray("details")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                details.add(CheckDetail(d.optString("check"), d.optInt("points"), d.optString("reason")))
            }
        }

        ConfigData(
            score = json.optInt("score"),
            status = Status.from(json.optString("verdict", "grey")),
            reason = json.optString("rejectionCode").takeIf { it != "null" && it.isNotEmpty() },
            redirectUrl = json.optString("targetUrl").takeIf { it.isNotEmpty() } ?: result.redirectUrl,
            details = details,
        )
    }

    suspend fun resolve(): ConfigData = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("X-App-Token", config.appToken)
            put("X-App-Id", DeviceInfo.appId(context))
            put("User-Agent", DeviceInfo.userAgent())
            put("Accept-Language", DeviceInfo.locale(context))
            put("X-Device-Info", DeviceInfo.model())
            put("X-Device-Codename", DeviceInfo.codename())
            put("X-Build-Product", DeviceInfo.product())
            put("X-OS-Version", DeviceInfo.osVersion())
            val gpu = DeviceInfo.graphicsRenderer()
            if (gpu.isNotBlank()) put("X-Graphics-Info", gpu)
        }

        val result = http.get("/", headers)
        val isActive = result.code == 302 && result.redirectUrl != null && result.redirectUrl != config.fallbackUrl

        ConfigData(
            score = if (isActive) 0 else 100,
            status = if (isActive) Status.ACTIVE else Status.INACTIVE,
            reason = null,
            redirectUrl = result.redirectUrl,
            details = emptyList(),
        )
    }

    private fun log(msg: String) {
        if (config.verbose) Log.d("ConfigProvider", msg)
    }
}
