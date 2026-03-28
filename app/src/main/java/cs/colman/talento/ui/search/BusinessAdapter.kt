package cs.colman.talento.ui.search

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cs.colman.talento.R
import cs.colman.talento.data.model.Business
import com.google.android.material.textview.MaterialTextView
import java.text.DecimalFormat

class BusinessAdapter(
    private var businesses: List<Business>,
    private val userLat: Double,
    private val userLon: Double,
    private val onBusinessClick: (String) -> Unit
) : RecyclerView.Adapter<BusinessAdapter.BusinessViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusinessViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_business, parent, false)
        return BusinessViewHolder(view)
    }

    override fun onBindViewHolder(holder: BusinessViewHolder, position: Int) {
        val business = businesses[position]
        holder.bind(business)
    }

    override fun getItemCount(): Int = businesses.size

    fun getBusinesses(): List<Business> = businesses

    fun updateList(newList: List<Business>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = businesses.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return businesses[oldItemPosition].businessId == newList[newItemPosition].businessId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return businesses[oldItemPosition] == newList[newItemPosition]
            }
        })

        businesses = newList
        diffResult.dispatchUpdatesTo(this)
    }

    inner class BusinessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: MaterialTextView = itemView.findViewById(R.id.tvBusinessName)
        private val tvOccupation: MaterialTextView = itemView.findViewById(R.id.tvBusinessOccupation)
        private val tvDistance: MaterialTextView = itemView.findViewById(R.id.tvBusinessDistance)

        fun bind(business: Business) {
            tvName.text = business.businessName
            tvOccupation.text = business.profession

            val distance = calculateDistance(business)
            val df = DecimalFormat("#.#")
            // Fix: Pass the formatted string directly to match the %s in strings.xml
            tvDistance.text = itemView.context.getString(R.string.distance_km_away, df.format(distance))

            itemView.setOnClickListener {
                onBusinessClick(business.userId)
            }
        }

        private fun calculateDistance(business: Business): Double {
            val userLocation = Location("").apply {
                latitude = userLat
                longitude = userLon
            }
            val businessLocation = Location("").apply {
                latitude = business.location.latitude
                longitude = business.location.longitude
            }
            return userLocation.distanceTo(businessLocation) / 1000.0
        }
    }
}
