package com.livemap.app.data.pref

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "live_map_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val PAIR_CODE_KEY = stringPreferencesKey("pair_code")
        private val MY_USER_ID_KEY = stringPreferencesKey("my_user_id")
        private val MY_NAME_KEY = stringPreferencesKey("my_name")
        private val PARTNER_NAME_KEY = stringPreferencesKey("partner_name")
        private val IS_SHARING_ACTIVE_KEY = booleanPreferencesKey("is_sharing_active")
        private val MAP_PROVIDER_KEY = stringPreferencesKey("map_provider")

        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

        const val DEFAULT_USER_A = "USER_A"
        const val DEFAULT_USER_B = "USER_B"
        const val MAP_PROVIDER_OSM = "OSM"
        const val MAP_PROVIDER_GOOGLE = "GOOGLE"

        fun generateRandomPairCode(): String {
            val randomNum = (1000..9999).random()
            return "LM-$randomNum"
        }
    }

    val mapProviderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MAP_PROVIDER_KEY] ?: MAP_PROVIDER_OSM
    }

    val pairCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PAIR_CODE_KEY] ?: ""
    }

    suspend fun ensurePairCodeInitialized(): String {
        val prefs = context.dataStore.data.first()
        val existing = prefs[PAIR_CODE_KEY]
        if (existing.isNullOrBlank() || existing == "live_couple_8888") {
            val newCode = generateRandomPairCode()
            savePairCode(newCode)
            return newCode
        }
        return existing
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.dataStore.data.first()
        val existing = prefs[DEVICE_ID_KEY]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val newId = "DEV_" + java.util.UUID.randomUUID().toString().take(8).uppercase()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

    val myUserIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MY_USER_ID_KEY] ?: DEFAULT_USER_A
    }

    val myNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MY_NAME_KEY] ?: "Tôi"
    }

    val partnerNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PARTNER_NAME_KEY] ?: "Người ấy"
    }

    val isSharingActiveFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SHARING_ACTIVE_KEY] ?: false
    }

    suspend fun savePairCode(code: String) {
        context.dataStore.edit {
            it[PAIR_CODE_KEY] = code.trim()
        }
    }

    suspend fun saveMyUserId(userId: String) {
        context.dataStore.edit { it[MY_USER_ID_KEY] = userId.trim() }
    }

    suspend fun saveMyName(name: String) {
        context.dataStore.edit { it[MY_NAME_KEY] = name.trim() }
    }

    suspend fun savePartnerName(name: String) {
        context.dataStore.edit { it[PARTNER_NAME_KEY] = name.trim() }
    }

    suspend fun saveSharingActive(active: Boolean) {
        context.dataStore.edit { it[IS_SHARING_ACTIVE_KEY] = active }
    }

    suspend fun saveMapProvider(provider: String) {
        context.dataStore.edit { it[MAP_PROVIDER_KEY] = provider }
    }
}
