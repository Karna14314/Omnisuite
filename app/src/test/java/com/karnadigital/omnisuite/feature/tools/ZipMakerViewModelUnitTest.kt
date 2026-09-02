package com.karnadigital.omnisuite.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ZipMakerState sealed class representations.
 */
class ZipMakerViewModelUnitTest {

    @Test
    fun testZipMakerStateRepresentations() {
        val idleState: ZipMakerState = ZipMakerState.Idle
        val compressingState: ZipMakerState = ZipMakerState.Compressing
        val errorState: ZipMakerState = ZipMakerState.Error("Compression failed")

        assertTrue(idleState is ZipMakerState.Idle)
        assertTrue(compressingState is ZipMakerState.Compressing)
        assertTrue(errorState is ZipMakerState.Error)
        assertEquals("Compression failed", (errorState as ZipMakerState.Error).message)
    }
}
