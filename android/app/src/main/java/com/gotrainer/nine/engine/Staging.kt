package com.gotrainer.nine.engine

/**
 * Pure staging plan for the KataGo native closure — zero Android imports, so the
 * PC test suite guards what the phone demands.
 *
 * libkatago.so is useless alone: the linker needs every DT_NEEDED non-system
 * lib beside it or the engine process dies instantly with no output (the
 * "Stream closed" outage). REQUIRED_LIBS is that closure; jniLibs, staging and
 * this test must agree on it.
 */
object Staging {
    val REQUIRED_LIBS = listOf("libkatago.so", "libSNPE.so", "libtensorflowlite.so")
    const val CONFIG_ASSET = "gtp.cfg"

    /** Names whose staged copy is missing or size-stale and must be (re)copied. */
    fun planCopies(bundledSizes: Map<String, Long>, stagedSizes: Map<String, Long>): List<String> =
        REQUIRED_LIBS.filter { name -> stagedSizes[name] != bundledSizes[name] }

    fun ldPath(dirs: List<String>): String = dirs.filter { it.isNotEmpty() }.joinToString(":")
}
