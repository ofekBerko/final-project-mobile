package cs.colman.talento.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import cs.colman.talento.R
import cs.colman.talento.data.model.Review
import cs.colman.talento.data.model.ReviewWithUser
import cs.colman.talento.utils.UserManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import java.text.SimpleDateFormat
import java.util.Locale

class ReviewsAdapter(
    private val profileUserId: String,
    private val onReviewClick: (String) -> Unit,
    private val onEditReviewClick: (String, String) -> Unit
) : PagingDataAdapter<ReviewWithUser, ReviewsAdapter.ReviewViewHolder>(REVIEW_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val reviewWithUser = getItem(position)
        if (reviewWithUser != null) {
            holder.bind(reviewWithUser)
        }
    }

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvReviewerId: MaterialTextView = itemView.findViewById(R.id.tvReviewerId)
        private val tvReviewDate: MaterialTextView = itemView.findViewById(R.id.tvReviewDate)
        private val tvReviewContent: MaterialTextView = itemView.findViewById(R.id.tvReviewContent)
        private val ivReviewImage: ShapeableImageView = itemView.findViewById(R.id.ivReviewImage)
        private val btnEditReview: MaterialButton = itemView.findViewById(R.id.btnEditReview)

        fun bind(reviewWithUser: ReviewWithUser) {
            val review = reviewWithUser.review
            val context = itemView.context
            val currentUserId = UserManager.getUserId(context)

            val isReviewByMe = review.reviewerId == profileUserId
            
            val displayText = if (isReviewByMe) {
                context.getString(R.string.my_review_to, reviewWithUser.reviewed.fullName)
            } else {
                context.getString(R.string.review_from, reviewWithUser.reviewer.fullName)
            }

            tvReviewerId.text = displayText
            tvReviewDate.text = formatDate(review.date)
            tvReviewContent.text = review.content

            if (review.imageUrl.isNullOrEmpty()) {
                ivReviewImage.visibility = View.GONE
            } else {
                ivReviewImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(review.imageUrl)
                    .placeholder(R.drawable.loading_icon)
                    .error(R.drawable.error_icon)
                    .into(ivReviewImage)
            }

            btnEditReview.visibility = if (review.reviewerId == currentUserId) View.VISIBLE else View.GONE

            btnEditReview.setOnClickListener {
                onEditReviewClick(review.reviewedId, review.reviewId)
            }

            itemView.setOnClickListener {
                val otherUserId = if (isReviewByMe) {
                    review.reviewedId
                } else {
                    review.reviewerId
                }
                onReviewClick(otherUserId)
            }
        }

        private fun formatDate(dateString: String): String {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                return if (date != null) outputFormat.format(date) else dateString
            } catch (e: Exception) {
                return dateString
            }
        }
    }

    companion object {
        private val REVIEW_COMPARATOR = object : DiffUtil.ItemCallback<ReviewWithUser>() {
            override fun areItemsTheSame(oldItem: ReviewWithUser, newItem: ReviewWithUser): Boolean {
                return oldItem.review.reviewId == newItem.review.reviewId
            }

            override fun areContentsTheSame(oldItem: ReviewWithUser, newItem: ReviewWithUser): Boolean {
                return oldItem.review.content == newItem.review.content &&
                        oldItem.review.date == newItem.review.date &&
                        oldItem.review.imageUrl == newItem.review.imageUrl &&
                        oldItem.reviewer.fullName == newItem.reviewer.fullName &&
                        oldItem.reviewed.fullName == newItem.reviewed.fullName
            }
        }
    }
}
