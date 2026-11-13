package com.example.plant_app_hcc

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.plant_app_hcc.databinding.FragmentFirstBinding
import com.example.plant_app_hcc.search.Species
import com.example.plant_app_hcc.search.Ui
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Collections

/**
 * Home screen fragment styled as "My Garden".
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PlantAdapter
    private val plantsList = mutableListOf<Species>()
    private var customizationDialog: AlertDialog? = null

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
        // Top Search button
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_PlantSearchFragment)
        }

        // Add Plants button
        binding.btnAddPlants.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_AddPlantFragment)
        }
        binding.btnCustomizeGarden.setOnClickListener { customizeGarden() }

        // Setup RecyclerView with 3-column GridLayout & Performance optimizations
        val recyclerView = binding.recyclerPlants
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)

        adapter = PlantAdapter(plantsList) { plant ->
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
        val context = requireContext()
        plantsList.clear()
        plantsList.addAll(UserGarden.plants(context))
        binding.textEmptyGarden.visibility = if (plantsList.isEmpty()) View.VISIBLE else View.GONE
        val background = UserGarden.background(context)
        val hasArtwork = background != GardenBackground.DEFAULT
        binding.root.setBackgroundColor(if (hasArtwork) Color.TRANSPARENT else Color.WHITE)
        binding.titleMyGarden.apply {
            text = UserGarden.name(context)
            this.background = if (hasArtwork) Ui.shape(0xD9FFFFFF.toInt(), Ui.dp(context, 18f)) else null
            val horizontalPadding = if (hasArtwork) Ui.dp(context, 12f) else 0
            val verticalPadding = if (hasArtwork) Ui.dp(context, 4f) else 0
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
        adapter.inSpace = background == GardenBackground.SPACE
        adapter.notifyDataSetChanged()
    }

    private fun customizeGarden() {
        val context = requireContext()
        val backgrounds = ChallengeStore(context).ownedBackgrounds()
        val current = UserGarden.background(context)
        val form = Ui.column(context).apply {
            setPadding(Ui.dp(context, 20f), Ui.dp(context, 12f), Ui.dp(context, 20f), Ui.dp(context, 12f))
        }
        val name = Ui.input(context, getString(R.string.garden_name), UserGarden.name(context), false)
        name.filters = arrayOf(InputFilter.LengthFilter(UserGarden.MAX_NAME_LENGTH))
        form.addView(name)
        Ui.gap(form, 12)
        val picker = Spinner(context).apply {
            id = View.generateViewId()
            minimumHeight = Ui.dp(context, 48f)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                backgrounds.map { getString(it.title) }).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(backgrounds.indexOf(current).coerceAtLeast(0))
        }
        form.addView(Ui.text(context, getString(R.string.garden_background_label), 15, Ui.INK, true).apply {
            labelFor = picker.id
        })
        form.addView(picker)
        if (backgrounds.size == 1) {
            form.addView(Ui.text(context, getString(R.string.garden_background_unlock_hint), 14, Ui.MUTED, false))
        }
        customizationDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.garden_customize_title)
            .setView(Ui.scroll(context, form))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.garden_customize_save) { _, _ ->
                val background = backgrounds[picker.selectedItemPosition]
                if (UserGarden.customize(context, name.text.toString(), background)) {
                    (requireActivity() as MainActivity).showGardenScene(background)
                    refreshPlantsList()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        customizationDialog?.dismiss()
        customizationDialog = null
        binding.recyclerPlants.adapter = null
        super.onDestroyView()
        _binding = null
    }

    class PlantAdapter(
        private val items: List<Species>,
        private val onItemClick: (Species) -> Unit
    ) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {
        var inSpace: Boolean = false

        class PlantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val art: PottedPlantView = view.findViewById(R.id.img_plant_pot)
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
            holder.art.show(plant.flowerId, PotStyle.fromId(plant.potStyleId), inSpace)
            holder.card.setOnClickListener { onItemClick(plant) }
        }

        override fun getItemCount(): Int = items.size
    }
}
