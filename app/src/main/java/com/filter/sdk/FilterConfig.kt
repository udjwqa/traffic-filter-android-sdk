package com.filter.sdk

data class FilterConfig(
    val serverUrl: String,
    val clientSecret: String,
    val safeUrl: String,
    val targetUrl: String? = null,
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 15_000,
    val enablePlayIntegrity: Boolean = true,
    val enableJsTracker: Boolean = true,
    val debug: Boolean = false,
)
