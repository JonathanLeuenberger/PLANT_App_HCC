package com.example.plant_app_hcc

import com.example.plant_app_hcc.FlowerColor.*

enum class FlowerColor(val fileName: String, val label: Int) {
    BLUE("blue", R.string.flower_blue),
    COLORFUL("colorful", R.string.flower_colorful),
    MAGENTA("magenta", R.string.flower_magenta),
    ORANGE("orange", R.string.flower_orange),
    PINK("pink", R.string.flower_pink),
    PINK_2("pink_2", R.string.flower_rose),
    PURPLE("purple", R.string.flower_purple),
    RED("red", R.string.flower_red),
    TEAL("teal", R.string.flower_teal),
    YELLOW("yellow", R.string.flower_yellow)
}

data class GardenFlower(val shape: String, val color: FlowerColor) {
    val id: String get() = "$shape/${color.fileName}"
}

object GardenFlowers {
    private val flowers = mapOf(
            "01" to listOf(BLUE, COLORFUL, RED, TEAL, YELLOW),
            "02" to listOf(MAGENTA, PINK, PURPLE, TEAL),
            "05" to listOf(BLUE, ORANGE, PINK, PURPLE),
            "06" to listOf(BLUE, ORANGE, PINK, PINK_2, PURPLE),
            "07" to listOf(BLUE, ORANGE, PINK, PINK_2, PURPLE),
            "08" to listOf(ORANGE, PINK, RED, YELLOW),
            "09" to listOf(ORANGE, PURPLE, RED, YELLOW),
            "13" to listOf(COLORFUL, PINK, PURPLE, YELLOW)
    ).flatMap { (shape, colors) -> colors.map { GardenFlower(shape, it) } }

    private val byId = flowers.associateBy { it.id }
    private val byShape = flowers.groupBy { it.shape }

    fun choose(used: Set<String>): String =
        flowers.filterNot { it.id in used }.ifEmpty { flowers }.random().id

    fun contains(id: String): Boolean = id in byId

    @JvmStatic fun colorsFor(id: String): List<GardenFlower> =
        byShape.getValue((byId[id] ?: flowers.first()).shape)

    internal fun assetPath(id: String): String =
        "garden/flowers/${(byId[id] ?: flowers.first()).id}.png"
}
