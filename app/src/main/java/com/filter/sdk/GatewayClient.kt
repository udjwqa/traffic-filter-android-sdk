package com.filter.sdk

import android.content.Context
import android.util.Log
import com.filter.sdk.models.GatewayResult
import com.filter.sdk.models.ScoringDetail
import com.filter.sdk.models.Verdict
import com.filter.sdk.utils.DeviceInfo
import com.filter.sdk.utils.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GatewayClient(
    private val config: FilterConfig,
    private val context: Context,
) {
    private val http = HttpClient(config)

    suspend fun check(): GatewayResult = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("X-Client-Secret", config.clientSecret)
            put("X-Package-Name", DeviceInfo.getPackageName(context))
            put("User-Agent", DeviceInfo.getUserAgent())
            put("Accept-Language", DeviceInfo.getAcceptLanguage(context))
            put("X-Device-Model", DeviceInfo.getDeviceModel())
            put("X-Device-Codename", DeviceInfo.getDeviceCodename())
            put("X-Build-Product", DeviceInfo.getBuildProduct())
            put("X-OS-Version", DeviceInfo.getOsVersion())

            val gpu = DeviceInfo.getGpuRenderer()
            if (gpu.isNotBlank()) put("X-GPU-Renderer", gpu)
        }

        val result = http.get("/score-debug", headers)

        if (!result.success || result.json == null) {
            log("Gateway request failed: code=${result.code} body=${result.body}")
            return@withContext GatewayResult(
                score = 0,
                verdict = Verdict.GREY,
                rejectionCode = null,
                redirectUrl = null,
                details = emptyList(),
            )
        }

        val json = result.json
        val details = mutableListOf<ScoringDetail>()
        val detailsArr = json.optJSONArray("details")
        if (detailsArr != null) {
            for (i in 0 until detailsArr.length()) {
                val d = detailsArr.getJSONObject(i)
                details.add(
                    ScoringDetail(
                        check = d.optString("check"),
                        points = d.optInt("points"),
                        reason = d.optString("reason"),
                    )
                )
            }
        }

        GatewayResult(
            score = json.optInt("score"),
            verdict = Verdict.from(json.optString("verdict", "grey")),
            rejectionCode = json.optString("rejectionCode").takeIf { it != "null" && it.isNotEmpty() },
            redirectUrl = json.optString("targetUrl").takeIf { it.isNotEmpty() } ?: result.redirectUrl,
            details = details,
        )
    }

    suspend fun checkRedirect(): GatewayResult = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("X-Client-Secret", config.clientSecret)
            put("X-Package-Name", DeviceInfo.getPackageName(context))
            put("User-Agent", DeviceInfo.getUserAgent())
            put("Accept-Language", DeviceInfo.getAcceptLanguage(context))
            put("X-Device-Model", DeviceInfo.getDeviceModel())
            put("X-Device-Codename", DeviceInfo.getDeviceCodename())
            put("X-Build-Product", DeviceInfo.getBuildProduct())
            put("X-OS-Version", DeviceInfo.getOsVersion())

            val gpu = DeviceInfo.getGpuRenderer()
            if (gpu.isNotBlank()) put("X-GPU-Renderer", gpu)
        }

        val result = http.get("/", headers)

        val isGrey = result.code == 302 &&
                result.redirectUrl != null &&
                result.redirectUrl != config.safeUrl

        GatewayResult(
            score = if (isGrey) 0 else 100,
            verdict = if (isGrey) Verdict.GREY else Verdict.WHITE,
            rejectionCode = null,
            redirectUrl = result.redirectUrl,
            details = emptyList(),
        )
    }

    private fun log(msg: String) {
        if (config.debug) Log.d("GatewayClient", msg)
    }
}
