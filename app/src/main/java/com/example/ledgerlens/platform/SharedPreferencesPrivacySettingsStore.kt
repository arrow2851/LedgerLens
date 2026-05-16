package com.example.ledgerlens.platform

import android.content.Context
import com.example.ledgerlens.domain.privacy.PrivacySettingsStore
import com.example.ledgerlens.domain.privacy.RawSmsRetention

class SharedPreferencesPrivacySettingsStore(
    context: Context
) : PrivacySettingsStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        "ledgerlens_privacy",
        Context.MODE_PRIVATE
    )

    override fun getRawSmsRetention(): RawSmsRetention {
        return preferences.getString(KEY_RAW_SMS_RETENTION, null)
            ?.let { runCatching { RawSmsRetention.valueOf(it) }.getOrNull() }
            ?: RawSmsRetention.KEEP_FOR_AUDIT
    }

    override fun setRawSmsRetention(retention: RawSmsRetention) {
        preferences.edit()
            .putString(KEY_RAW_SMS_RETENTION, retention.name)
            .apply()
    }

    private companion object {
        const val KEY_RAW_SMS_RETENTION = "raw_sms_retention"
    }
}
