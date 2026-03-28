package cs.colman.talento.ui.booking

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.AppDatabase
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.repository.AppointmentRepository
import cs.colman.talento.data.repository.BusinessRepository
import cs.colman.talento.data.repository.UserRepository
import cs.colman.talento.utils.DateTimeUtils
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.UserManager
import kotlinx.coroutines.launch

class AppointmentBookingViewModel(application: Application) : AndroidViewModel(application) {
    private val appointmentDao = AppDatabase.getDatabase(application).appointmentDao()
    private val businessDao = AppDatabase.getDatabase(application).businessDao()
    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val userRepository = UserRepository(userDao, context = getApplication())
    private val businessRepository = BusinessRepository(businessDao, userDao, context = getApplication())
    private val appointmentRepository = AppointmentRepository(appointmentDao, userRepository, businessRepository, context = getApplication())

    private val _selectedDate = MutableLiveData<String>()

    private val _selectedTime = MutableLiveData<String>()

    private val _availableTimeSlots = MutableLiveData<List<String>>()
    val availableTimeSlots: LiveData<List<String>> = _availableTimeSlots

    private val _bookingState = MutableLiveData<NetworkResult<String>>()
    val bookingState: LiveData<NetworkResult<String>> = _bookingState

    private val _business = MutableLiveData<Business?>()
    val business: MutableLiveData<Business?> = _business


    fun loadBusiness(businessId: String) {
        viewModelScope.launch {
            try {
                when (val result = businessRepository.getBusinessById(businessId)) {
                    is NetworkResult.Success -> {
                        _business.value = result.data
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "loadBusiness: Failed to load business: ${result.message}")
                    }
                    is NetworkResult.Loading -> {
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadBusiness: Exception occurred", e)
            }
        }
    }
    fun setSelectedDate(date: String) {
        _selectedDate.value = date
        updateAvailableTimeSlots()
    }

    fun setSelectedTime(time: String) {
        _selectedTime.value = time
    }

    private fun updateAvailableTimeSlots() {
        viewModelScope.launch {
            val date = _selectedDate.value ?: return@launch

            val allPossibleSlots = DateTimeUtils.generateTimeSlotsForDate(date)

            val business = _business.value
            if (business == null) {
                Log.e(TAG, "Business is null, cannot get booked slots")
                _availableTimeSlots.value = allPossibleSlots
                return@launch
            }

            try {
                val result = appointmentRepository.fetchAppointmentsForBusinessOnDate(business.businessId, date)
                if (result is NetworkResult.Success) {
                    val bookedSlots = result.data
                        .map { it.time }

                    val availableSlots = allPossibleSlots.filter { time ->
                        !bookedSlots.contains(time)
                    }

                    _availableTimeSlots.value = availableSlots
                } else {
                    Log.e(TAG, "Error fetching appointments: ${(result as? NetworkResult.Error)?.message}")
                    _availableTimeSlots.value = allPossibleSlots
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching appointments", e)
                _availableTimeSlots.value = allPossibleSlots
            }
        }
    }

    fun bookAppointment() {
        val userId = UserManager.getUserId(getApplication())
        if (userId == null) {
            Log.e(TAG, "bookAppointment: User ID is null")
            _bookingState.value = NetworkResult.Error(getApplication<Application>().getString(R.string.error_user_not_logged_in))
            return
        }

        val business = _business.value
        if (business == null) {
            Log.e(TAG, "bookAppointment: Business is null")
            _bookingState.value = NetworkResult.Error(getApplication<Application>().getString(R.string.error_business_not_available))
            return
        }

        val businessId = business.businessId

        val date = _selectedDate.value
        if (date == null) {
            Log.e(TAG, "bookAppointment: Date is null")
            _bookingState.value = NetworkResult.Error(getApplication<Application>().getString(R.string.error_select_date))
            return
        }

        val time = _selectedTime.value
        if (time == null) {
            Log.e(TAG, "bookAppointment: Time is null")
            _bookingState.value = NetworkResult.Error(getApplication<Application>().getString(R.string.error_select_time))
            return
        }

        viewModelScope.launch {
            _bookingState.value = NetworkResult.Loading
            _bookingState.value = appointmentRepository.bookAppointment(userId, businessId, date, time)
        }
    }
}
