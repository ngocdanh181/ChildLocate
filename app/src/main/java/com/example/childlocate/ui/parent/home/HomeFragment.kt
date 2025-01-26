package com.example.childlocate.ui.parent.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

private object MapConstants {
    const val DEFAULT_ZOOM = 15f
    const val MARKER_SIZE = 100
    const val CORNER_RADIUS = 50f
    const val PROJECT_ID = "childlocatedemo-6c5da"
}

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var binding: FragmentHomeBinding
    private val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }
    private val audioRealtimeViewModel: AudioStreamViewModel by lazy {
        ViewModelProvider(this)[AudioStreamViewModel::class.java]
    }

    private lateinit var childSpinnerAdapter: ChildSpinnerAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>

    private var childShareId: String? = null
    private var userMarker: Marker? = null
    private var sharedMarker: Marker? = null
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheet()
        setupObservers()
        setupButtons()
        setupChildrenSpinner()
        setupMap()
        setupAudioStream()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding.bottomSheet.background = MaterialShapeDrawable().apply {
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, MapConstants.CORNER_RADIUS)
                .setTopRightCorner(CornerFamily.ROUNDED, MapConstants.CORNER_RADIUS)
                .build()
        }

        setupBottomSheetCallback()
    }

    private fun setupBottomSheetCallback() {
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                binding.fabDirection.visibility = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> View.GONE
                    BottomSheetBehavior.STATE_COLLAPSED -> View.VISIBLE
                    else -> binding.fabDirection.visibility
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: Handle slide animations
            }
        })
    }

    private fun setupObservers() {
        viewModel.familyId.observe(viewLifecycleOwner) { familyId ->
            familyId?.let {
                viewModel.fetchChildren(it)
                setupFamilyChat(it)
            }
        }

        viewModel.children.observe(viewLifecycleOwner) { children ->
            childSpinnerAdapter.apply {
                clear()
                addAll(children)
                notifyDataSetChanged()
            }
        }

        setupChildrenSpinner()
        restoreSelectedChild()

        viewModel.fetchAvatarUrl()
        viewModel.avatarUrl.observe(viewLifecycleOwner) { avatarUrl ->
            Log.d("Maps", if (avatarUrl != null) "Have getted Url" else "Can't get Url")
        }
        /*
        viewModel.locationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LocationState.Loading -> {
                    showSuccessMessage("Đang cập nhật vị trí của trẻ")
                }
                is LocationState.Success -> {
                    showSuccessMessage("Đã cập nhật vị trí của trẻ")
                }
                is LocationState.Error -> {
                    showErrorMessage(state.message)
                }
                else -> { /* Handle other states */ }
            }
        }*/
    }
    private fun restoreSelectedChild() {
        viewModel.selectedChild.value?.let { selectedChild ->
            val position = childSpinnerAdapter.getPosition(selectedChild)
            if (position != -1) {
                binding.childNameSpinner.setSelection(position)
            }
        }
    }

    private fun setupFamilyChat(familyId: String) {
        binding.fabChat.setOnClickListener {
            val parentId = viewModel.parentId.value.toString()
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToDetailChatFragment(parentId, familyId)
            )
        }
    }

    private fun setupButtons() {
        with(binding) {
            btnSetting.setOnClickListener { navigateToTaskFragment() }
            btnHistory.setOnClickListener { navigateToWebFilterFragment() }
            btnSeeDetail.setOnClickListener { navigateToUsageDetailFragment() }
            btnCallChild.setOnClickListener { handleLocationRequest() }
            btnFindDirection.setOnClickListener { handleStopLocationRequest() }
            btnListenSound.setOnClickListener { handleAudioToggle() }
        }
    }

    private fun navigateToTaskFragment() {
        val parentId = viewModel.parentId.value.toString()
        childShareId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToTaskFragment(parentId, childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToWebFilterFragment() {
        childShareId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToWebFilterFragment(childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToUsageDetailFragment() {
        childShareId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToUsageDetailFragment(childId)
            findNavController().navigate(action)
        }
    }

    private fun handleLocationRequest() {
        childShareId?.let { childId ->
            viewModel.sendLocationRequest(MapConstants.PROJECT_ID, childId)
            getLocation()
        }
    }

    private fun handleStopLocationRequest() {
        childShareId?.let { childId ->
            viewModel.sendStopLocationRequest(MapConstants.PROJECT_ID, childId)
        }
    }

    private fun handleAudioToggle() {
        when (audioRealtimeViewModel.streamingState.value) {
            is StreamingState.Idle -> {
                childShareId?.let { audioRealtimeViewModel.requestRecording(it) }
            }
            is StreamingState.Listening -> {
                childShareId?.let { audioRealtimeViewModel.stopRecording(it) }
            }
            else -> { /* Handle other states */ }
        }
    }

    private fun setupChildrenSpinner() {
        childSpinnerAdapter = ChildSpinnerAdapter(requireContext())
        binding.childNameSpinner.apply {
            adapter = childSpinnerAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    childSpinnerAdapter.getItem(position)?.let { child ->
                        viewModel.selectChild(child)
                        childShareId = child.childId
                        Log.d("HomeFragment", "$childShareId")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    }

    private fun setupMap() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.mapView, it).commit()
            }
        mapFragment.getMapAsync(this)
        getCurrentLocation()
    }

    private fun setupAudioStream() {
        lifecycleScope.launch {
            audioRealtimeViewModel.streamingState.collect { state ->
                updateStreamingUI(state)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStreamingUI(state: StreamingState) {
        binding.btnListenSound.apply {
            when (state) {
                is StreamingState.Idle -> {
                    text = "Ready to listen"
                    isEnabled = true
                }
                is StreamingState.Connecting -> {
                    text = "Connecting..."
                    isEnabled = false
                }
                is StreamingState.Listening -> {
                    text = "Stop Listening..."
                    isEnabled = true
                }
                is StreamingState.Error -> {
                    text = "Error: ${state.message}"
                    Log.d("AUDIO", state.message)
                    isEnabled = true
                }
            }
        }
    }

    private fun getLocation() {
        childShareId?.let { childId ->
            viewModel.getSharedLocation(childId)
            viewModel.locationInfo.observe(viewLifecycleOwner) { address ->
                address?.let {
                    Log.d("AddressUpdate", "New address: ${it.getAddressLine(0)}")
                    updateSharedLocation(LatLng(it.latitude, it.longitude))
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude
                Log.d("Maps", "User Location: $userLatitude, $userLongitude")
                updateUserLocation(LatLng(userLatitude!!, userLongitude!!))
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onMapReady(mMap: GoogleMap) {
        googleMap = mMap
        applyMapStyle()
        updateUserLocation(LatLng(userLatitude ?: 0.0, userLongitude ?: 0.0))
    }

    private fun applyMapStyle() {
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            )
            if (!success) {
                Log.e("MapFragment", "Failed to apply map style.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("MapFragment", "Map style resource not found.", e)
        }
    }

    private fun updateUserLocation(userLatLng: LatLng) {
        if (!::googleMap.isInitialized) return

        userMarker?.remove()
        userMarker = googleMap.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("Your Location")
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, MapConstants.DEFAULT_ZOOM))
    }

    private fun updateSharedLocation(sharedLatLng: LatLng) {
        if (!::googleMap.isInitialized) return

        viewModel.avatarUrl.observe(viewLifecycleOwner) { avatarUrl ->
            if (avatarUrl != null) {
                loadCustomMarker(avatarUrl, sharedLatLng)
            } else {
                addDefaultMarker(sharedLatLng)
            }
        }
    }

    private fun loadCustomMarker(avatarUrl: String, location: LatLng) {
        Log.d("Maps", "Have UrlAvatar")
        Glide.with(this)
            .asBitmap()
            .load(avatarUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val resizedBitmap = Bitmap.createScaledBitmap(
                        resource,
                        MapConstants.MARKER_SIZE,
                        MapConstants.MARKER_SIZE,
                        false
                    )
                    addCustomMarker(location, resizedBitmap)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Handle placeholder if needed
                }
            })
    }

    private fun addCustomMarker(location: LatLng, bitmap: Bitmap) {
        sharedMarker?.remove()
        sharedMarker = googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Shared Location")
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
        )
    }

    private fun addDefaultMarker(location: LatLng) {
        Log.d("Maps", "Can't get Url")
        sharedMarker?.remove()
        sharedMarker = googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Shared Location")
        )
    }

    private fun showErrorMessage(message: String) {
        // Hiển thị thông báo lỗi (có thể dùng Snackbar hoặc Toast)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccessMessage(message: String) {
        // Hiển thị thông báo thành công
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}