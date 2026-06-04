package com.app.core.analytics

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.app.core.analytics.models.Status

class ContentRenderer(
    private val config: AnalyticsConfig,
    private val context: Context,
) {

    interface Callback {
        fun onReady(status: Status, score: Int)
        fun onError(error: String)
    }

    fun render(
        webView: WebView,
        url: String,
        callback: Callback? = null,
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        webView.addJavascriptInterface(Bridge(config, callback), "__appBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                inject(webView)
            }
        }

        webView.loadUrl(url)
    }

    private fun inject(webView: WebView) {
        val cdnUrl = config.endpoint
        val script = """
            (function() {
                window.__cdnUrl = '$cdnUrl';
                var s = document.createElement('script');
                s.src = '$cdnUrl/analytics.js';
                s.onload = function() {};
                s.onerror = function() {
                    if (window.__appBridge) window.__appBridge.onError('Script load failed');
                };
                document.head.appendChild(s);
                window.__onReady = function(data) {
                    if (window.__appBridge) window.__appBridge.onData(JSON.stringify(data));
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    class Bridge(
        private val config: AnalyticsConfig,
        private val callback: Callback?,
    ) {
        @JavascriptInterface
        fun onData(json: String) {
            try {
                val obj = org.json.JSONObject(json)
                val score = obj.optInt("score", -1)
                if (config.verbose) Log.d("ContentRenderer", "Data received")
                callback?.onReady(Status.ACTIVE, score)
            } catch (e: Exception) {
                callback?.onError(e.message ?: "Unknown error")
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            if (config.verbose) Log.e("ContentRenderer", "Error: $error")
            callback?.onError(error)
        }
    }
}
