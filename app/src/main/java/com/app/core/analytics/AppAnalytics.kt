package com.app.core.analytics

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.app.core.analytics.models.ConfigResult
import com.app.core.analytics.models.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppAnalytics private constructor(
    private val config: AnalyticsConfig,
    private val context: Context,
) {
    private val provider = ConfigProvider(config, context)
    private val security = SecurityCheck(config, context)
    private val renderer = ContentRenderer(config, context)

    suspend fun initialize(): ConfigResult = withContext(Dispatchers.IO) {
        log("Starting initialization...")

        val configData = provider.fetch()
        log("Config: status=${configData.status} score=${configData.score}")

        if (configData.status == Status.INACTIVE) {
            log("Config check: inactive")
            return@withContext ConfigResult(
                status = Status.INACTIVE,
                contentUrl = config.fallbackUrl,
                score = configData.score,
                reason = configData.reason,
                configReady = false,
                securityPassed = false,
                details = configData.details,
            )
        }

        if (config.enableSecurityCheck) {
            log("Running security check...")
            val secResult = security.verify()
            log("Security: status=${secResult.status} score=${secResult.score}")

            if (secResult.status == Status.INACTIVE) {
                log("Security check: inactive")
                return@withContext ConfigResult(
                    status = Status.INACTIVE,
                    contentUrl = config.fallbackUrl,
                    score = secResult.score,
                    reason = secResult.reason,
                    configReady = true,
                    securityPassed = false,
                    details = configData.details,
                )
            }
        }

        val url = config.contentUrl ?: configData.redirectUrl ?: config.fallbackUrl
        log("Ready: url=$url")

        ConfigResult(
            status = Status.ACTIVE,
            contentUrl = url,
            score = configData.score,
            reason = null,
            configReady = true,
            securityPassed = true,
            details = configData.details,
        )
    }

    fun renderContent(
        webView: WebView,
        url: String,
        callback: ContentRenderer.Callback? = null,
    ) {
        log("Rendering content: $url")
        renderer.render(webView, url, callback)
    }

    suspend fun preload(): ConfigResult = withContext(Dispatchers.IO) {
        val result = provider.resolve()
        ConfigResult(
            status = result.status,
            contentUrl = if (result.status == Status.ACTIVE) {
                result.redirectUrl ?: config.fallbackUrl
            } else config.fallbackUrl,
            score = result.score,
            configReady = result.status == Status.ACTIVE,
        )
    }

    private fun log(msg: String) {
        if (config.verbose) Log.d("AppAnalytics", msg)
    }

    companion object {
        @Volatile
        private var instance: AppAnalytics? = null

        fun init(context: Context, config: AnalyticsConfig): AppAnalytics {
            return AppAnalytics(config, context.applicationContext).also { instance = it }
        }

        fun getInstance(): AppAnalytics {
            return instance ?: throw IllegalStateException(
                "AppAnalytics not initialized. Call AppAnalytics.init() first."
            )
        }
    }
}
