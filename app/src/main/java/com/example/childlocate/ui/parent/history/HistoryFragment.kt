package com.example.childlocate.ui.parent.history


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.R
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.databinding.FragmentHistoryBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton


class HistoryFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentHistoryBinding
    private val viewModel: HistoryViewModel by lazy {
        ViewModelProvider(this)[HistoryViewModel::class.java]
    }
    private lateinit var locationAdapter: HistoryAdapter
    private lateinit var googleMap: GoogleMap
    private var isMapVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isInternetAvailable()) {
            binding.textError.visibility = View.VISIBLE
            binding.textError.text="Unable to get history location"

        } else {
            // Setup RecyclerView
            locationAdapter = HistoryAdapter()
            val layoutManager = LinearLayoutManager(context)
            layoutManager.reverseLayout = true
            layoutManager.stackFromEnd = true
            binding.historyRecyclerView.layoutManager = layoutManager
            binding.historyRecyclerView.adapter = locationAdapter

            binding.historyRecyclerView.clipToPadding = false
            binding.historyRecyclerView.setPadding(0, 0, 0, 150)

            val userId = "1"  // Replace with actual user ID
            viewModel.loadLocationHistory(userId)

            viewModel.locationHistory.observe(viewLifecycleOwner, Observer { locationHistory ->
                locationAdapter.submitList(locationHistory)
            })

            // Setup Google Map
            val mapFragment =
                childFragmentManager.findFragmentById(R.id.historyMapContainer) as SupportMapFragment?
                    ?: SupportMapFragment.newInstance().also {
                        childFragmentManager.beginTransaction().replace(R.id.historyMapContainer, it)
                            .commit()
                    }
            mapFragment.getMapAsync(this)

            // Setup FAB
            val fabShowHistory: FloatingActionButton = view.findViewById(R.id.fab_show_history)
            fabShowHistory.setOnClickListener {
                isMapVisible = !isMapVisible
                if (isMapVisible) {
                    binding.historyRecyclerView.visibility = View.GONE
                    binding.historyMapContainer.visibility = View.VISIBLE
                    viewModel.locationHistory.value?.let { locationHistory ->
                        updateMapMarkers(locationHistory.take(6))
                    }
                    fabShowHistory.setImageResource(R.drawable.baseline_list_24)
                } else {
                    binding.historyRecyclerView.visibility = View.VISIBLE
                    binding.historyMapContainer.visibility = View.GONE
                    fabShowHistory.setImageResource(R.drawable.baseline_map_24)
                }
            }
        }




    }
    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    override fun onMapReady(mMap: GoogleMap) {
        googleMap = mMap
        /*try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_2)
            )
            if (!success) {
                Log.e("MapFragment", "Failed to apply map style.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("MapFragment", "Map style resource not found.", e)
        }*/
    }

    private fun updateMapMarkers(locationHistory: List<LocationHistory>) {
        if (::googleMap.isInitialized) {
            googleMap.clear()
            for (location in locationHistory) {
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(location.latitude, location.longitude))
                        .title(location.timestamp)
                )
            }
            locationHistory.firstOrNull()?.let {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
            }
        }
    }


}



/*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var locationAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationAdapter = HistoryAdapter()
        val layoutManager = LinearLayoutManager(context)
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true
        binding.historyRecyclerView.layoutManager = layoutManager
        binding.historyRecyclerView.adapter = locationAdapter

        binding.historyRecyclerView.clipToPadding = false
        binding.historyRecyclerView.setPadding(0, 0, 0, 150)

        val userId = "1"  // Replace with actual user ID
        viewModel.loadLocationHistory(userId)

        viewModel.locationHistory.observe(viewLifecycleOwner, Observer { locationHistory ->
            locationAdapter.submitList(locationHistory)
        })
    }
}
*/