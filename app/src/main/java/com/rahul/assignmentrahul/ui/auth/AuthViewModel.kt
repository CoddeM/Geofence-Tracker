package com.rahul.assignmentrahul.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.UserRepository
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading &&
            Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() &&
            password.length >= MIN_PASSWORD_LENGTH &&
            (!isRegisterMode || displayName.isNotBlank())

    companion object {
        const val MIN_PASSWORD_LENGTH = 6 // Firebase Auth minimum
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun toggleMode() = _uiState.update { it.copy(isRegisterMode = !it.isRegisterMode, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val email = state.email.trim()
        val name = state.displayName.trim()

        // This ViewModel is activity-scoped (AuthScreen is outside the NavHost), so this
        // coroutine keeps running after a successful sign-in swaps the UI to the groups screen.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runSuspendCatching {
                if (state.isRegisterMode) {
                    val user = authRepository.register(email, state.password, name)
                    userRepository.saveProfile(user.uid, name, email)
                } else {
                    authRepository.signIn(email, state.password)
                }
            }.onSuccess {
                _uiState.update { AuthUiState(email = email) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.localizedMessage) }
            }
        }
    }
}
