package com.filter.sdk.models

data class FilterResult(
    val verdict: Verdict,
    val targetUrl: String,
    val score: Int = 0,
    val rejectionCode: String? = null,
    val gatewayPassed: Boolean = false,
    val integrityPassed: Boolean = false,
    val details: List<ScoringDetail> = emptyList(),
)

enum class Verdict {
    GREY,
    WHITE;

    companion object {
        fun from(value: String): Verdict =
            if (value.equals("grey", ignoreCase = true)) GREY else WHITE
    }
}

data class ScoringDetail(
    val check: String,
    val points: Int,
    val reason: String,
)

data class NonceResponse(
    val nonce: String,
    val ttl: Int,
)

data class IntegrityResult(
    val verified: Boolean,
    val score: Int,
    val verdict: Verdict,
    val rejectionCode: String?,
    val error: String?,
)

data class GatewayResult(
    val score: Int,
    val verdict: Verdict,
    val rejectionCode: String?,
    val redirectUrl: String?,
    val details: List<ScoringDetail>,
)
