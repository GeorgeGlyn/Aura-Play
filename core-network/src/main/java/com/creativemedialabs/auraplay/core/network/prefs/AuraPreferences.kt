package com.creativemedialabs.auraplay.core.network.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_play_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEBRID_API_KEY = "debrid_api_key"
        private const val KEY_DEBRID_SERVICE = "debrid_service" // "realdebrid", "torbox", "alldebrid"
        private const val KEY_QUALITY_PREFERENCE = "quality_preference" // "4k", "1080p", "720p"
    }

    var debridApiKey: String?
        get() = prefs.getString(KEY_DEBRID_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_DEBRID_API_KEY, value).apply()

    var debridService: String
        get() = prefs.getString(KEY_DEBRID_SERVICE, "realdebrid") ?: "realdebrid"
        set(value) = prefs.edit().putString(KEY_DEBRID_SERVICE, value).apply()

    var qualityPreference: String
        get() = prefs.getString(KEY_QUALITY_PREFERENCE, "1080p") ?: "1080p"
        set(value) = prefs.edit().putString(KEY_QUALITY_PREFERENCE, value).apply()
}
