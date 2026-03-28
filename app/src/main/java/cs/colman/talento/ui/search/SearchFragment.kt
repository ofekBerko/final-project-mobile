package cs.colman.talento.ui.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import cs.colman.talento.R
import cs.colman.talento.data.model.Business
import cs.colman.talento.databinding.FragmentSearchBinding
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.showSnackbar
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var businessAdapter: BusinessAdapter
    private val viewModel: SearchViewModel by viewModels()

    private var currentDistanceKm = 5.0
    private var searchDebounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkLocationPermission()
        setupProfessionAutocomplete()
        setupSearchInputs()
        setupDistanceSlider()
        setupBusinessList()
        observeViewModel()
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadUserLocationAsync()
        } else {
            showSnackbar(binding.root, getString(R.string.location_permission_denied), SnackbarType.WARNING)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.loadUserLocationAsync()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun setupProfessionAutocomplete() {
        binding.professionAutocomplete.setup(
            lifecycleOwner = viewLifecycleOwner,
            professions = viewModel.filteredProfessions,
            onSearch = { query: String, limit: Int -> viewModel.searchProfessions(query, limit) }
        )

        binding.professionAutocomplete.addTextChangeListener { _: String ->
            debounceSearch()
        }

        binding.professionAutocomplete.setOnItemClickListener { _: String ->
            debounceSearch()
        }

        viewModel.refreshProfessions()
    }

    private fun setupSearchInputs() {
        binding.etBusinessName.doAfterTextChanged { text ->
            debounceSearch()
        }
    }

    private fun setupDistanceSlider() {
        binding.sliderDistance.apply {
            addOnChangeListener { _, value, fromUser ->
                currentDistanceKm = value.toDouble()
                binding.tvDistanceValue.text = getString(R.string.distance_km, value.toInt())

                if (fromUser) {
                    debounceSearch()
                }
            }

            binding.tvDistanceValue.text = getString(R.string.distance_km, value.toInt())
        }
    }

    private fun debounceSearch() {
        searchDebounceJob?.cancel()
        searchDebounceJob = MainScope().launch {
            delay(500)
            updateSearch()
        }
    }

    private fun updateSearch() {
        val businessName = binding.etBusinessName.text.toString()
        val profession = binding.professionAutocomplete.getText()

        viewModel.updateSearchCriteria(
            name = businessName,
            profession = profession,
            distanceKm = currentDistanceKm
        )
    }

    private fun setupBusinessList() {
        businessAdapter = BusinessAdapter(
            businesses = emptyList(),
            userLat = 0.0,
            userLon = 0.0
        ) { userId ->
            navigateToProfile(userId)
        }

        binding.rvBusinesses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = businessAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    LoadingUtil.showLoading(requireContext(), true)
                }
                is NetworkResult.Success -> {
                    updateBusinessAdapter(result.data)
                    binding.tvResultsCount.text = getString(R.string.results_found, result.data.size)
                    LoadingUtil.showLoading(requireContext(), false)
                }
                is NetworkResult.Error -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }

        viewModel.userLocation.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                if (::businessAdapter.isInitialized) {
                    val currentBusinesses = businessAdapter.getBusinesses()
                    businessAdapter = BusinessAdapter(
                        businesses = currentBusinesses,
                        userLat = location.latitude,
                        userLon = location.longitude
                    ) { userId ->
                        navigateToProfile(userId)
                    }
                    binding.rvBusinesses.adapter = businessAdapter
                }

                updateSearch()
            }
        }
    }

    private fun updateBusinessAdapter(businesses: List<Business>) {
        val location = viewModel.userLocation.value
        if (location != null && ::businessAdapter.isInitialized) {
            businessAdapter.updateList(businesses)
        }
    }

    private fun navigateToProfile(userId: String) {
        val action = SearchFragmentDirections.actionSearchToProfile(userId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }
}
