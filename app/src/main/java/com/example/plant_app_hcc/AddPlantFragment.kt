package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.plant_app_hcc.databinding.FragmentAddPlantBinding
import com.example.plant_app_hcc.search.PlantCatalog
import com.example.plant_app_hcc.search.Species

object UserGarden {
    val addedPlants = mutableListOf<Species>()
}

class AddPlantFragment : Fragment() {
    private var _binding: FragmentAddPlantBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPlantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddToMyGarden.setOnClickListener {
            val plantName = binding.inputPlantName.text.toString().trim()
            val plantType = binding.inputPlantType.text.toString().trim()
            val location = binding.inputPlantLocation.text.toString().trim()

            val displayName = when {
                plantName.isNotEmpty() -> plantName
                plantType.isNotEmpty() -> plantType
                else -> "My Plant"
            }

            val catalog = PlantCatalog(requireContext())
            val matching = if (plantType.isNotEmpty()) {
                catalog.localSearch(plantType).firstOrNull() ?: catalog.all.firstOrNull()
            } else {
                catalog.all.firstOrNull()
            }

            val newSpecies = Species().apply {
                id = "user_${System.currentTimeMillis()}"
                name = displayName
                scientific = if (location.isNotEmpty()) location else (matching?.scientific ?: "Indoor Plant")
                description = matching?.description ?: "Added to My Garden"
                image = matching?.image ?: "asset:plantsearch/plants/monstera.jpg"
                water = matching?.water ?: "Water when soil is dry."
                light = matching?.light ?: "Bright, indirect light."
                curated = true
            }

            // Add new plant at index 0 (top-left of home screen)
            UserGarden.addedPlants.add(0, newSpecies)

            Toast.makeText(requireContext(), "Added $displayName to My Garden!", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
