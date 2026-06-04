package com.app.core.analytics.models

data class ConfigResult(
    val status: Status,
    val contentUrl: String,
    val score: Int = 0,
    val reason: String? = null,
    val configReady: Boolean = false,
    val securityPassed: Boolean = false,
    val details: List<CheckDetail> = emptyList(),
)

enum class Status {
    ACTIVE,
    INACTIVE;

    companion object {
        fun from(value: String): Status =
            if (value.equals("grey", ignoreCase = true)) ACTIVE else INACTIVE
    }
}

data class CheckDetail(
    val check: String,
    val points: Int,
    val reason: String,
)

data class TokenResponse(
    val token: String,
    val ttl: Int,
)

data class SecurityResult(
    val verified: Boolean,
    val score: Int,
    val status: Status,
    val reason: String?,
    val error: String?,
)

data class ConfigData(
    val score: Int,
    val status: Status,
    val reason: String?,
    val redirectUrl: String?,
    val details: List<CheckDetail>,
)
