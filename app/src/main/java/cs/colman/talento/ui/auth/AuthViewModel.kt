package cs.colman.talento.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cs.colman.talento.TAG
import cs.colman.talento.data.local.AppDatabase
import cs.colman.talento.data.repository.UserRepository
import cs.colman.talento.utils.NetworkResult
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val userRepository = UserRepository(userDao, context = getApplication())

    private val _loginState = MutableLiveData<NetworkResult<String>>()
    val loginState: LiveData<NetworkResult<String>> = _loginState

    private val _registerState = MutableLiveData<NetworkResult<String>>()
    val registerState: LiveData<NetworkResult<String>> = _registerState

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = NetworkResult.Loading
            try {
                val result = userRepository.loginUser(email, password)
                _loginState.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error in login")
                _loginState.value = NetworkResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun registerUser(fullName: String, email: String, phone: String, password: String) {
        viewModelScope.launch {
            _registerState.value = NetworkResult.Loading
            try {
                val result = userRepository.registerUser(fullName, email, phone, password)
                _registerState.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error in registration")
                _registerState.value = NetworkResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
