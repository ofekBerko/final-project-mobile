package cs.colman.talento.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.dao.ReviewDao
import cs.colman.talento.data.local.dao.UserDao
import cs.colman.talento.data.model.Review
import cs.colman.talento.data.model.ReviewWithUser
import cs.colman.talento.utils.ImageUploadService
import cs.colman.talento.utils.NetworkResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReviewRepository(
    private val reviewDao: ReviewDao,
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val context: Context
) {
    suspend fun createOrUpdateReview(
        reviewId: String? = null,
        reviewerId: String,
        reviewedId: String,
        content: String,
        imageUri: Uri? = null
    ): NetworkResult<Review> = withContext(Dispatchers.IO) {
        try {
            val reviewer = userDao.getUserByIdSync(reviewerId)
            val reviewedUser = userDao.getUserByIdSync(reviewedId)

            if (reviewer == null || reviewedUser == null) {
                Log.e(TAG, "createOrUpdateReview: Invalid users. Reviewer: ${reviewer != null}, Reviewed: ${reviewedUser != null}")
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_users))
            }

            var existingReview: Review? = null
            if (reviewId != null) {
                existingReview = reviewDao.getReviewById(reviewId)
            }

            var imageUrl: String? = null

            if (imageUri != null) {
                val uriString = imageUri.toString()

                if (existingReview != null && uriString == existingReview.imageUrl) {
                    imageUrl = existingReview.imageUrl
                } else if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                    imageUrl = ImageUploadService.uploadImage(imageUri, "review-image-${UUID.randomUUID()}")
                } else {
                    imageUrl = existingReview?.imageUrl
                }
            } else if (existingReview != null) {
                imageUrl = existingReview.imageUrl
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val reviewMap = mutableMapOf(
                "reviewerId" to reviewerId,
                "reviewedId" to reviewedId,
                "content" to content,
                "date" to currentDate,
                "imageUrl" to (imageUrl ?: "")
            )

            val finalReviewId = if (reviewId != null) {
                firestore.collection("reviews").document(reviewId)
                    .update(reviewMap as Map<String, Any>).await()
                reviewId
            } else {
                val docRef = firestore.collection("reviews").document()
                docRef.set(reviewMap).await()
                docRef.id
            }

            val review = Review(
                reviewId = finalReviewId,
                reviewerId = reviewerId,
                reviewedId = reviewedId,
                content = content,
                date = currentDate,
                imageUrl = imageUrl
            )

            try {
                reviewDao.insertReview(review)
            } catch (e: Exception) {
                Log.e(TAG, "createOrUpdateReview: Error saving review to local database", e)
            }

            NetworkResult.Success(review)
        } catch (e: Exception) {
            Log.e(TAG, "createOrUpdateReview: Error", e)
            NetworkResult.Error(context.getString(R.string.error_creating_review))
        }
    }

    suspend fun getReviewById(reviewId: String): NetworkResult<Review> = withContext(Dispatchers.IO) {
        try {
            val localReview = reviewDao.getReviewById(reviewId)
            if (localReview != null) {
                return@withContext NetworkResult.Success(localReview)
            }

            val docSnapshot = firestore.collection("reviews").document(reviewId).get().await()
            if (!docSnapshot.exists()) {
                return@withContext NetworkResult.Error(context.getString(R.string.error_review_not_found))
            }

            val review = processReviewsDocument(docSnapshot)
            if (review == null) {
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_review_data))
            }

            reviewDao.insertReview(review)
            return@withContext NetworkResult.Success(review)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching review: ${e.message}", e)
            return@withContext NetworkResult.Error(context.getString(R.string.error_fetching_review))
        }
    }

    fun getCombinedUserReviewsPaged(userId: String): Flow<PagingData<ReviewWithUser>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { reviewDao.getCombinedUserReviewsPaged(userId) }
        ).flow
    }

    suspend fun refreshCombinedUserReviews(userId: String): NetworkResult<List<Review>> {
        return withContext(Dispatchers.IO) {
            try {
                val combinedReviews = mutableListOf<Review>()

                val reviewerQuery = firestore.collection("reviews")
                    .whereEqualTo("reviewerId", userId)
                    .get()
                    .await()

                val reviewerReviews = reviewerQuery.documents.mapNotNull { processReviewsDocument(it) }
                combinedReviews.addAll(reviewerReviews)

                val reviewedQuery = firestore.collection("reviews")
                    .whereEqualTo("reviewedId", userId)
                    .get()
                    .await()

                val reviewedReviews = reviewedQuery.documents.mapNotNull { processReviewsDocument(it) }
                combinedReviews.addAll(reviewedReviews)

                val sortedReviews = combinedReviews.sortedByDescending { it.date }

                reviewDao.clearUserReviews(userId)
                if (sortedReviews.isNotEmpty()) {
                    reviewDao.insertReviews(sortedReviews)
                }

                return@withContext NetworkResult.Success(sortedReviews)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing user reviews: ${e.message}", e)
                return@withContext NetworkResult.Error(context.getString(R.string.error_fetching_review))
            }
        }
    }

    private fun processReviewsDocument(doc: DocumentSnapshot): Review? {
        return try {
            val reviewId = doc.id
            val reviewerId = doc.getString("reviewerId")
            val reviewedId = doc.getString("reviewedId")
            val date = doc.getString("date")
            val content = doc.getString("content")
            val imageUrl = doc.getString("imageUrl")

            if (reviewerId == null || reviewedId == null || date == null || content == null) {
                Log.e(TAG, "processReviewDocument: missing fields in doc ${doc.id}")
                return null
            }

            Review(
                reviewId = reviewId,
                reviewerId = reviewerId,
                reviewedId = reviewedId,
                date = date,
                content = content,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "processReviewDocument: error parsing doc ${doc.id}", e)
            null
        }
    } 
}
