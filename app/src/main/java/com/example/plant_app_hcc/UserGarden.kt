package com.example.plant_app_hcc

import android.content.Context
import com.example.plant_app_hcc.search.Species
import org.json.JSONArray
import java.util.UUID

/** Saved plant instances. Two plants of the same species still have separate IDs and tasks. */
object UserGarden {
    const val PREFERENCES = "plant_garden_v1"
    const val MAX_NAME_LENGTH = 32
    private const val GARDEN_NAME = "garden_name"
    private const val BACKGROUND = "garden_background"

    internal fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @JvmStatic fun plants(context: Context): List<Species> = synchronized(preferences(context)) {
        val rows = JSONArray(preferences(context).getString("plants", "[]") ?: "[]")
        val plants = (0 until rows.length()).map { Species.fromJson(rows.getJSONObject(it)) }
        val unassigned = plants.filterNot { GardenFlowers.contains(it.flowerId) }
        if (unassigned.isNotEmpty()) {
            val used = plants.map { it.flowerId }.toMutableSet()
            for (plant in unassigned) {
                plant.flowerId = GardenFlowers.choose(used)
                used.add(plant.flowerId)
            }
            save(context, plants)
        }
        plants
    }

    @JvmStatic fun find(context: Context, plantId: String): Species? =
        plants(context).find { it.id == plantId }

    @JvmStatic @JvmOverloads
    fun add(context: Context, source: Species, name: String? = null, location: String? = null): Species =
        synchronized(preferences(context)) {
            val plants = plants(context)
            // Copy every field, including attribution and care-source links.
            val plant = Species.fromJson(source.toJson()).apply {
                speciesId = source.speciesId.ifEmpty { source.id }
                id = "garden_${UUID.randomUUID()}"
                potStyleId = PotStyle.STARTER.id
                flowerId = GardenFlowers.choose(plants.map { it.flowerId }.toSet())
                this.name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name
                this.location = location?.trim() ?: source.location
            }
            save(context, listOf(plant) + plants)
            plant
        }

    @JvmStatic fun remove(context: Context, plantId: String): Boolean =
        synchronized(preferences(context)) {
            val old = plants(context)
            if (old.none { it.id == plantId }) return false
            CareTaskManager.removeForPlant(context, plantId)
            save(context, old.filterNot { it.id == plantId })
            true
        }

    @JvmStatic fun setPot(context: Context, plantId: String, pot: PotStyle): Boolean {
        if (pot !in ChallengeStore(context).ownedPots()) return false
        return synchronized(preferences(context)) {
            val plants = plants(context)
            val plant = plants.find { it.id == plantId } ?: return false
            plant.potStyleId = pot.id
            save(context, plants)
            true
        }
    }

    @JvmStatic fun setFlower(context: Context, plantId: String, flowerId: String): Boolean =
        synchronized(preferences(context)) {
            val plants = plants(context)
            val plant = plants.find { it.id == plantId } ?: return false
            if (GardenFlowers.colorsFor(plant.flowerId).none { it.id == flowerId }) return false
            plant.flowerId = flowerId
            save(context, plants)
            true
        }

    fun name(context: Context): String =
        preferences(context).getString(GARDEN_NAME, null) ?: context.getString(R.string.garden_default_name)

    fun background(context: Context): GardenBackground =
        GardenBackground.fromId(preferences(context).getString(BACKGROUND, null))

    fun customize(context: Context, name: String, background: GardenBackground): Boolean {
        val trimmed = name.trim()
        if (trimmed.length > MAX_NAME_LENGTH || background !in ChallengeStore(context).ownedBackgrounds()) {
            return false
        }
        val editor = preferences(context).edit().putString(BACKGROUND, background.id)
        if (trimmed.isEmpty()) editor.remove(GARDEN_NAME) else editor.putString(GARDEN_NAME, trimmed)
        editor.apply()
        return true
    }

    fun useBackground(context: Context, background: GardenBackground): Boolean {
        if (background !in ChallengeStore(context).ownedBackgrounds()) return false
        preferences(context).edit().putString(BACKGROUND, background.id).apply()
        return true
    }

    fun reorder(context: Context, orderedIds: List<String>) = synchronized(preferences(context)) {
        val old = plants(context)
        val byId = old.associateBy { it.id }
        val ordered = orderedIds.distinct().mapNotNull { byId[it] }
        save(context, ordered + old.filterNot { it.id in orderedIds })
    }

    private fun save(context: Context, plants: List<Species>) {
        val rows = JSONArray()
        plants.forEach { rows.put(it.toJson()) }
        val preferences = preferences(context)
        val editor = preferences.edit().putString("plants", rows.toString())
        ChallengeStore.recordPlantCount(preferences, editor, plants.size)
        editor.apply()
    }
}
