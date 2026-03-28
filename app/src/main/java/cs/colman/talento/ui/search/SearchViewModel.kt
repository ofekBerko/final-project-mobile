package cs.colman.talento.ui.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cs.colman.talento.TAG
import cs.colman.talento.data.local.AppDatabase
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.repository.BusinessRepository
import cs.colman.talento.data.repository.ProfessionRepository
import cs.colman.talento.utils.MapUtils
import cs.colman.talento.utils.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val professionDao = AppDatabase.getDatabase(application).professionDao()
    private val businessDao = AppDatabase.getDatabase(application).businessDao()
    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val professionRepository = ProfessionRepository(professionDao)
    private val businessRepository = BusinessRepository(businessDao, userDao, context = getApplication())

    val filteredProfessions = professionRepository.filteredProfessions

    private val _searchResults = MutableLiveData<NetworkResult<List<Business>>>()
    val searchResults: LiveData<NetworkResult<List<Business>>> = _searchResults

    private val _userLocation = MutableLiveData<LatLng?>()
    val userLocation: MutableLiveData<LatLng?> = _userLocation

    private val defaultLocation = LatLng(32.0853, 34.7818)

    private var searchName: String = ""
    private var searchProfession: String = ""
    private var searchDistanceKm: Double = 5.0

    private var searchJob: Job? = null

    init {
        _userLocation.value = defaultLocation
    }

    fun loadUserLocationAsync() {
        viewModelScope.launch {
            try {
                val location = MapUtils.getUserLocationAsync(getApplication())
                if (location != null) {
                    _userLocation.value = location
                    performSearch()
                } else {
                    Log.d(TAG, "Using default location as user location is unavailable")
                    _userLocation.value = defaultLocation
                    performSearch()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user location: ${e.message}")
                _userLocation.value = defaultLocation
                performSearch()
            }
        }
    }

    fun searchProfessions(query: String, limit: Int = 15) {
        viewModelScope.launch {
            professionRepository.searchProfessions(query, limit)
        }
    }

    fun refreshProfessions() {
        viewModelScope.launch {
            try {
                professionRepository.refreshProfessions()
                professionRepository.searchProfessions("", 15)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing professions: ${e.message}")
            }
        }
    }

    fun updateSearchCriteria(name: String = searchName,
                              profession: String = searchProfession,
                              distanceKm: Double = searchDistanceKm) {
        searchName = name.trim()
        searchProfession = profession.trim()
        searchDistanceKm = distanceKm

        performSearch()
    }

    private fun performSearch() {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                _searchResults.value = NetworkResult.Loading

                delay(300)

                val location = _userLocation.value ?: defaultLocation
                val result = businessRepository.searchBusinesses(
                    center = location,
                    radiusInKm = searchDistanceKm,
                    name = searchName,
                    profession = searchProfession
                )

                if (isActive) {
                    _searchResults.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching businesses: ${e.message}")
            }
        }
    }
}
