package cs.colman.talento.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import cs.colman.talento.data.model.Review
import cs.colman.talento.data.model.ReviewWithUser

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<Review>)

    @Transaction
    @Query("SELECT * FROM reviews WHERE reviewerId = :userId OR reviewedId = :userId ORDER BY date DESC")
    fun getCombinedUserReviewsPaged(userId: String): PagingSource<Int, ReviewWithUser>

    @Query("DELETE FROM reviews WHERE reviewerId = :userId OR reviewedId = :userId")
    suspend fun clearUserReviews(userId: String)

    @Query("SELECT * FROM reviews WHERE reviewId = :reviewId LIMIT 1")
    suspend fun getReviewById(reviewId: String): Review?
}
