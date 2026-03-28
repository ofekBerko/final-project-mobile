package cs.colman.talento.ui.appointments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cs.colman.talento.R
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.AppointmentWithDetails
import com.google.android.material.button.MaterialButton

class AppointmentsAdapter(
    private val isPastAppointments: Boolean,
    private val currentUserId: String,
    private val onAppointmentClick: (AppointmentWithDetails) -> Unit,
    private val onCancelClick: (Appointment) -> Unit,
    private val onReviewClick: (AppointmentWithDetails) -> Unit
) : RecyclerView.Adapter<AppointmentsAdapter.AppointmentViewHolder>() {

    private var appointments: List<AppointmentWithDetails> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointmentWithDetails = appointments[position]
        holder.bind(appointmentWithDetails)
    }

    override fun getItemCount(): Int = appointments.size

    fun updateAppointments(newAppointments: List<AppointmentWithDetails>) {
        val diffCallback = AppointmentDiffCallback(appointments, newAppointments)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        appointments = newAppointments
        diffResult.dispatchUpdatesTo(this)
    }

    fun getPositionForAppointment(appointmentId: String): Int {
        return appointments.indexOfFirst { it.appointment.appointmentId == appointmentId }
    }

    inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvAppointmentDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvAppointmentTime)
        private val tvWith: TextView = itemView.findViewById(R.id.tvAppointmentWith)
        private val tvBusiness: TextView = itemView.findViewById(R.id.tvAppointmentBusiness)
        private val btnAction: MaterialButton = itemView.findViewById(R.id.btnAppointmentAction)

        fun bind(appointmentWithDetails: AppointmentWithDetails) {
            val appointment = appointmentWithDetails.appointment
            val user = appointmentWithDetails.user
            val business = appointmentWithDetails.business
            val context = itemView.context

            tvDate.text = formatDate(appointment.date)
            tvTime.text = appointment.time

            if (currentUserId == business.userId) {
                tvWith.text = context.getString(R.string.customer_with_name, user.fullName)
                tvBusiness.text = context.getString(R.string.phone_with_number, user.phone)
                tvBusiness.visibility = View.VISIBLE
            } else {
                tvWith.text = context.getString(R.string.business_with_name, business.businessName)
                tvBusiness.text = context.getString(R.string.service_with_profession, business.profession)
                tvBusiness.visibility = View.VISIBLE
            }

            if (isPastAppointments) {
                btnAction.text = context.getString(R.string.leave_a_review)
                btnAction.setBackgroundColor(context.getColor(R.color.purple_700))
                btnAction.setTextColor(context.getColor(R.color.white))
                btnAction.setOnClickListener {
                    onReviewClick(appointmentWithDetails)
                }
            } else {
                btnAction.text = context.getString(R.string.cancel_appointment)
                btnAction.setBackgroundColor(context.getColor(R.color.red))
                btnAction.setOnClickListener {
                    onCancelClick(appointment)
                }
            }

            itemView.setOnClickListener {
                onAppointmentClick(appointmentWithDetails)
            }
        }

        private fun formatDate(dateString: String): String {
            if (dateString.contains(",")) {
                return dateString
            }

            try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val outputFormat = java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.getDefault())
                val date = inputFormat.parse(dateString)
                return if (date != null) outputFormat.format(date) else dateString
            } catch (e: Exception) {
                return dateString
            }
        }
    }

    private class AppointmentDiffCallback(
        private val oldList: List<AppointmentWithDetails>,
        private val newList: List<AppointmentWithDetails>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].appointment.appointmentId ==
                    newList[newItemPosition].appointment.appointmentId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return oldItem.appointment.date == newItem.appointment.date &&
                    oldItem.appointment.time == newItem.appointment.time &&
                    oldItem.user.fullName == newItem.user.fullName &&
                    oldItem.business.businessName == newItem.business.businessName
        }
    }
}
