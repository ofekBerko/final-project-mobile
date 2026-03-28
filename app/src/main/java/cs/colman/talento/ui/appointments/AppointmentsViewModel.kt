package cs.colman.talento.ui.appointments

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.AppDatabase
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.AppointmentWithDetails
import cs.colman.talento.data.repository.AppointmentRepository
import cs.colman.talento.data.repository.BusinessRepository
import cs.colman.talento.data.repository.UserRepository
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.UserManager
import kotlinx.coroutines.launch

class AppointmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val appointmentDao = AppDatabase.getDatabase(application).appointmentDao()
    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val businessDao = AppDatabase.getDatabase(application).businessDao()

    private val userRepository = UserRepository(userDao, context = getApplication())
    private val businessRepository = BusinessRepository(businessDao, userDao, context = getApplication())
    private val appointmentRepository = AppointmentRepository(appointmentDao, userRepository, businessRepository, context = getApplication())

    private val _upcomingAppointments = MutableLiveData<List<AppointmentWithDetails>>(emptyList())
    val upcomingAppointments: LiveData<List<AppointmentWithDetails>> = _upcomingAppointments

    private val _pastAppointments = MutableLiveData<List<AppointmentWithDetails>>(emptyList())
    val pastAppointments: LiveData<List<AppointmentWithDetails>> = _pastAppointments

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _cancelResult = MutableLiveData<NetworkResult<Boolean>?>(null)
    val cancelResult: LiveData<NetworkResult<Boolean>?> = _cancelResult

    private val _highlightedAppointmentId = MutableLiveData<String?>(null)
    val highlightedAppointmentId: LiveData<String?> = _highlightedAppointmentId

    val pendingHighlightId = MutableLiveData<String?>(null)

    private var currentUpcomingPage = 1
    private var currentPastPage = 1
    private var hasMoreUpcoming = true
    private var hasMorePast = true
    private var lastUpcomingAppointment: Appointment? = null
    private var lastPastAppointment: Appointment? = null

    private var isLoadingUpcoming = false
    private var isLoadingPast = false

    fun setHighlightedAppointmentId(appointmentId: String?) {
        if (appointmentId != null) {

            pendingHighlightId.postValue(appointmentId)

            val isInUpcoming = _upcomingAppointments.value?.any {
                it.appointment.appointmentId == appointmentId
            } ?: false

            val isInPast = _pastAppointments.value?.any {
                it.appointment.appointmentId == appointmentId
            } ?: false

            if (isInUpcoming || isInPast) {
                _highlightedAppointmentId.postValue(appointmentId)
            }
        } else {
            _highlightedAppointmentId.postValue(null)
            pendingHighlightId.postValue(null)
        }
    }

    fun loadAppointments(loadMore: Boolean = false, isUpcoming: Boolean = true) {
        if ((isUpcoming && isLoadingUpcoming) || (!isUpcoming && isLoadingPast)) {
            return
        }

        if (loadMore && isUpcoming && !hasMoreUpcoming) return
        if (loadMore && !isUpcoming && !hasMorePast) return

        if (!loadMore) {
            if (isUpcoming) {
                currentUpcomingPage = 1
                hasMoreUpcoming = true
                lastUpcomingAppointment = null
                _upcomingAppointments.value = emptyList()
            } else {
                currentPastPage = 1
                hasMorePast = true
                lastPastAppointment = null
                _pastAppointments.value = emptyList()
            }
        }

        if (isUpcoming) isLoadingUpcoming = true else isLoadingPast = true

        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = UserManager.getUserId(getApplication()) ?: run {
                    Log.e(TAG, "ViewModel: userId is null")
                    _loading.postValue(false)
                    _error.postValue(getApplication<Application>().getString(R.string.error_user_not_logged_in))
                    if (isUpcoming) isLoadingUpcoming = false else isLoadingPast = false
                    return@launch
                }

                val userResult = userRepository.loadUser(userId)
                if (userResult !is NetworkResult.Success) {
                    Log.e(TAG, "ViewModel: Error loading user: ${(userResult as? NetworkResult.Error)?.message}")
                    _error.postValue(getApplication<Application>().getString(R.string.error_loading_user_profile))
                    _loading.postValue(false)
                    if (isUpcoming) isLoadingUpcoming = false else isLoadingPast = false
                    return@launch
                }

                val user = userResult.data
                val businessId = user.businessId

                val excludeIds = if (loadMore) {
                    if (isUpcoming) {
                        _upcomingAppointments.value?.map { it.appointment.appointmentId }?.toSet() ?: emptySet()
                    } else {
                        _pastAppointments.value?.map { it.appointment.appointmentId }?.toSet() ?: emptySet()
                    }
                } else emptySet()

                val result = appointmentRepository.fetchPaginatedAppointments(
                    userId = userId,
                    businessId = businessId,
                    isUpcoming = isUpcoming,
                    limit = 15,
                    excludeIds = excludeIds
                )

                when (result) {
                    is NetworkResult.Success -> {
                        val appointments = result.data

                        if (appointments.isEmpty()) {
                            if (isUpcoming) hasMoreUpcoming = false else hasMorePast = false
                        } else {
                            val appointmentsWithDetails = appointmentRepository.getAppointmentsWithDetails(appointments)

                            if (isUpcoming) {
                                lastUpcomingAppointment = appointments.lastOrNull()
                                val currentList = _upcomingAppointments.value ?: emptyList()
                                _upcomingAppointments.postValue(if (loadMore) currentList + appointmentsWithDetails else appointmentsWithDetails)
                                currentUpcomingPage++
                            } else {
                                lastPastAppointment = appointments.lastOrNull()
                                val currentList = _pastAppointments.value ?: emptyList()
                                _pastAppointments.postValue(if (loadMore) currentList + appointmentsWithDetails else appointmentsWithDetails)
                                currentPastPage++
                            }

                            val pendingId = pendingHighlightId.value
                            if (pendingId != null && appointmentsWithDetails.any { it.appointment.appointmentId == pendingId }) {
                                _highlightedAppointmentId.postValue(pendingId)
                                pendingHighlightId.postValue(null)
                            }
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "ViewModel: Error fetching appointments: ${result.message}")
                        _error.postValue(getApplication<Application>().getString(R.string.error_loading_appointments))
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel: Error in loadAppointments: ${e.message}")
                _error.postValue(getApplication<Application>().getString(R.string.error_loading_appointments))
            } finally {
                _loading.postValue(false)
                if (isUpcoming) isLoadingUpcoming = false else isLoadingPast = false
            }
        }
    }

    fun cancelAppointment(appointmentId: String) {
        viewModelScope.launch {
            _cancelResult.value = NetworkResult.Loading
            try {
                val result = appointmentRepository.cancelAppointment(appointmentId)
                _cancelResult.postValue(result)

                if (result is NetworkResult.Success) {
                    loadAppointments(isUpcoming = true)
                    loadAppointments(isUpcoming = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel: Error canceling appointment: ${e.message}")
                _cancelResult.postValue(NetworkResult.Error(getApplication<Application>().getString(R.string.error_canceling_appointment)))
            }
        }
    }

    fun resetCancelResult() {
        _cancelResult.value = null
    }
}
