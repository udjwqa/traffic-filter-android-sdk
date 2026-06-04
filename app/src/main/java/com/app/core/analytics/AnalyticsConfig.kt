package com.app.core.analytics

class AnalyticsConfig(
    val endpoint: String,
    val appToken: String,
    val fallbackUrl: String,
    val contentUrl: String? = null,
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 15_000,
    val enableSecurityCheck: Boolean = true,
    val enableContentTracking: Boolean = true,
    val verbose: Boolean = false,
)
