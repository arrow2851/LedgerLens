package com.example.ledgerlens.platform

import android.content.Context
import com.example.ledgerlens.domain.sync.SmsSyncCursorStore

class SharedPreferencesSmsSyncCursorStore(
    context: Context
) : SmsSyncCursorStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        "ledgerlens_sync",
        Context.MODE_PRIVATE
    )

    override fun getLastSuccessfulRefreshEpochMs(): Long? {
        val value = preferences.getLong(KEY_LAST_SUCCESSFUL_REFRESH, 0L)
        return value.takeIf { it > 0L }
    }

    override fun setLastSuccessfulRefreshEpochMs(epochMs: Long) {
        preferences.edit()
            .putLong(KEY_LAST_SUCCESSFUL_REFRESH, epochMs)
            .apply()
    }

    private companion object {
        const val KEY_LAST_SUCCESSFUL_REFRESH = "last_successful_refresh_epoch_ms"
    }
}
