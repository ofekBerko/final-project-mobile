package cs.colman.talento.ui.appointments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.showSnackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AppointmentsFragment : Fragment() {
    private val viewModel: AppointmentsViewModel by viewModels()
    private val args: AppointmentsFragmentArgs by navArgs()

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private var pendingHighlightId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appointments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)

        setupViewPager()
        observeViewModel()

        viewModel.loadAppointments(isUpcoming = true)

        args.appointmentId?.let { appointmentId ->
            pendingHighlightId = appointmentId

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    viewModel.setHighlightedAppointmentId(appointmentId)

                    tabLayout.getTabAt(0)?.select()
                } catch (e: Exception) {
                    Log.e(TAG, "AppointmentsFrag: Error setting highlight ID: ${e.message}")
                }
            }, 300)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = AppointmentsPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.upcoming) else getString(R.string.past)
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val isUpcoming = tab.position == 0
                viewModel.loadAppointments(loadMore = false, isUpcoming = isUpcoming)

                pendingHighlightId?.let { appointmentId ->
                    viewModel.setHighlightedAppointmentId(appointmentId)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun observeViewModel() {
        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                Log.e(TAG, "AppointmentsFrag: ERROR - $errorMessage")
                showSnackbar(requireView(), errorMessage, SnackbarType.ERROR)
            }
        }

        viewModel.cancelResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    showSnackbar(requireView(), getString(R.string.appointment_canceled_success), SnackbarType.SUCCESS)
                    viewModel.resetCancelResult()
                }
                is NetworkResult.Error -> {
                    showSnackbar(requireView(), result.message, SnackbarType.ERROR)
                    viewModel.resetCancelResult()
                }
                else -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()

        args.appointmentId?.let { appointmentId ->
            pendingHighlightId = appointmentId

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    viewModel.setHighlightedAppointmentId(appointmentId)

                    tabLayout.getTabAt(0)?.select()
                } catch (e: Exception) {
                    Log.e(TAG, "AppointmentsFrag: Error highlighting in onResume: ${e.message}")
                }
            }, 300)
        }
    }

    private inner class AppointmentsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return AppointmentListFragment.newInstance(isPast = position == 1)
        }
    }

    fun getCurrentTabPosition(): Int {
        return tabLayout.selectedTabPosition
    }
}
