package com.example.wannago.places

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wannago.Places
import com.example.wannago.databinding.PlacesListItemBinding

class PlacesAdapter(private val places: MutableList<Places>,
                    private val onPlaceClicked: (latitude: Double, longitude: Double) -> Unit
) : RecyclerView.Adapter<PlacesAdapter.PlacesViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlacesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = PlacesListItemBinding.inflate(inflater, parent, false)
        return PlacesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlacesViewHolder, position: Int) {
        val place = places[position]
        holder.bind(place, onPlaceClicked)
    }

    override fun getItemCount() = places.size

    fun onItemSwiped(position: Int) {
        // Remove the item from the data list
        places.removeAt(position)
        // Notify the adapter that an item has been removed
        notifyItemRemoved(position)
    }

    // Method to get the LatLng at a specific position
    fun getLocationAtPosition(position: Int): Places {
        return places[position]
    }

    inner class PlacesViewHolder(val binding: PlacesListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(places: Places, onPlaceClicked: (latitude: Double, longitude: Double) -> Unit) {
            binding.latitude.text = places.latitude.toString()
            binding.longitude.text = places.longitude.toString()
            binding.name.text = places.address
            binding.root.setOnClickListener{
                onPlaceClicked(places.latitude, places.longitude)
            }
        }
    }
}