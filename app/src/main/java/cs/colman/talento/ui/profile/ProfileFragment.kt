package cs.colman.talento.ui.profile

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.databinding.FragmentProfileBinding
import cs.colman.talento.ui.components.ProfessionAutocompleteView
import cs.colman.talento.utils.*
import cs.colman.talento.utils.MapUtils.bindMapLifecycle
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback

class ProfileFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val args: ProfileFragmentArgs by navArgs()
    private val viewModel: ProfileViewModel by viewModels()

    private var isEditing = false
    private var isCurrentUser = true
    private var userId: String? = null
    private var businessId: String? = null
    private var profileImageUri: Uri? = null
    private var businessLatLng: LatLng? = null
    private var maplibreMap: MapLibreMap? = null

    private var isSaving = false
    private lateinit var professionAutocomplete: ProfessionAutocompleteView
    private lateinit var reviewsAdapter: ReviewsAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        MapUtils.initializeMap(requireContext(), binding.mapViewBusinessLocation, savedInstanceState)
        binding.mapViewBusinessLocation.getMapAsync(this)

        bindMapLifecycle(binding.mapViewBusinessLocation)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = args.userId ?: UserManager.getUserId(requireContext())
        isCurrentUser = (userId == UserManager.getUserId(requireContext()))

        professionAutocomplete = binding.professionAutocomplete
        professionAutocomplete.setup(
            lifecycleOwner = viewLifecycleOwner,
            professions = viewModel.filteredProfessions,
            onSearch = { query, limit -> viewModel.searchProfessions(query, limit) }
        )

        setupListeners()
        setupReviewsSection()
        observeViewModel()

        userId?.let {
            viewModel.getUserProfile(it)

            if (businessId != null) {
                viewModel.refreshBusinessData(businessId)
            }

            viewModel.refreshProfessions()
            viewModel.refreshCombinedUserReviews(it)
        }

        MapUtils.setupMapTouchHandler(binding.mapViewBusinessLocation)
    }

    private fun setupReviewsSection() {
        binding.recyclerViewReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewReviews.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        userId?.let { uid ->
            reviewsAdapter = ReviewsAdapter(
                profileUserId = uid,
                onReviewClick = { reviewedUserId ->
                    findNavController().navigate(
                        ProfileFragmentDirections.actionProfileSelf(reviewedUserId)
                    )
                },
                onEditReviewClick = { reviewedId, reviewId ->
                    findNavController().navigate(
                        ProfileFragmentDirections.actionProfileToEditReview(reviewedId, reviewId)
                    )
                }
            )
            binding.recyclerViewReviews.adapter = reviewsAdapter

            reviewsAdapter.addLoadStateListener { loadState ->
                binding.progressReviews.visibility =
                    if (loadState.refresh is LoadState.Loading) View.VISIBLE else View.GONE

                val isListEmpty = loadState.refresh is LoadState.NotLoading && reviewsAdapter.itemCount == 0
                binding.textNoReviews.visibility = if (isListEmpty) View.VISIBLE else View.GONE

                val errorState = loadState.source.append as? LoadState.Error
                    ?: loadState.source.prepend as? LoadState.Error
                    ?: loadState.append as? LoadState.Error
                    ?: loadState.prepend as? LoadState.Error

                errorState?.let {
                    showSnackbar(binding.root, it.error.message ?: getString(R.string.error_loading_reviews),
                        SnackbarType.ERROR)
                }
            }
        }

        observeReviews()
    }

    private fun observeReviews() {
        userId?.let { uid ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.getCombinedUserReviewsPaged(uid).collect { pagingData ->
                        reviewsAdapter.submitData(pagingData)
                    }
                }
            }

            viewModel.reviewsState.observe(viewLifecycleOwner) { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressReviews.visibility = View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressReviews.visibility = View.GONE
                    }
                    is NetworkResult.Error -> {
                        binding.progressReviews.visibility = View.GONE
                        showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                    }
                }
            }
        }
    }

    override fun onMapReady(maplibreMap: MapLibreMap) {
        this.maplibreMap = maplibreMap

        MapUtils.setupMapStyle(maplibreMap) {
            MapUtils.enableUserLocation(maplibreMap, requireContext())

            businessLatLng?.let {
                updateMapWithBusinessLocation()
            }

            maplibreMap.uiSettings.setAllGesturesEnabled(true)
        }

        maplibreMap.addOnMapClickListener { point ->
            if (isEditing) {
                businessLatLng = point
                MapUtils.clearMap(maplibreMap)
                MapUtils.addMarker(maplibreMap, point)
                MapUtils.animateCamera(maplibreMap, point)
                true
            } else false
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            if (isEditing) {
                saveProfileChanges()
            } else {
                enableEditMode()
            }
        }

        binding.btnBookAppointment.setOnClickListener {
            val localBusinessId = businessId
            if (localBusinessId != null) {
                findNavController().navigate(
                    ProfileFragmentDirections.actionProfileToAppointmentBooking(localBusinessId)
                )
            } else {
                showSnackbar(binding.root, getString(R.string.error_unable_to_book), SnackbarType.ERROR)
            }
        }

        binding.radioGroupBusiness.setOnCheckedChangeListener { _, checkedId ->
            updateBusinessVisibility(checkedId == R.id.radioBusinessYes)

            if (checkedId == R.id.radioBusinessYes && businessLatLng == null && isEditing) {
                centerMapOnUserLocation()
            }
        }

        binding.ivProfilePic.setOnClickListener {
            if (isEditing) selectProfileImage()
        }

        binding.fabChangeProfilePic.setOnClickListener {
            if (isEditing) selectProfileImage()
        }
    }

    private fun updateBusinessVisibility(isVisible: Boolean) {
        binding.cardBusinessInfo.visibility = if (isVisible) View.VISIBLE else View.GONE

        if (isVisible && isEditing) {
            binding.tvBusinessName.visibility = View.GONE
            binding.layoutBusinessName.visibility = View.VISIBLE
            binding.tvBusinessDescription.visibility = View.GONE
            binding.layoutBusinessDescription.visibility = View.VISIBLE
            binding.tvBusinessAddress.visibility = View.GONE
            binding.layoutBusinessAddress.visibility = View.VISIBLE
            binding.tvBusinessProfession.visibility = View.GONE
            binding.professionContainer.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        userId?.let { uid ->
            LoadingUtil.showLoading(requireContext(), true)
            viewModel.getUserWithBusiness(uid).observe(viewLifecycleOwner) { userWithBusiness ->
                userWithBusiness?.let { uwb ->
                    if (!isSaving) {
                        val user = uwb.user

                        binding.tvUserName.text = user.fullName
                        binding.tvUserEmail.text = user.email
                        binding.tvUserPhone.text = user.phone

                        binding.etUserName.setText(user.fullName)
                        binding.etUserPhone.setText(user.phone)

                        if (profileImageUri == null) {
                            if (user.profilePicUrl.isNotEmpty()) {
                                Glide.with(this)
                                    .load(user.profilePicUrl)
                                    .placeholder(R.drawable.loading_icon)
                                    .into(binding.ivProfilePic)
                            } else {
                                binding.ivProfilePic.setImageResource(R.drawable.student_avatar)
                            }
                        }

                        businessId = user.businessId

                        val business = uwb.business
                        if (business != null) {
                            binding.tvBusinessName.text = business.businessName
                            binding.tvBusinessDescription.text = business.description
                            binding.tvBusinessAddress.text = business.address
                            binding.tvBusinessProfession.text = business.profession

                            binding.etBusinessName.setText(business.businessName)
                            binding.etBusinessDescription.setText(business.description)
                            binding.etBusinessAddress.setText(business.address)
                            professionAutocomplete.setText(business.profession)

                            businessLatLng = business.location
                            updateMapWithBusinessLocation()
                        }

                        updateUIState()
                    }

                    if (!isSaving) {
                        LoadingUtil.showLoading(requireContext(), false)
                    }
                }
            }
        }

        viewModel.userProfileState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    if (!isSaving) LoadingUtil.showLoading(requireContext(), true)
                }
                is NetworkResult.Success -> {
                    if (!isSaving) LoadingUtil.showLoading(requireContext(), false)
                }
                is NetworkResult.Error -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }

        viewModel.profileUpdateState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                }
                is NetworkResult.Success -> {
                    isEditing = false
                    isSaving = false
                    binding.btnEditProfile.text = getString(R.string.edit_profile)

                    updateUIState()

                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, getString(R.string.profile_update_success), SnackbarType.SUCCESS)
                }
                is NetworkResult.Error -> {
                    isSaving = false
                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }
    }

    private fun updateUIState() {
        binding.tvUserName.visibility = if (isEditing) View.GONE else View.VISIBLE
        binding.layoutUserName.visibility = if (isEditing) View.VISIBLE else View.GONE
        binding.tvUserPhone.visibility = if (isEditing) View.GONE else View.VISIBLE
        binding.layoutUserPhone.visibility = if (isEditing) View.VISIBLE else View.GONE

        binding.tvBusinessQuestion.visibility = if (isEditing) View.VISIBLE else View.GONE
        binding.radioGroupBusiness.visibility = if (isEditing) View.VISIBLE else View.GONE

        binding.fabChangeProfilePic.visibility = if (isEditing) View.VISIBLE else View.GONE

        if (isEditing) {
            binding.radioBusinessYes.isChecked = (businessId != null)
            binding.radioBusinessNo.isChecked = (businessId == null)
        }

        if (isEditing) {
            updateBusinessVisibility(binding.radioBusinessYes.isChecked)
        } else {
            binding.cardBusinessInfo.visibility = if (businessId != null) View.VISIBLE else View.GONE

            if (businessId != null) {
                binding.tvBusinessName.visibility = View.VISIBLE
                binding.layoutBusinessName.visibility = View.GONE
                binding.tvBusinessDescription.visibility = View.VISIBLE
                binding.layoutBusinessDescription.visibility = View.GONE
                binding.tvBusinessAddress.visibility = View.VISIBLE
                binding.layoutBusinessAddress.visibility = View.GONE
                binding.tvBusinessProfession.visibility = View.VISIBLE
                binding.professionContainer.visibility = View.GONE
            }
        }

        updateButtons()

        if (!isEditing && businessLatLng != null) {
            updateMapWithBusinessLocation()
        } else if (isEditing && businessLatLng == null && binding.radioBusinessYes.isChecked) {
            centerMapOnUserLocation()
        }
    }

    private fun updateMapWithBusinessLocation() {
        maplibreMap?.let { map ->
            businessLatLng?.let { location ->
                MapUtils.clearMap(map)
                MapUtils.addMarker(map, location)
                MapUtils.animateCamera(map, location, 15.0)
            }
        }
    }

    private fun centerMapOnUserLocation() {
        maplibreMap?.let { map ->
            val userLocation = MapUtils.getUserLocation(map)
            if (userLocation != null) {
                businessLatLng = userLocation
                MapUtils.clearMap(map)
                MapUtils.addMarker(map, userLocation)
                MapUtils.animateCamera(map, userLocation, 15.0)
            } else {
                centerMapOnDefaultLocation()
            }
        }
    }

    private fun updateButtons() {
        if (isCurrentUser) {
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnBookAppointment.visibility = View.GONE
        } else {
            binding.btnEditProfile.visibility = View.GONE
            binding.btnBookAppointment.visibility =
                if (businessId.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun enableEditMode() {
        isEditing = true
        binding.btnEditProfile.text = getString(R.string.save_profile)

        updateUIState()

        if (binding.radioBusinessYes.isChecked && businessLatLng == null) {
            centerMapOnUserLocation()
        }
    }

    private fun centerMapOnDefaultLocation() {
        val defaultLocation = LatLng(32.0853, 34.7818) // Example (Tel Aviv)
        maplibreMap?.let { map ->
            MapUtils.animateCamera(map, defaultLocation, 12.0)
        }
    }

    private fun isProfileValid(): Boolean {
        val fullName = binding.etUserName.text.toString().trim()
        val phone = binding.etUserPhone.text.toString().trim()

        if (!ValidationUtil.isValidName(fullName)) {
            binding.layoutUserName.error = getString(R.string.error_full_name_required)
            return false
        } else {
            binding.layoutUserName.error = null
        }

        if (!ValidationUtil.isValidPhoneNumber(phone)) {
            binding.layoutUserPhone.error = getString(R.string.error_invalid_phone)
            return false
        } else {
            binding.layoutUserPhone.error = null
        }

        if (binding.radioBusinessYes.isChecked) {
            val businessName = binding.etBusinessName.text.toString().trim()
            val address = binding.etBusinessAddress.text.toString().trim()
            val profession = professionAutocomplete.getText()

            if (!ValidationUtil.isValidBusinessName(businessName)) {
                binding.layoutBusinessName.error = getString(R.string.error_business_name_required)
                return false
            } else {
                binding.layoutBusinessName.error = null
            }

            if (!ValidationUtil.isValidAddress(address)) {
                binding.layoutBusinessAddress.error = getString(R.string.error_business_address_required)
                return false
            } else {
                binding.layoutBusinessAddress.error = null
            }

            if (isProfessionValid(profession).not()) {
                professionAutocomplete.setError(getString(R.string.error_invalid_profession))
                return false
            } else {
                professionAutocomplete.setError(null)
            }

            if (businessLatLng == null) {
                showSnackbar(binding.root, getString(R.string.error_select_location), SnackbarType.ERROR)
                return false
            }
        }

        return true
    }

    private fun isProfessionValid(professionName: String): Boolean {
        return professionAutocomplete.isProfessionValid(professionName, viewModel.filteredProfessions.value)
    }

    private fun saveProfileChanges() {
        if (!isProfileValid()) return

        isSaving = true
        LoadingUtil.showLoading(requireContext(), true)

        val fullName = binding.etUserName.text.toString().trim()
        val phone = binding.etUserPhone.text.toString().trim()

        val businessDetails = if (binding.radioBusinessYes.isChecked) {
            BusinessDetails(
                name = binding.etBusinessName.text.toString().trim(),
                description = binding.etBusinessDescription.text.toString().trim(),
                address = binding.etBusinessAddress.text.toString().trim(),
                profession = professionAutocomplete.getText(),
                location = businessLatLng ?: LatLng(0.0, 0.0)
            )
        } else null

        userId?.let { uid ->
            viewModel.saveProfileChanges(
                userId = uid,
                fullName = fullName,
                phone = phone,
                isBusiness = binding.radioBusinessYes.isChecked,
                businessId = businessId,
                businessDetails = businessDetails,
                profileImageUri = profileImageUri
            )

            profileImageUri = null
        }
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                profileImageUri = it
                Glide.with(requireContext())
                    .load(uri)
                    .placeholder(R.drawable.loading_icon)
                    .into(binding.ivProfilePic)
            }
        }

    private fun selectProfileImage() {
        imagePickerLauncher.launch("image/*")
    }

    override fun onResume() {
        super.onResume()

        if (!isEditing && !isSaving) {
             val localUserId = args.userId ?: UserManager.getUserId(requireContext())

            localUserId?.let { uid ->
                 viewModel.getUserProfile(uid)

                 viewLifecycleOwner.lifecycleScope.launch {
                      val userResult = viewModel.forceRefreshUserData(uid)

                      if (userResult is NetworkResult.Success && userResult.data.businessId != null) {
                         businessId = userResult.data.businessId
                         viewModel.refreshBusinessData(userResult.data.businessId)
                     }

                    viewModel.refreshCombinedUserReviews(uid)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
