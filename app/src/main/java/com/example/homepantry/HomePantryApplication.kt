package com.example.homepantry

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.example.homepantry.data.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class HomePantryApplication : Application() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val TAG = "HomePantryApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // Apply saved language globally
        runBlocking {
            val language = userPreferencesRepository.appLanguage.first()
            Log.d(TAG, "Applying global language: '$language'")
            applyLocale(language)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d(TAG, "Application attachBaseContext")
    }

    private fun applyLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)

        Log.d(TAG, "Global locale set to: ${Locale.getDefault().language}")
    }
}