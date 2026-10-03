package com.gotrainer.nine.game


/**
 * Ladder weakest to strongest: 20k … 1k, 1d … 9d. KataGo HumanSL profiles
 * exist for every rung (rank_20k is its floor); the old 30k–21k ids never
 * had bots behind them and resolve to [R20K] in [fromId].
 */
enum class Rank(val id: String) {
    R20K("20k"), R19K("19k"), R18K("18k"), R17K("17k"), R16K("16k"),
    R15K("15k"), R14K("14k"), R13K("13k"), R12K("12k"), R11K("11k"),
    R10K("10k"), R9K("9k"), R8K("8k"), R7K("7k"), R6K("6k"),
    R5K("5k"), R4K("4k"), R3K("3k"), R2K("2k"), R1K("1k"),
    R1D("1d"), R2D("2d"), R3D("3d"), R4D("4d"), R5D("5d"),
    R6D("6d"), R7D("7d"), R8D("8d"), R9D("9d");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): Rank {
            entries.firstOrNull { it.id == id }?.let { return it }
            // Retired sub-20k ids: those games were played against the
            // rank_20k config (the only one below the HumanSL floor), so
            // old records resolve upward instead of falling back to 10k.
            val num = id.dropLast(1).toIntOrNull()
            if (id.endsWith("k") && num != null && num > 20) return R20K
            return R10K
        }
    }
}

