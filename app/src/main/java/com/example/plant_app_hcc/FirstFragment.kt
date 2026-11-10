package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.plant_app_hcc.databinding.FragmentFirstBinding
import com.example.plant_app_hcc.search.Images
import com.example.plant_app_hcc.search.Species
import java.util.Collections

/**
 * Home screen fragment styled as "My Garden".
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var images: Images
    private lateinit var adapter: PlantAdapter
    private val plantsList = mutableListOf<Species>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        images = Images(context)

        // Top Search button
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_PlantSearchFragment)
        }

        // Add Plants button
        binding.btnAddPlants.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_AddPlantFragment)
        }

        // Setup RecyclerView with 3-column GridLayout & Performance optimizations
        val recyclerView = binding.recyclerPlants
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)

        adapter = PlantAdapter(plantsList, images) { plant ->
            val args = Bundle().apply {
                putString("plant", plant.toJson().toString())
                putString("gardenPlantId", plant.id)
            }
            findNavController().navigate(R.id.PlantDetailsFragment, args)
        }
        recyclerView.adapter = adapter

        // Setup Drag and Drop ItemTouchHelper
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    Collections.swap(plantsList, fromPos, toPos)
                    adapter.notifyItemMoved(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                    viewHolder?.itemView?.scaleX = 1.05f
                    viewHolder?.itemView?.scaleY = 1.05f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
                UserGarden.reorder(context, plantsList.map { it.id })
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)

        refreshPlantsList()
    }

    override fun onResume() {
        super.onResume()
        refreshPlantsList()
    }

    private fun refreshPlantsList() {
        plantsList.clear()
        plantsList.addAll(UserGarden.plants(requireContext()))
        binding.textEmptyGarden.visibility = if (plantsList.isEmpty()) View.VISIBLE else View.GONE

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        images.close()
        binding.recyclerPlants.adapter = null
        super.onDestroyView()
        _binding = null
    }

    class PlantAdapter(
        private val items: List<Species>,
        private val images: Images,
        private val onItemClick: (Species) -> Unit
    ) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

        class PlantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.img_plant)
            val potBadge: ImageView = view.findViewById(R.id.img_plant_pot)
            val name: TextView = view.findViewById(R.id.text_plant_name)
            val card: View = view.findViewById(R.id.card_plant)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plant_grid, parent, false)
            return PlantViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
            val plant = items[position]
            holder.name.text = plant.name
            images.load(holder.img, plant.image)
            val pot = PotStyle.fromId(plant.potStyleId)
            holder.potBadge.visibility = if (pot == PotStyle.STARTER) View.GONE else View.VISIBLE
            holder.potBadge.setImageResource(pot.icon)
            holder.potBadge.contentDescription = holder.itemView.context.getString(pot.title)
            holder.card.setOnClickListener { onItemClick(plant) }
        }

        override fun getItemCount(): Int = items.size
    }
}
