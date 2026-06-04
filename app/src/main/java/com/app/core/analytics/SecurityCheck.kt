package com.app.core.analytics

import android.content.Context
import android.util.Log
import com.app.core.analytics.models.SecurityResult
import com.app.core.analytics.models.TokenResponse
import com.app.core.analytics.models.Status
import com.app.core.analytics.utils.DeviceInfo
import com.app.core.analytics.utils.HttpClient
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class SecurityCheck(
    private val config: AnalyticsConfig,
    private val context: Context,
) {
    private val http = HttpClient(config)
    private val manager = IntegrityManagerFactory.create(context)

    suspend fun verify(): SecurityResult = withContext(Dispatchers.IO) {
        try {
            val tokenResp = requestToken()
            if (tokenResp == null) {
                return@withContext SecurityResult(false, 0, Status.ACTIVE, null, "Token unavailable")
            }

            val securityToken = requestSecurityToken(tokenResp.token)
            if (securityToken == null) {
                return@withContext SecurityResult(false, 0, Status.ACTIVE, null, "Security API unavailable")
            }

            validateToken(securityToken, tokenResp.token)
        } catch (e: Exception) {
            log("Security check failed: ${e.message}")
            SecurityResult(false, 0, Status.ACTIVE, null, e.message)
        }
    }

    private fun requestToken(): TokenResponse? {
        val result = http.get("/api/security/token")
        if (!result.success || result.json == null) return null
        return TokenResponse(result.json.optString("nonce"), result.json.optInt("ttl", 300))
    }

    private suspend fun requestSecurityToken(nonce: String): String? {
        return suspendCancellableCoroutine { cont ->
            val request = IntegrityTokenRequest.builder().setNonce(nonce).build()
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { cont.resume(it.token()) }
                .addOnFailureListener { log("API error: ${it.message}"); cont.resume(null) }
        }
    }

    private fun validateToken(token: String, nonce: String): SecurityResult {
        val body = JSONObject().apply {
            put("integrityToken", token)
            put("nonce", nonce)
        }
        val headers = mapOf("X-App-Id" to DeviceInfo.appId(context))
        val result = http.post("/api/security/validate", body, headers)

        if (result.json == null) {
            return SecurityResult(false, 0, Status.ACTIVE, null, "Invalid response")
        }

        val json = result.json
        return SecurityResult(
            verified = json.optBoolean("verified", false),
            score = json.optInt("score", 0),
            status = Status.from(json.optString("verdict", "grey")),
            reason = json.optString("rejectionCode").takeIf { it != "null" && it.isNotEmpty() },
            error = json.optString("error").takeIf { it != "null" && it.isNotEmpty() },
        )
    }

    private fun log(msg: String) {
        if (config.verbose) Log.d("SecurityCheck", msg)
    }
}
