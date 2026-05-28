package com.filter.sdk

import android.content.Context
import android.util.Log
import com.filter.sdk.models.IntegrityResult
import com.filter.sdk.models.NonceResponse
import com.filter.sdk.models.Verdict
import com.filter.sdk.utils.DeviceInfo
import com.filter.sdk.utils.HttpClient
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class IntegrityClient(
    private val config: FilterConfig,
    private val context: Context,
) {
    private val http = HttpClient(config)
    private val integrityManager = IntegrityManagerFactory.create(context)

    suspend fun verify(): IntegrityResult = withContext(Dispatchers.IO) {
        try {
            // Шаг 1: Получить nonce с сервера
            log("Requesting nonce...")
            val nonce = requestNonce()
            if (nonce == null) {
                log("Failed to get nonce")
                return@withContext IntegrityResult(
                    verified = false, score = 0,
                    verdict = Verdict.GREY, rejectionCode = null,
                    error = "Failed to get nonce from server",
                )
            }
            log("Got nonce: ${nonce.nonce.take(16)}... (TTL=${nonce.ttl}s)")

            // Шаг 2: Запросить integrity token у Google
            log("Requesting integrity token from Google...")
            val integrityToken = requestIntegrityToken(nonce.nonce)
            if (integrityToken == null) {
                log("Failed to get integrity token from Google")
                return@withContext IntegrityResult(
                    verified = false, score = 0,
                    verdict = Verdict.GREY, rejectionCode = null,
                    error = "Play Integrity API unavailable",
                )
            }
            log("Got integrity token (${integrityToken.length} chars)")

            // Шаг 3: Отправить токен на сервер для верификации
            log("Sending token to server for verification...")
            val result = verifyToken(integrityToken, nonce.nonce)
            log("Integrity result: verdict=${result.verdict} score=${result.score}")

            result
        } catch (e: Exception) {
            log("Integrity check failed: ${e.message}")
            IntegrityResult(
                verified = false, score = 0,
                verdict = Verdict.GREY, rejectionCode = null,
                error = e.message,
            )
        }
    }

    private fun requestNonce(): NonceResponse? {
        val result = http.get("/api/integrity/nonce")
        if (!result.success || result.json == null) return null

        return NonceResponse(
            nonce = result.json.optString("nonce"),
            ttl = result.json.optInt("ttl", 300),
        )
    }

    private suspend fun requestIntegrityToken(nonce: String): String? {
        return suspendCancellableCoroutine { cont ->
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()

            integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    cont.resume(response.token())
                }
                .addOnFailureListener { e ->
                    log("Play Integrity API error: ${e.message}")
                    cont.resume(null)
                }
        }
    }

    private fun verifyToken(integrityToken: String, nonce: String): IntegrityResult {
        val body = JSONObject().apply {
            put("integrityToken", integrityToken)
            put("nonce", nonce)
        }

        val headers = mapOf("X-Package-Name" to DeviceInfo.getPackageName(context))
        val result = http.post("/api/integrity/verify", body, headers)

        if (result.json == null) {
            return IntegrityResult(
                verified = false, score = 0,
                verdict = Verdict.GREY, rejectionCode = null,
                error = "Server returned invalid response: ${result.body}",
            )
        }

        val json = result.json
        return IntegrityResult(
            verified = json.optBoolean("verified", false),
            score = json.optInt("score", 0),
            verdict = Verdict.from(json.optString("verdict", "grey")),
            rejectionCode = json.optString("rejectionCode").takeIf { it != "null" && it.isNotEmpty() },
            error = json.optString("error").takeIf { it != "null" && it.isNotEmpty() },
        )
    }

    private fun log(msg: String) {
        if (config.debug) Log.d("IntegrityClient", msg)
    }
}
