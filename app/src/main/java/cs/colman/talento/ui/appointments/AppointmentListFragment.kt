package cs.colman.talento.ui.appointments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.AppointmentWithDetails
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.UserManager

class AppointmentListFragment : Fragment() {
    private lateinit var viewModel: AppointmentsViewModel
    private lateinit var rvAppointments: RecyclerView
    private lateinit var tvNoAppointments: TextView

    private var adapter: AppointmentsAdapter? = null
    private var isPastAppointments = false
    private var isFirstLoad = true

    companion object {
        private const val ARG_IS_PAST = "is_past"

        fun newInstance(isPast: Boolean): AppointmentListFragment {
            return AppointmentListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_PAST, isPast)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPastAppointments = arguments?.getBoolean(ARG_IS_PAST, false) ?: false

        viewModel = ViewModelProvider(requireParentFragment())[AppointmentsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appointment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvAppointments = view.findViewById(R.id.rvAppointments)
        tvNoAppointments = view.findViewById(R.id.tvNoAppointments)

        setupAdapter()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupAdapter() {
        val userId = UserManager.getUserId(requireContext())

        adapter = AppointmentsAdapter(
            isPastAppointments = isPastAppointments,
            currentUserId = userId ?: "",
            onAppointmentClick = { appointmentWithDetails ->
                val otherUserId = getOtherUserId(appointmentWithDetails, userId)
                navigateToProfile(otherUserId)
            },
            onCancelClick = { appointment ->
                showCancelConfirmation(appointment)
            },
            onReviewClick = { appointmentWithDetails ->
                val otherUserId = getOtherUserId(appointmentWithDetails, userId)
                navigateToReview(otherUserId)
            }
        )

        rvAppointments.adapter = adapter
    }

    private fun setupRecyclerView() {
        rvAppointments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3
                        && firstVisibleItemPosition >= 0
                        && viewModel.loading.value == false) {
                        viewModel.loadAppointments(
                            loadMore = true,
                            isUpcoming = !isPastAppointments
                        )
                    }
                }
            }
        })
    }

    private fun getOtherUserId(appointment: AppointmentWithDetails, currentUserId: String?): String {
        return if (appointment.appointment.userId == currentUserId) {
            appointment.business.userId
        } else {
            appointment.appointment.userId
        }
    }

    private fun navigateToProfile(userId: String) {
        val action = AppointmentsFragmentDirections.actionAppointmentsToProfile(userId)
        requireParentFragment().findNavController().navigate(action)
    }

    private fun navigateToReview(reviewedUserId: String) {
        val action = AppointmentsFragmentDirections.actionAppointmentsToReview(reviewedUserId)
        requireParentFragment().findNavController().navigate(action)
    }

    private fun showCancelConfirmation(appointment: Appointment) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_cancel_appointment_title))
            .setMessage(getString(R.string.confirm_cancel_appointment_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.cancelAppointment(appointment.appointmentId)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun observeViewModel() {
        if (isPastAppointments) {
            viewModel.pastAppointments.observe(viewLifecycleOwner) { appointments ->
                updateList(appointments)

                val pendingHighlightId = viewModel.pendingHighlightId.value
                if (pendingHighlightId != null) {
                    if (appointments.any { it.appointment.appointmentId == pendingHighlightId }) {
                        highlightAppointment(pendingHighlightId)
                        viewModel.pendingHighlightId.postValue(null)
                    }
                }
            }
        } else {
            viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
                updateList(appointments)

                val pendingHighlightId = viewModel.pendingHighlightId.value
                if (pendingHighlightId != null) {
                    if (appointments.any { it.appointment.appointmentId == pendingHighlightId }) {
                        highlightAppointment(pendingHighlightId)
                        viewModel.pendingHighlightId.postValue(null)
                    }
                }
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            LoadingUtil.showLoading(requireContext(), isLoading)

            if (!isLoading) {
                val appointments = if (isPastAppointments)
                    viewModel.pastAppointments.value else viewModel.upcomingAppointments.value

                tvNoAppointments.visibility = if (appointments.isNullOrEmpty()) View.VISIBLE else View.GONE
                if (appointments.isNullOrEmpty()) {
                    tvNoAppointments.text = if (isPastAppointments)
                        getString(R.string.no_past_appointments) else getString(R.string.no_upcoming_appointments)
                }
            } else {
                tvNoAppointments.visibility = View.GONE
            }
        }

        viewModel.highlightedAppointmentId.observe(viewLifecycleOwner) { appointmentId ->
            if (appointmentId != null) {
                highlightAppointment(appointmentId)
            }
        }
    }

    private fun updateList(appointments: List<AppointmentWithDetails>) {
        adapter?.updateAppointments(appointments)

        if (appointments.isEmpty() && viewModel.loading.value == false) {
            tvNoAppointments.visibility = View.VISIBLE
            tvNoAppointments.text = if (isPastAppointments)
                getString(R.string.no_past_appointments) else getString(R.string.no_upcoming_appointments)
        } else {
            tvNoAppointments.visibility = View.GONE
        }
    }

    private fun highlightAppointment(appointmentId: String) {
        val position = adapter?.getPositionForAppointment(appointmentId) ?: -1

        if (position >= 0) {
            try {
                val layoutManager = rvAppointments.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(position, 20)

                rvAppointments.post {
                    try {
                        val viewHolder = rvAppointments.findViewHolderForAdapterPosition(position)
                        if (viewHolder == null) {
                            return@post
                        }

                        val itemView = viewHolder.itemView
                        val card = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.appointmentCard)

                        if (card != null) {
                            val originalElevation = card.cardElevation
                            card.cardElevation = originalElevation + 24f

                            card.animate()
                                .scaleX(1.08f)
                                .scaleY(1.08f)
                                .setDuration(200)
                                .start()

                            Handler(Looper.getMainLooper()).postDelayed({
                                card.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(200)
                                    .start()

                                card.cardElevation = originalElevation
                            }, 400)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "ListFrag: Error highlighting: ${e.message}")
                    }
                }

                viewModel.setHighlightedAppointmentId(null)
            } catch (e: Exception) {
                Log.e(TAG, "ListFrag: Error highlighting appointment: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val parentFragment = parentFragment as? AppointmentsFragment
            if (parentFragment != null) {
                val currentTabPosition = parentFragment.getCurrentTabPosition()
                val isCurrentTabSelected = (currentTabPosition == 0 && !isPastAppointments) ||
                        (currentTabPosition == 1 && isPastAppointments)

                if (isCurrentTabSelected && !isFirstLoad) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        viewModel.loadAppointments(loadMore = false, isUpcoming = !isPastAppointments)
                    }, 100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ListFrag: Error in onResume: ${e.message}")
        }

        isFirstLoad = false
    }
}
