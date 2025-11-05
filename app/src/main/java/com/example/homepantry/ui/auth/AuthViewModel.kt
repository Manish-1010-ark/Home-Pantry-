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
            prefsRepository.houseInfoFlow.firstOrNull()?.let { (houseId, pin, houseName) ->
                if (houseId != null && pin != null && houseName != null) {
                    Log.d(TAG, "Found saved session for house: $houseName (ID: $houseId)")
                    _loginState.value = LoginState.Success(houseId, houseName)
                } else {
                    Log.d(TAG, "No saved session found.")
                    _loginState.value = LoginState.Idle
                }
            } ?: run {
                _loginState.value = LoginState.Idle
            }
        }
    }

    fun login(pin: String) {
        if (pin.isBlank()) {
            _loginState.value = LoginState.Error("PIN cannot be empty.")
            return
        }
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val house = repository.getHouseForPin(pin)
            if (house != null) {
                prefsRepository.saveHouseInfo(house.id, house.pin, house.house_name)
                _loginState.value = LoginState.Success(house.id, house.house_name)
            } else {
                _loginState.value = LoginState.Error("Invalid PIN. Please try again.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repository.cleanup()
                prefsRepository.clearHouseInfo()
                _loginState.value = LoginState.Idle
                hasCheckedSession = false
                showSnackbar("Logged out successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during logout", e)
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
        _snackbarMessages.emit(message)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared")
    }
}