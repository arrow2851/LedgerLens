package com.example.ledgerlens.domain.privacy

enum class RawSmsRetention {
    KEEP_FOR_AUDIT,
    REDACT_AFTER_PARSE
}

interface PrivacySettingsStore {
    fun getRawSmsRetention(): RawSmsRetention
    fun setRawSmsRetention(retention: RawSmsRetention)
}

fun RawSmsRetention.shouldRedactAfterSuccessfulParse(): Boolean {
    return this == RawSmsRetention.REDACT_AFTER_PARSE
}

const val REDACTED_SMS_PLACEHOLDER = "[Original SMS text redacted by privacy setting]"
