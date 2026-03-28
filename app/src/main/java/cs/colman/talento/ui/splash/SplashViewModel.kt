package cs.colman.talento.ui.splash

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
import cs.colman.talento.utils.UserManager
import kotlinx.coroutines.launch

class SplashViewModel(application: Application) : AndroidViewModel(application) {
    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val userRepository = UserRepository(userDao, context = getApplication())

    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn

    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> = _isLoading

    fun checkUserLoginStatus() {
        viewModelScope.launch {
            _isLoading.value = true

            val userId = UserManager.getUserId(getApplication())
            if (userId != null) {
                try {
                    val result = userRepository.loadUser(userId)
                    _isUserLoggedIn.value = result is NetworkResult.Success
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user data")
                    _isUserLoggedIn.value = false
                }
            } else {
                _isUserLoggedIn.value = false
            }

            _isLoading.value = false
        }
    }
}
