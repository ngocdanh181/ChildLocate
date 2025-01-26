package com.example.childlocate.ui.parent.history



import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.databinding.ItemHistoryLocationBinding


class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.LocationViewHolder>() {

    private var locationList = listOf<LocationHistory>()

    fun submitList(list: List<LocationHistory>) {
        locationList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemHistoryLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(locationList[position])
    }

    override fun getItemCount(): Int = locationList.size

    inner class LocationViewHolder(private val binding: ItemHistoryLocationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(locationData: LocationHistory) {
            binding.timestampTextView.text = locationData.timestamp
            binding.addressTextView.text = locationData.address
        }
    }
}
