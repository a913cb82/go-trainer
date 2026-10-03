package com.gotrainer.nine.game

/** Fixed bot strength vs automatch-to-target-winrate. */
enum class Difficulty(val id: String) {
    FIXED("fixed"),
    AUTOMATCH("automatch");

    fun label(): String = if (this == FIXED) "Bot Skill" else "Auto-Match"

    companion object {
        fun fromId(id: String): Difficulty =
            entries.firstOrNull { it.id == id } ?: FIXED
    }
}
