package com.example.plant_app_hcc

enum class PotStyle(val id: String, val title: Int, val icon: Int, val price: Int) {
    STARTER("starter", R.string.pot_starter, R.drawable.ic_potted_plant, 0),
    TERRACOTTA("terracotta", R.string.pot_terracotta, R.drawable.ic_pot_terracotta, 15),
    STRIPED("striped", R.string.pot_striped, R.drawable.ic_pot_striped, 25),
    GOLD("gold", R.string.pot_gold, R.drawable.ic_pot_gold, 40);

    companion object {
        @JvmStatic fun fromId(id: String): PotStyle = entries.find { it.id == id } ?: STARTER
    }
}
