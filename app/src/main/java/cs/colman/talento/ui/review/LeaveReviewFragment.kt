package cs.colman.talento.ui.review

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.databinding.FragmentLeaveReviewBinding
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.NetworkResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class LeaveReviewFragment : Fragment() {
    private var _binding: FragmentLeaveReviewBinding? = null
    private val binding get() = _binding!!

    private val args: LeaveReviewFragmentArgs by navArgs()
    private var reviewImageUri: Uri? = null

    private val viewModel: ReviewViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaveReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reviewedUserId = args.reviewedUserId
        binding.tvReviewTitle.text = getString(R.string.leave_review_for_user, reviewedUserId.take(5))

        setupImagePicker()
        setupSubmitButton(reviewedUserId)
        observeReviewSubmission()
        observeReviewedUser()

        viewModel.fetchReviewedUser(reviewedUserId)

        val reviewId = args.reviewId

        if (reviewId != null) {
            binding.btnSubmitReview.text = getString(R.string.update_review)
            loadExistingReview(reviewId)
        }
    }

    private fun observeReviewedUser() {
        viewModel.reviewedUser.observe(viewLifecycleOwner) { result ->
            if (result is NetworkResult.Success) {
                val user = result.data
                val isEdit = args.reviewId != null
                binding.tvReviewTitle.text = if (isEdit) {
                    getString(R.string.edit_review_for_user, user.fullName)
                } else {
                    getString(R.string.leave_review_for_user, user.fullName)
                }
            }
        }
    }

    private fun loadExistingReview(reviewId: String) {
        viewModel.fetchReview(reviewId)

        viewModel.reviewData.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val review = result.data
                    binding.etReviewContent.setText(review.content)

                    if (!review.imageUrl.isNullOrEmpty()) {
                        binding.ivReviewImage.visibility = View.VISIBLE
                        Glide.with(requireContext())
                            .load(review.imageUrl)
                            .placeholder(R.drawable.loading_icon)
                            .into(binding.ivReviewImage)

                        reviewImageUri = Uri.parse(review.imageUrl)
                    }
                }
                is NetworkResult.Error -> {
                    Snackbar.make(
                        binding.root,
                        result.message ?: getString(R.string.error_fetching_review),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun setupImagePicker() {
        val imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                reviewImageUri = it
                binding.ivReviewImage.visibility = View.VISIBLE
                Glide.with(requireContext())
                    .load(uri)
                    .placeholder(R.drawable.loading_icon)
                    .into(binding.ivReviewImage)
            }
        }

        binding.btnUploadImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        binding.cardReviewImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun setupSubmitButton(reviewedUserId: String) {
        binding.btnSubmitReview.setOnClickListener {
            val reviewContent = binding.etReviewContent.text.toString().trim()

            if (reviewContent.isEmpty()) {
                Snackbar.make(binding.root, R.string.error_empty_review, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId.isNullOrEmpty()) {
                Snackbar.make(binding.root, R.string.error_not_logged_in, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reviewId = args.reviewId;
            LoadingUtil.showLoading(requireContext(), true)

            viewModel.submitReview(
                reviewId = reviewId,
                reviewerId = currentUserId,
                reviewedId = reviewedUserId,
                content = reviewContent,
                imageUri = reviewImageUri
            )
        }
    }

    private fun observeReviewSubmission() {
        viewModel.reviewSubmissionState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    LoadingUtil.showLoading(requireContext(), false)

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.review_submitted_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToProfile(args.reviewedUserId)
                }
                is NetworkResult.Error -> {
                    LoadingUtil.showLoading(requireContext(), false)

                    Snackbar.make(
                        binding.root,
                        result.message ?: getString(R.string.error_creating_review),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun navigateToProfile(userId: String) {
        findNavController().navigate(LeaveReviewFragmentDirections.actionReviewToProfile(userId))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
