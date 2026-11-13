package com.example.plant_app_hcc

enum class GardenBackground(val id: String, val title: Int, val image: Int, val price: Int) {
    DEFAULT("default", R.string.garden_background_default, 0, 0),
    FIELD("background_field", R.string.garden_background_field, R.drawable.garden_field, 15),
    FOREST("background_forest", R.string.garden_background_forest, R.drawable.garden_forest, 25),
    RIVER("background_river", R.string.garden_background_river, R.drawable.garden_river, 25),
    SPACE("background_space", R.string.garden_background_space, R.drawable.garden_space, 35);

    companion object {
        fun fromId(id: String?): GardenBackground = entries.find { it.id == id } ?: DEFAULT
    }
}
