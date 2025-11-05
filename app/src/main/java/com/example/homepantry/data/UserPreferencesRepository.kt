package com.example.homepantry.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Create a DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {

    // Define keys for storing data
    private val HOUSE_ID_KEY = longPreferencesKey("house_id")
    private val HOUSE_PIN_KEY = stringPreferencesKey("house_pin")
    private val HOUSE_NAME_KEY = stringPreferencesKey("house_name")

    private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE_KEY] ?: "en"
    }

    val APP_THEME = stringPreferencesKey("app_theme")

    val appTheme: Flow<String> = context.dataStore.data // <-- Also add 'context.'
        .map { preferences ->
            preferences[APP_THEME] ?: "light" // <-- Fixed
        }

    suspend fun saveAppTheme(theme: String) {
        context.dataStore.edit { preferences -> // <-- Also add 'context.'
            preferences[APP_THEME] = theme // <-- Fixed
        }
    }

    // A Flow that emits the saved house info whenever it changes
    // Updated to include house name as third element in Triple
    val houseInfoFlow: Flow<Triple<Long?, String?, String?>> =
        context.dataStore.data.map { preferences ->
            Triple(
                preferences[HOUSE_ID_KEY],
                preferences[HOUSE_PIN_KEY],
                preferences[HOUSE_NAME_KEY]
            )
        }

    // Function to save the house ID, PIN, and NAME after a successful login
    suspend fun saveHouseInfo(houseId: Long, pin: String, houseName: String) {
        context.dataStore.edit { preferences ->
            preferences[HOUSE_ID_KEY] = houseId
            preferences[HOUSE_PIN_KEY] = pin
            preferences[HOUSE_NAME_KEY] = houseName
        }
    }

    // Function to clear the data on logout
    suspend fun clearHouseInfo() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun saveAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_KEY] = language
        }
    }
}