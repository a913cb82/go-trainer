package com.gotrainer.nine.game

/** Which KataGo runs the game. Default REMOTE until the on-device binary is proven. */
enum class EngineMode(val id: String) {
    DEVICE("device"),
    REMOTE("remote");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): EngineMode = entries.firstOrNull { it.id == id } ?: REMOTE
    }
}

fun EngineMode.label(): String = when (this) {
    EngineMode.DEVICE -> "On-device"
    EngineMode.REMOTE -> "Remote"
}
