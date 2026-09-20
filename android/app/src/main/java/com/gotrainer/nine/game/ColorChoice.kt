package com.gotrainer.nine.game

/** Which side the human plays. RANDOM is resolved to Black/White on every new game. */
enum class ColorChoice(val id: String) {
    BLACK("black"),
    WHITE("white"),
    RANDOM("random");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): ColorChoice = entries.firstOrNull { it.id == id } ?: BLACK
    }
}

fun ColorChoice.label(): String = when (this) {
    ColorChoice.BLACK -> "Black"
    ColorChoice.WHITE -> "White"
    ColorChoice.RANDOM -> "Random"
}
