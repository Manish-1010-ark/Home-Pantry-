package com.example.homepantry.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homepantry.data.InventoryRepository
import com.example.homepantry.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    object Checking : LoginState()
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val houseId: Long, val houseName: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val TAG = "AuthViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Checking)
    val loginState = _loginState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private var hasCheckedSession = false

    init {
        checkSavedSession()
    }

    private fun checkSavedSession() {
        if (hasCheckedSession) return
        hasCheckedSession = true

        viewModelScope.launch {
            try {
                Log.d(TAG, "Checking for saved session...")

                prefsRepository.houseInfoFlow.firstOrNull()?.let { (houseId, pin, houseName) ->
                    if (houseId != null && pin != null && houseName != null) {
                        Log.d(TAG, "Found saved session for house: $houseName (ID: $houseId)")
                        _loginState.value = LoginState.Success(houseId, houseName)
                    } else {
                        Log.d(
                            TAG,
                            "Incomplete saved session data. houseId=$houseId, pin=${pin?.take(2)}***, houseName=$houseName"
                        )
                        _loginState.value = LoginState.Idle
                    }
                } ?: run {
                    Log.d(TAG, "No saved session found.")
                    _loginState.value = LoginState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking saved session: ${e.message}", e)
                _loginState.value = LoginState.Idle
            }
        }
    }

    fun login(pin: String) {
        // Input validation
        if (pin.isBlank()) {
            Log.w(TAG, "Login attempt with empty PIN")
            _loginState.value = LoginState.Error("PIN cannot be empty.")
            return
        }

        val trimmedPin = pin.trim()
        Log.d(
            TAG,
            "Login attempt with PIN: ${trimmedPin.take(2)}*** (length: ${trimmedPin.length})"
        )
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "Querying Firestore for house with PIN...")
                val house = repository.getHouseForPin(trimmedPin)

                if (house != null) {
                    Log.d(TAG, "House found: $house")

                    // Validate house data completeness
                    if (!house.isValid()) {
                        Log.e(TAG, "Invalid house data retrieved: $house")
                        _loginState.value = LoginState.Error(
                            "Invalid house data received. Please contact support."
                        )
                        showSnackbar("Invalid house data. Please contact support.")
                        return@launch
                    }

                    // Save to preferences
                    Log.d(TAG, "Saving house info to preferences...")
                    try {
                        prefsRepository.saveHouseInfo(house.id, house.pin, house.house_name)
                        Log.d(TAG, "Successfully saved house info to preferences")
                    } catch (prefException: Exception) {
                        Log.e(TAG, "Failed to save house info to preferences", prefException)
                        _loginState.value = LoginState.Error(
                            "Failed to save login data. Please try again."
                        )
                        showSnackbar("Failed to save login. Please try again.")
                        return@launch
                    }

                    // Update state to success
                    _loginState.value = LoginState.Success(house.id, house.house_name)
                    Log.d(TAG, "Login successful for house: ${house.house_name}")
                    showSnackbar("Welcome to ${house.house_name}!")

                } else {
                    Log.w(TAG, "No house found with the provided PIN")
                    _loginState.value = LoginState.Error(
                        "Invalid PIN. Please check and try again."
                    )
                    showSnackbar("Invalid PIN. Please try again.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.javaClass.simpleName} - ${e.message}", e)
                e.printStackTrace()

                // Detailed error mapping
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> {
                        "Access denied. Please check Firestore security rules."
                    }

                    e.message?.contains("UNAVAILABLE", ignoreCase = true) == true -> {
                        "Service unavailable. Please check your internet connection."
                    }

                    e.message?.contains("DEADLINE_EXCEEDED", ignoreCase = true) == true ||
                            e.message?.contains("timeout", ignoreCase = true) == true -> {
                        "Connection timeout. Please try again."
                    }

                    e.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true -> {
                        "Authentication failed. Please check your Firebase configuration."
                    }

                    e.message?.contains("network", ignoreCase = true) == true -> {
                        "Network error. Please check your connection."
                    }

                    e is com.google.firebase.FirebaseNetworkException -> {
                        "No internet connection. Please check your network."
                    }

                    else -> {
                        "Login failed: ${e.message ?: "Unknown error occurred"}"
                    }
                }

                _loginState.value = LoginState.Error(errorMessage)
                showSnackbar(errorMessage)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Logging out...")

                // Clean up repository resources
                repository.cleanup()

                // Clear preferences
                prefsRepository.clearHouseInfo()

                // Reset state
                _loginState.value = LoginState.Idle
                hasCheckedSession = false

                Log.d(TAG, "Logout successful")
                showSnackbar("Logged out successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error during logout: ${e.message}", e)
                showSnackbar("Logout failed. Please try again.")
            }
        }
    }

    fun getCurrentHouseId(): Long? {
        return if (loginState.value is LoginState.Success) {
            (loginState.value as LoginState.Success).houseId
        } else {
            null
        }
    }

    fun getCurrentHouseName(): String {
        return if (loginState.value is LoginState.Success) {
            (loginState.value as LoginState.Success).houseName
        } else {
            "My House"
        }
    }

    private suspend fun showSnackbar(message: String) {
        try {
            _snackbarMessages.emit(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing snackbar: ${e.message}", e)
        }
    }

    fun resetErrorState() {
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared")
    }
}