package cs.colman.talento.ui.review

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.AppDatabase
import cs.colman.talento.data.model.Review
import cs.colman.talento.data.model.User
import cs.colman.talento.data.repository.ReviewRepository
import cs.colman.talento.data.repository.UserRepository
import cs.colman.talento.utils.NetworkResult
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val reviewDao = AppDatabase.getDatabase(application).reviewDao()
    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val reviewRepository = ReviewRepository(
        reviewDao = reviewDao,
        userDao = userDao,
        context = getApplication()
    )

    private val userRepository = UserRepository(userDao, context = getApplication())

    private val _reviewSubmissionState = MutableLiveData<NetworkResult<Review>>()
    val reviewSubmissionState: LiveData<NetworkResult<Review>> = _reviewSubmissionState

    private val _reviewData = MutableLiveData<NetworkResult<Review>>()
    val reviewData: LiveData<NetworkResult<Review>> = _reviewData

    private val _reviewedUser = MutableLiveData<NetworkResult<User>>()
    val reviewedUser: LiveData<NetworkResult<User>> = _reviewedUser

    fun fetchReviewedUser(userId: String) {
        viewModelScope.launch {
            _reviewedUser.value = NetworkResult.Loading
            _reviewedUser.value = userRepository.loadUser(userId)
        }
    }

    fun fetchReview(reviewId: String) {
        viewModelScope.launch {
            _reviewData.value = NetworkResult.Loading
            try {
                val result = reviewRepository.getReviewById(reviewId)
                _reviewData.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching review", e)
                _reviewData.value = NetworkResult.Error(
                    getApplication<Application>().getString(R.string.error_fetching_review)
                )
            }
        }
    }

    fun submitReview(
        reviewId: String?,
        reviewerId: String,
        reviewedId: String,
        content: String,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            _reviewSubmissionState.value = NetworkResult.Loading

            try {
                val result = reviewRepository.createOrUpdateReview(
                    reviewId = reviewId,
                    reviewerId = reviewerId,
                    reviewedId = reviewedId,
                    content = content,
                    imageUri = imageUri
                )

                _reviewSubmissionState.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting review", e)
                _reviewSubmissionState.value = NetworkResult.Error(
                    getApplication<Application>().getString(R.string.error_creating_review)
                )
            }
        }
    }
}
