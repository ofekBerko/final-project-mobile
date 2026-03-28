package cs.colman.talento.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cs.colman.talento.BuildConfig
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.model.BusinessWithOwner
import cs.colman.talento.databinding.FragmentHomeBinding
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.MapUtils
import cs.colman.talento.utils.MapUtils.bindMapLifecycle
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.UserManager
import cs.colman.talento.utils.showSnackbar
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.annotations.Marker
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment(), OnMapReadyCallback, MapLibreMap.OnCameraMoveListener,
    MapLibreMap.OnCameraIdleListener, MapLibreMap.OnInfoWindowClickListener {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private var maplibreMap: MapLibreMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isMapReady = false
    private var initialLocationSet = false

    private var pendingBusinesses: List<BusinessWithOwner>? = null

    private val baseRadius = 10.0
    private val maxRadius = 50.0
    private val minZoomLevel = 5.0
    private val initialZoomLevel = 15.0

    private val handler = Handler(Looper.getMainLooper())
    private var fetchBusinessesRunnable: Runnable? = null
    private val fetchDelay = 300L

    private val markerBusinessMap = mutableMapOf<Marker, BusinessWithOwner>()

    private var isFetchingBusinesses = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(requireContext(), BuildConfig.MAPLIBRE_API_KEY, WellKnownTileServer.MapLibre)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        bindMapLifecycle(binding.mapView)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()

        LoadingUtil.showLoading(requireContext(), true)
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_search)
        }

        binding.btnMenu.setOnClickListener { button ->
            showPopupMenu(button)
        }
    }

    private fun observeViewModel() {
        viewModel.nearbyBusinesses.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    if (!isFetchingBusinesses) {
                        LoadingUtil.showLoading(requireContext(), true)
                        isFetchingBusinesses = true
                    }
                }
                is NetworkResult.Success -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    isFetchingBusinesses = false
                }
                is NetworkResult.Error -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    isFetchingBusinesses = false
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            if (!initialLocationSet) {
                updateMapLocation(location)
                initialLocationSet = true
            }
        }

        viewModel.localBusinessesWithOwners.observe(viewLifecycleOwner) { businessesWithOwners ->
            if (isMapReady && maplibreMap != null) {
                displayBusinessesOnMap(businessesWithOwners)
            } else {
                pendingBusinesses = businessesWithOwners
            }
        }
    }

    private fun displayBusinessesOnMap(businessesWithOwners: List<BusinessWithOwner>) {
        CoroutineScope(Dispatchers.Default).launch {
            val map = maplibreMap ?: return@launch

            val newMarkerData = mutableListOf<Pair<LatLng, BusinessWithOwner>>()
            val mapCenter = map.cameraPosition.target ?: return@launch
            val currentRadius = calculateRadiusFromZoom(map.cameraPosition.zoom)

            businessesWithOwners.forEach { businessWithOwner ->
                val business = businessWithOwner.business

                if (isBusinessInRadius(business, mapCenter, currentRadius)) {
                    newMarkerData.add(Pair(business.location, businessWithOwner))
                }
            }

            withContext(Dispatchers.Main) {
                MapUtils.clearMap(map)
                markerBusinessMap.clear()

                newMarkerData.forEach { (location, businessWithOwner) ->
                    val marker = MapUtils.addMarkerAndReturn(
                        map,
                        location,
                        businessWithOwner.business.businessName
                    )

                    markerBusinessMap[marker] = businessWithOwner
                }

                setupInfoWindows()

                LoadingUtil.showLoading(requireContext(), false)
            }
        }
    }

    private fun navigateToBusinessProfile(businessWithOwner: BusinessWithOwner) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeToProfile(businessWithOwner.owner.userId)
        )
    }

    private fun isBusinessInRadius(business: Business, center: LatLng, radiusKm: Double): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(
            center.latitude, center.longitude,
            business.location.latitude, business.location.longitude,
            results
        )

        return results[0] / 1000 <= radiusKm
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.menu_home, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_profile -> {
                    findNavController().navigate(HomeFragmentDirections.actionHomeToProfile(null))
                    true
                }
                R.id.action_appointments -> {
                    findNavController().navigate(R.id.action_home_to_appointments)
                    true
                }
                R.id.action_logout -> {
                    viewModel.logout()
                    UserManager.clearUserId(requireContext())
                    findNavController().navigate(R.id.action_home_to_login)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    override fun onMapReady(maplibreMap: MapLibreMap) {
        this.maplibreMap = maplibreMap
        isMapReady = true

        maplibreMap.uiSettings.isCompassEnabled = true

        maplibreMap.cameraPosition = CameraPosition.Builder()
            .zoom(initialZoomLevel)
            .build()

        MapUtils.setupMapStyle(maplibreMap) {
            setupMapListeners()

            pendingBusinesses?.let {
                displayBusinessesOnMap(it)
                pendingBusinesses = null
            }

            requestLocationPermission()
        }
    }

    private fun setupMapListeners() {
        val map = maplibreMap ?: return

        map.addOnCameraMoveListener(this)
        map.addOnCameraIdleListener(this)
        map.onInfoWindowClickListener = this
    }

    private fun setupInfoWindows() {
        val map = maplibreMap ?: return

        MapUtils.setupInfoWindowAdapter(map, layoutInflater, R.layout.map_info_window) {
                marker, view ->
            val businessWithOwner = markerBusinessMap[marker] ?: return@setupInfoWindowAdapter

            val tvBusinessName = view.findViewById<android.widget.TextView>(R.id.tv_business_name)
            val tvProfession = view.findViewById<android.widget.TextView>(R.id.tv_profession)
            val tvOwnerName = view.findViewById<android.widget.TextView>(R.id.tv_owner_name)

            tvBusinessName.text = businessWithOwner.business.businessName
            tvProfession.text = businessWithOwner.business.profession
            tvOwnerName.text = businessWithOwner.owner.fullName
        }
    }

    override fun onInfoWindowClick(marker: Marker): Boolean {
        if (isFetchingBusinesses) return true

        val businessWithOwner = markerBusinessMap[marker] ?: return false

        navigateToBusinessProfile(businessWithOwner)
        return true
    }

    override fun onCameraMove() {
        fetchBusinessesRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onCameraIdle() {
        fetchBusinessesRunnable?.let { handler.removeCallbacks(it) }

        val map = maplibreMap ?: return

        fetchBusinessesRunnable = Runnable {
            val center = map.cameraPosition.target
            val radiusKm = calculateRadiusFromZoom(map.cameraPosition.zoom)

            if (center != null) {
                viewModel.fetchNearbyBusinesses(center, radiusKm)
            }
        }

        handler.postDelayed(fetchBusinessesRunnable!!, fetchDelay)
    }

    private fun calculateRadiusFromZoom(zoomLevel: Double): Double {
        if (zoomLevel <= minZoomLevel) {
            return maxRadius
        }

        val zoomRange = 20.0 - minZoomLevel
        val zoomFactor = (20.0 - zoomLevel) / zoomRange

        return baseRadius + (maxRadius - baseRadius) * zoomFactor * zoomFactor
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableUserLocation()
        } else {
            Log.e(TAG,"Location permission denied")
            showSnackbar(binding.root, getString(R.string.location_permission_denied), SnackbarType.WARNING)

            LoadingUtil.showLoading(requireContext(), false)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            enableUserLocation()
        }
    }

    private fun enableUserLocation() {
        val map = maplibreMap ?: return

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val userLocation = MapUtils.enableUserLocation(map, requireContext())

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLatLng = LatLng(location.latitude, location.longitude)
                viewModel.setCurrentLocation(userLatLng)
            } ?: run {
                userLocation?.let {
                    viewModel.setCurrentLocation(it)
                } ?: run {
                    LoadingUtil.showLoading(requireContext(), false)
                }
            }
        }
    }

    private fun updateMapLocation(location: LatLng,) {
        val map = maplibreMap ?: return
        map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(location)
                        .zoom(initialZoomLevel)
                        .tilt(30.0)
                        .bearing(0.0)
                        .build()
                ), 1000, object : MapLibreMap.CancelableCallback {
                    override fun onCancel() {
                        LoadingUtil.showLoading(requireContext(), false)
                    }

                    override fun onFinish() {
                        LoadingUtil.showLoading(requireContext(), false)
                    }
                }
            )
        }


    override fun onDestroyView() {
        super.onDestroyView()
        fetchBusinessesRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}
