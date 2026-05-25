package com.filter.sdk

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.filter.sdk.models.FilterResult
import com.filter.sdk.models.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrafficFilter private constructor(
    private val config: FilterConfig,
    private val context: Context,
) {
    private val gateway = GatewayClient(config, context)
    private val integrity = IntegrityClient(config, context)
    private val tracker = WebViewTracker(config, context)

    /**
     * Полная проверка: Gateway → Play Integrity → результат.
     *
     * Вызывай из корутины. Возвращает FilterResult с вердиктом
     * и URL куда направить юзера.
     */
    suspend fun checkTraffic(): FilterResult = withContext(Dispatchers.IO) {
        log("=== Starting traffic filter ===")

        // --- Шаг 1: Gateway (статические заголовки + IP) ---
        log("[1/2] Gateway check...")
        val gatewayResult = gateway.check()
        log("[1/2] Gateway: verdict=${gatewayResult.verdict} score=${gatewayResult.score}")

        if (gatewayResult.verdict == Verdict.WHITE) {
            log("BLOCKED at gateway: ${gatewayResult.rejectionCode}")
            return@withContext FilterResult(
                verdict = Verdict.WHITE,
                targetUrl = config.safeUrl,
                score = gatewayResult.score,
                rejectionCode = gatewayResult.rejectionCode,
                gatewayPassed = false,
                integrityPassed = false,
                details = gatewayResult.details,
            )
        }

        // --- Шаг 2: Play Integrity ---
        if (config.enablePlayIntegrity) {
            log("[2/2] Play Integrity check...")
            val integrityResult = integrity.verify()
            log("[2/2] Integrity: verdict=${integrityResult.verdict} score=${integrityResult.score}")

            if (integrityResult.verdict == Verdict.WHITE) {
                log("BLOCKED at integrity: ${integrityResult.rejectionCode}")
                return@withContext FilterResult(
                    verdict = Verdict.WHITE,
                    targetUrl = config.safeUrl,
                    score = integrityResult.score,
                    rejectionCode = integrityResult.rejectionCode,
                    gatewayPassed = true,
                    integrityPassed = false,
                    details = gatewayResult.details,
                )
            }
        } else {
            log("[2/2] Play Integrity disabled, skipping")
        }

        // --- Все проверки пройдены ---
        val offerUrl = config.targetUrl
            ?: gatewayResult.redirectUrl
            ?: config.safeUrl

        log("PASSED! Redirecting to: $offerUrl")

        FilterResult(
            verdict = Verdict.GREY,
            targetUrl = offerUrl,
            score = gatewayResult.score,
            rejectionCode = null,
            gatewayPassed = true,
            integrityPassed = true,
            details = gatewayResult.details,
        )
    }

    /**
     * Загрузить оффер-страницу в WebView с трекером.
     *
     * Вызывай ПОСЛЕ checkTraffic() если вердикт = GREY.
     * Трекер автоматически соберёт метрики устройства и отправит на сервер.
     */
    fun loadOffer(
        webView: WebView,
        url: String,
        callback: WebViewTracker.TrackerCallback? = null,
    ) {
        log("Loading offer with tracker: $url")
        tracker.loadWithTracker(webView, url, callback)
    }

    /**
     * Быстрая проверка только через gateway redirect (без debug-скоринга).
     * Используй если не нужны подробности — просто серый/белый.
     */
    suspend fun quickCheck(): FilterResult = withContext(Dispatchers.IO) {
        val result = gateway.checkRedirect()
        FilterResult(
            verdict = result.verdict,
            targetUrl = if (result.verdict == Verdict.GREY) {
                result.redirectUrl ?: config.safeUrl
            } else {
                config.safeUrl
            },
            score = result.score,
            gatewayPassed = result.verdict == Verdict.GREY,
        )
    }

    private fun log(msg: String) {
        if (config.debug) Log.d("TrafficFilter", msg)
    }

    companion object {
        @Volatile
        private var instance: TrafficFilter? = null

        /**
         * Инициализировать SDK. Вызови один раз в Application.onCreate().
         */
        fun init(context: Context, config: FilterConfig): TrafficFilter {
            return TrafficFilter(config, context.applicationContext).also {
                instance = it
            }
        }

        /**
         * Получить инициализированный инстанс SDK.
         */
        fun getInstance(): TrafficFilter {
            return instance ?: throw IllegalStateException(
                "TrafficFilter not initialized. Call TrafficFilter.init() first."
            )
        }
    }
}
