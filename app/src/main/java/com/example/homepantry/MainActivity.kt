package com.example.homepantry

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.homepantry.data.UserPreferencesRepository
import com.example.homepantry.ui.AppNavigation
import com.example.homepantry.ui.theme.HomePantryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val TAG = "MainActivity@Log"

    override fun attachBaseContext(newBase: Context) {
        // ✅ CRITICAL: Apply locale BEFORE Activity is created
        val context = runBlocking {
            val repo = newBase.applicationContext
                .let { it as? HomePantryApplication }
                ?.userPreferencesRepository

            if (repo != null) {
                val language = repo.appLanguage.first()
                Log.d(TAG, "attachBaseContext: Applying language '$language'")
                applyLocale(newBase, language)
            } else {
                Log.w(TAG, "attachBaseContext: Repository not available, using default")
                newBase
            }
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "MainActivity onCreate START")

        // Verify locale is applied
        val currentLocale = resources.configuration.locales[0]
        Log.d(TAG, "Current locale: ${currentLocale.language} (${currentLocale.displayLanguage})")

        // Test string
        val testString = getString(R.string.app_name)
        Log.d(TAG, "TEST STRING (app_name): '$testString'")
        Log.d(TAG, "════════════════════════════════════════")

        enableEdgeToEdge()

        setContent {
            HomePantryTheme {
                AppNavigation()
            }
        }
    }

    private fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}