package cs.colman.talento.ui.booking

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.databinding.FragmentAppointmentBookingBinding
import cs.colman.talento.utils.DateTimeUtils
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.showSnackbar
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppointmentBookingFragment : Fragment() {
    private var _binding: FragmentAppointmentBookingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppointmentBookingViewModel by viewModels()
    private val args: AppointmentBookingFragmentArgs by navArgs()

    private var selectedDate: String? = null
    private var selectedTime: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppointmentBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadBusiness(args.businessId)

        setupListeners()
        observeViewModel()

        updateConfirmButtonState()
    }

    private fun setupListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSelectTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnConfirmAppointment.setOnClickListener {
            confirmAppointment()
        }
    }

    private fun showDatePicker() {
        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .setFirstDayOfWeek(Calendar.SUNDAY)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_appointment_date))
            .setCalendarConstraints(constraintsBuilder.build())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = selectedDateFormat.format(selection)

            if (!DateTimeUtils.isDateAvailable(dateString)) {
                showSnackbar(binding.root, getString(R.string.error_saturdays_unavailable), SnackbarType.ERROR)
                return@addOnPositiveButtonClickListener
            }

            selectedDate = dateString

            val displayFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
            binding.btnSelectDate.text = displayFormat.format(Date(selection))

            viewModel.setSelectedDate(selectedDate!!)

            selectedTime = null
            binding.btnSelectTime.text = getString(R.string.pick_a_time)
            updateConfirmButtonState()
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker() {
        if (selectedDate == null) {
            showSnackbar(binding.root, getString(R.string.error_select_date_first), SnackbarType.WARNING)
            return
        }

        val timeSlots = viewModel.availableTimeSlots.value
        if (timeSlots.isNullOrEmpty()) {
            showSnackbar(binding.root, getString(R.string.error_no_available_slots), SnackbarType.ERROR)
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_time_title))
            .setItems(timeSlots.toTypedArray()) { _, which ->
                selectedTime = timeSlots[which]
                binding.btnSelectTime.text = selectedTime
                viewModel.setSelectedTime(selectedTime!!)
                updateConfirmButtonState()
            }
            .show()
    }

    private fun confirmAppointment() {
        if (selectedDate == null || selectedTime == null) {
            showSnackbar(binding.root, getString(R.string.error_select_date_time), SnackbarType.WARNING)
            return
        }

        val displayDate = DateTimeUtils.formatDateForDisplay(selectedDate!!)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_appointment_title))
            .setMessage(getString(R.string.confirm_appointment_message, displayDate, selectedTime))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                viewModel.bookAppointment()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateConfirmButtonState() {
        binding.btnConfirmAppointment.isEnabled = selectedDate != null && selectedTime != null
    }

    private fun observeViewModel() {
        viewModel.bookingState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    LoadingUtil.showLoading(requireContext(), true)
                }
                is NetworkResult.Success -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, getString(R.string.appointment_booked_success), SnackbarType.SUCCESS)
                    val appointmentId = result.data
                    findNavController().navigate(
                        AppointmentBookingFragmentDirections.actionBookingToAppointments(appointmentId))
                }
                is NetworkResult.Error -> {
                    LoadingUtil.showLoading(requireContext(), false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }

        viewModel.availableTimeSlots.observe(viewLifecycleOwner) { slots ->
            if (slots.isEmpty() && selectedDate != null) {
                showSnackbar(binding.root, getString(R.string.error_no_available_slots), SnackbarType.WARNING)
            }
        }

        viewModel.business.observe(viewLifecycleOwner) { business ->
            if (business === null)  {
                showSnackbar(binding.root, getString(R.string.error_business_info_missing), SnackbarType.ERROR)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
