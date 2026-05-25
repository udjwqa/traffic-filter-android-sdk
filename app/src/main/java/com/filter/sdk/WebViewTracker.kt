package com.filter.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.filter.sdk.models.Verdict

class WebViewTracker(
    private val config: FilterConfig,
    private val context: Context,
) {

    interface TrackerCallback {
        fun onMetricsCollected(verdict: Verdict, score: Int)
        fun onError(error: String)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun loadWithTracker(
        webView: WebView,
        url: String,
        callback: TrackerCallback? = null,
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
        }

        webView.addJavascriptInterface(
            TrackerBridge(config, callback),
            "__nativeBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                injectTracker(webView)
            }
        }

        webView.loadUrl(url)
    }

    private fun injectTracker(webView: WebView) {
        val trackerUrl = config.serverUrl
        val script = """
            (function() {
                window.__TRACKER_URL = '$trackerUrl';

                var s = document.createElement('script');
                s.src = '$trackerUrl/tracker.js';
                s.onload = function() {
                    console.log('[SDK] Tracker loaded');
                };
                s.onerror = function() {
                    console.log('[SDK] Tracker failed to load');
                    if (window.__nativeBridge) {
                        window.__nativeBridge.onError('Tracker script failed to load');
                    }
                };
                document.head.appendChild(s);

                window.__TRACKER_CALLBACK = function(metrics) {
                    console.log('[SDK] Metrics collected');
                    if (window.__nativeBridge) {
                        window.__nativeBridge.onMetrics(JSON.stringify(metrics));
                    }
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    class TrackerBridge(
        private val config: FilterConfig,
        private val callback: TrackerCallback?,
    ) {
        @JavascriptInterface
        fun onMetrics(metricsJson: String) {
            try {
                val json = org.json.JSONObject(metricsJson)
                val score = json.optInt("score", -1)

                if (config.debug) {
                    Log.d("WebViewTracker", "Metrics received: ${metricsJson.take(200)}...")
                }

                callback?.onMetricsCollected(Verdict.GREY, score)
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e("WebViewTracker", "Error parsing metrics: ${e.message}")
                }
                callback?.onError(e.message ?: "Unknown error")
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            if (config.debug) {
                Log.e("WebViewTracker", "Tracker error: $error")
            }
            callback?.onError(error)
        }
    }
}
