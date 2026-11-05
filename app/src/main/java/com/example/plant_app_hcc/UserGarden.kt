package com.example.plant_app_hcc

import android.content.Context
import com.example.plant_app_hcc.search.Species
import org.json.JSONArray
import java.util.UUID

/** Saved plant instances. Two plants of the same species still have separate IDs and tasks. */
object UserGarden {
    const val PREFERENCES = "plant_garden_v1"

    internal fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @JvmStatic fun plants(context: Context): List<Species> = synchronized(preferences(context)) {
        val rows = JSONArray(preferences(context).getString("plants", "[]") ?: "[]")
        (0 until rows.length()).map { Species.fromJson(rows.getJSONObject(it)) }
    }

    @JvmStatic fun find(context: Context, plantId: String): Species? =
        plants(context).find { it.id == plantId }

    @JvmStatic @JvmOverloads
    fun add(context: Context, source: Species, name: String? = null, location: String? = null): Species =
        synchronized(preferences(context)) {
            // Copy every field, including attribution and care-source links.
            val plant = Species.fromJson(source.toJson()).apply {
                speciesId = source.speciesId.ifEmpty { source.id }
                id = "garden_${UUID.randomUUID()}"
                this.name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name
                this.location = location?.trim() ?: source.location
            }
            save(context, listOf(plant) + plants(context))
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

    fun reorder(context: Context, orderedIds: List<String>) = synchronized(preferences(context)) {
        val old = plants(context)
        val byId = old.associateBy { it.id }
        val ordered = orderedIds.distinct().mapNotNull { byId[it] }
        save(context, ordered + old.filterNot { it.id in orderedIds })
    }

    private fun save(context: Context, plants: List<Species>) {
        val rows = JSONArray()
        plants.forEach { rows.put(it.toJson()) }
        preferences(context).edit().putString("plants", rows.toString()).apply()
    }
}
