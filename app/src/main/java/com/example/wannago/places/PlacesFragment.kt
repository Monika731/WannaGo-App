package com.example.wannago.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wannago.PlacesViewModel
import com.example.wannago.R
import com.example.wannago.databinding.FragmentPlacesBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch


private const val TAG = "PlacesFragment"
private const val DEFAULT_ZOOM = 15f
private const val GOOGLE_SIGN_IN = 1903


class PlacesFragment: Fragment(), OnMapReadyCallback{
    private var map: GoogleMap? = null
    private val placesViewModel: PlacesViewModel by viewModels()

    private var _binding: FragmentPlacesBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "binding cannot be created. Is view created?"
        }
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentLocation: Location? = null
    private var locationPermissionGranted: Boolean = false

    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var latLng: LatLng? = null
    private var place: String? = null
    private lateinit var placesClient: PlacesClient

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient


    @SuppressLint("MissingPermission")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationPermissionGranted = permissions.entries.all {
            it.value
        }

        if (locationPermissionGranted) {
            // starts requesting for location updates
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPlacesBinding.inflate(inflater, container, false)

        if (!locationEnabled()) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                currentLocation = locationResult.lastLocation

                if (currentLocation != null && map != null) {
                    Log.d(TAG, "$currentLocation")
                    updateMapLocation(currentLocation)
                    updateMapUI()
                    // once we get a location, we can stop requesting for updates
                    // if we do not do this, the phone will continually check for updates
                    // which will use battery power
                    fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        }
        else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        auth = Firebase.auth
        googleSignInClient = GoogleSignIn.getClient(requireContext(), getGSO())

        val itemTouchHelperCallback =
            object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val placesAdapter = binding.recyclerView.adapter as PlacesAdapter

                    val location = placesAdapter.getLocationAtPosition(position)
                    auth.currentUser?.displayName?.let { userId ->
                        placesViewModel.removeUserLocation(userId, location.latitude, location.longitude)
                    }
                    placesAdapter.onItemSwiped(position)
                    auth.currentUser?.displayName?.let {
                        placesViewModel.deletePlaceUser(position,
                            it
                        )
                    }


                }
            }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        try {
            val appInfo = requireActivity().packageManager.getApplicationInfo(
                requireActivity().packageName, PackageManager.GET_META_DATA
            )

            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")

            if (apiKey != null) {
                com.google.android.libraries.places.api.Places.initializeWithNewPlacesApiEnabled(requireContext(), apiKey)
                placesClient = com.google.android.libraries.places.api.Places.createClient(requireContext())
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return binding.root
    }

    private fun getGSO(): GoogleSignInOptions {
        return  GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, GOOGLE_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                e.message?.let { Log.e(TAG, it) }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userName = user?.displayName
                    Toast.makeText(requireContext(), "$userName Signing successful", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG,"Exception")
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        binding.button.setOnClickListener { signIn() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                auth.currentUser?.displayName?.let {
                    placesViewModel.getPlaceNamesUser(
                        it
                    )
                }
                placesViewModel.places.collect{Places ->
                    binding.recyclerView.adapter = null
                    binding.recyclerView.adapter = PlacesAdapter(Places.toMutableList()) { latitude, longitude->
                        // Center the map on the selected location
                        map?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(latitude, longitude),
                                DEFAULT_ZOOM
                            )
                        )
                        findNavController().navigate(
                            PlacesFragmentDirections.showPlaceDetail(latitude, longitude)
                        )
                    }
                }
            }
        }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
    }


    private fun locationEnabled(): Boolean {
        val locationManager: LocationManager = this.requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onMapReady(p0: GoogleMap) {
        map = p0
        map!!.uiSettings.isZoomControlsEnabled = true
        updateMapUI()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                auth.currentUser?.displayName?.let { userId ->
                    placesViewModel.getUserLocations(userId).collect { locations ->
                        updateMapWithLocations(locations)
                    }
                }
            }
        }
        map!!.setOnPoiClickListener { pointOfInterest ->
            val placeId = pointOfInterest.placeId
            latLng = pointOfInterest.latLng

            addMarkerToMap(latLng!!, map!!)

            // Specify the fields to return.
            val placeFields = listOf(Place.Field.ID, Place.Field.NAME)
            // Construct a request object, passing the place ID and fields array.
            val request = FetchPlaceRequest.newInstance(placeId, placeFields)
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response: FetchPlaceResponse ->
                    place = response.place.name
                    // Retrieve the signed-in user's username
                    val signedInUsername = auth.currentUser?.displayName
                    // Check if the username is not null before calling the ViewModel
                    signedInUsername?.let {
                        // Call the ViewModel function to create user subcollections
                        placesViewModel.createUserSubcollections(
                            userId = it,
                            latitude = latLng!!.latitude,
                            longitude = latLng!!.longitude,
                            locationName = response.place.name
                        )
                    }
                }.addOnFailureListener { exception: Exception ->
                    if (exception is ApiException) {
                        Log.i(TAG, "Place not found: ${exception.message}")
                    }
                }
        }
        binding.mapView.onResume()
    }

    private fun addMarkerToMap(latLng: LatLng, map: GoogleMap) {
        map.addMarker(
            MarkerOptions()
                .position(latLng)
        )
    }

    private fun updateMapWithLocations(locations: List<LatLng>) {
        map?.clear()
        // Update the map with the new locations
        for (location in locations) {
            addMarkerToMap(location, map!!)
        }
        if (locations.isNotEmpty()) {
            // Center the map on the bounds of all stored locations
            val bounds = calculateBounds(locations)
            val padding = 100
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }

    private fun calculateBounds(locations: List<LatLng>): LatLngBounds {
        val builder = LatLngBounds.builder()
        for (location in locations) {
            builder.include(location)
        }
        return builder.build()
    }

    private fun updateMapUI() {
        try {
            if (map == null) return
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun updateMapLocation(location: Location?) {
        try {
            if (map == null) return
            if (!locationPermissionGranted || location == null) {
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            defaultLocation.latitude,
                            defaultLocation.longitude
                        ), DEFAULT_ZOOM
                    )
                )
                return
            }

            map?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        location.latitude,
                        location.longitude
                    ), DEFAULT_ZOOM
                )
            )
        }
        catch (e: Exception) {
            e.message?.let { Log.e(TAG, it) }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        _binding = null
    }
}
