package com.example.wannago

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlacesViewModel"
class PlacesViewModel: ViewModel() {
    private val db = Firebase.firestore

    private var locationsMap = mutableMapOf<String, MutableStateFlow<List<LatLng>>>()

    private var _places = MutableStateFlow<List<Places>>(emptyList())
    val places: StateFlow<List<Places>> get() = _places

    private var _locationsFlow = MutableStateFlow(emptyList<LatLng>())
    private val locationsFlow = _locationsFlow.asStateFlow()


    fun getLocations(): StateFlow<List<LatLng>> = locationsFlow

    fun removeLocation(latitude: Double, longitude: Double) {
        val currentLocations = _locationsFlow.value.toMutableList()
        val locationToRemove = LatLng(latitude, longitude)

        // Find the index of the location to remove
        val indexToRemove = currentLocations.indexOf(locationToRemove)

        if (indexToRemove != -1) {
            currentLocations.removeAt(indexToRemove)
            _locationsFlow.value = currentLocations.toList()
        }
    }

    fun getPlaceNames(collectionName: String) {
        db.collection(collectionName)
            .addSnapshotListener{ snapshot, e->
                if(e != null) {
                    Log.w("Error", "Listen Failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.documents != null) {
                    _places.value = snapshot.documents.map { doc ->
                        Places(doc["latitude"] as Double, doc["longitude"] as Double, doc["address"] as String)
                    }
                    val locations = snapshot.documents.mapNotNull {
                        it["latitude"] as Double to it["longitude"] as Double
                    }.map { LatLng(it.first, it.second) }

                    _locationsFlow.value = locations
                    Log.d(TAG, "Current data: ${snapshot.documents}")
                } else {
                    Log.d(TAG, "Current data: null")
                }
            }
    }


    fun addPlace(collectionName: String, latitude: Double, longitude: Double, address: String) {
        val user = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "address" to address
        )

        // Add a new document with a generated ID
        db.collection(collectionName)
            .add(user)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }

    fun deletePlace(collectionName: String, position: Int) {
        val locationToRemove = _places.value.getOrNull(position)

        if (locationToRemove != null) {
            // Remove location from Firestore
            db.collection(collectionName)
                .whereEqualTo("latitude", locationToRemove.latitude)
                .whereEqualTo("longitude", locationToRemove.longitude)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.delete()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error deleting document", e)
                }

            // Remove location from ViewModel
            val updatedList = _places.value.toMutableList()
            updatedList.removeAt(position)
            _places.value = updatedList

            _locationsFlow.value = updatedList.map { LatLng(it.latitude, it.longitude) }
        }
    }

    fun getUserLocations(userId: String): StateFlow<List<LatLng>> {
        val userLocations = locationsMap.getOrPut(userId) { MutableStateFlow(emptyList()) }
        return userLocations
    }

    private fun addUserLocation(userId: String, locations: List<LatLng>) {
        val userLocations = locationsMap.getOrPut(userId) { MutableStateFlow(emptyList()) }
        userLocations.value = locations
    }

    fun removeUserLocation(userId: String, latitude: Double, longitude: Double) {
        val userLocations = locationsMap[userId]
        userLocations?.let {
            val locationToRemove = LatLng(latitude, longitude)
            val updatedList = it.value.toMutableList()
            val indexToRemove = updatedList.indexOf(locationToRemove)
            if (indexToRemove != -1) {
                updatedList.removeAt(indexToRemove)
                it.value = updatedList.toList()
            }
        }
    }

    fun createUserSubcollections(userId: String, latitude: Double, longitude: Double, locationName: String) {
        // Create a new subcollection for user locations
        val locationsCollection = db.collection("users").document(userId).collection("locations")

        // Location data
        val locationData = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "address" to locationName,
        )

        // Add the location data to the "locations" subcollection
        locationsCollection.add(locationData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Subcollection DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding subcollection document", e)
            }
    }

    fun getPlaceNamesUser(userId: String) {
        db.collection("users").document(userId).collection("locations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen Failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.documents != null) {
                    _places.value = snapshot.documents.map { doc ->
                        Places(
                            doc["latitude"] as Double,
                            doc["longitude"] as Double,
                            doc["address"] as String
                        )
                    }
                    val locations = snapshot.documents.mapNotNull {
                        it["latitude"] as Double to it["longitude"] as Double

                    }.map { LatLng(it.first, it.second)}
                    addUserLocation(userId, locations)
                    Log.d(TAG, "Current data: ${snapshot.documents}")
                } else {
                    Log.d(TAG, "Current data: null")
                }
            }
    }

    fun deletePlaceUser(position: Int, userId: String) {
        val locationToRemove = _places.value.getOrNull(position)

        if (locationToRemove != null) {
            // Remove location from Firestore
            val locationsCollection = db.collection("users").document(userId).collection("locations")
            locationsCollection
                .whereEqualTo("latitude", locationToRemove.latitude)
                .whereEqualTo("longitude", locationToRemove.longitude)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.delete()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error deleting document", e)
                }

            // Remove location from ViewModel
            val updatedList = _places.value.toMutableList()
            updatedList.removeAt(position)
            _places.value = updatedList

            _locationsFlow.value = updatedList.map { LatLng(it.latitude, it.longitude) }
        }
    }
}