package com.gotrainer.nine.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD for the on-phone "Stream closed" outage: only libkatago.so was staged,
 * but the linker also needs its DT_NEEDED companions. This test pins the full
 * closure — a new native dep must land here AND in jniLibs, or the phone dies
 * silently while every PC test stays green.
 */
class StagingTest {
    @Test fun `required libs include the linker companions, not just katago`() {
        assertEquals(
            setOf("libkatago.so", "libSNPE.so", "libtensorflowlite.so"),
            Staging.REQUIRED_LIBS.toSet(),
        )
    }

    @Test fun `in-sync layout plans nothing`() {
        val sizes = mapOf("libkatago.so" to 1L, "libSNPE.so" to 2L, "libtensorflowlite.so" to 3L)
        assertTrue(Staging.planCopies(bundledSizes = sizes, stagedSizes = sizes).isEmpty())
    }

    @Test fun `stale or missing staged libs are planned`() {
        val bundled = mapOf("libkatago.so" to 1L, "libSNPE.so" to 2L, "libtensorflowlite.so" to 3L)
        assertEquals(
            listOf("libSNPE.so"),
            Staging.planCopies(bundled, mapOf("libkatago.so" to 1L, "libSNPE.so" to 9L, "libtensorflowlite.so" to 3L)),
        )
        assertEquals(
            listOf("libkatago.so", "libSNPE.so", "libtensorflowlite.so"),
            Staging.planCopies(bundled, emptyMap()),
        )
    }

    @Test fun `ld path skips empties`() {
        assertEquals("a:/b", Staging.ldPath(listOf("a", "", "/b")))
    }
}
