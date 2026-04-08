/**
 * DelegateTypeTest.kt — Unit tests for [DelegateType] enum.
 *
 * Confirms that all expected variants are present and have stable ordinal
 * values that the priority system in [LiteRtDelegateProvider] depends on.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.delegate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateTypeTest {

    @Test
    fun `enum contains exactly three variants`() {
        assertEquals(3, DelegateType.entries.size)
    }

    @Test
    fun `QNN_NPU is a valid DelegateType value`() {
        assertEquals(DelegateType.QNN_NPU, DelegateType.valueOf("QNN_NPU"))
    }

    @Test
    fun `GPU is a valid DelegateType value`() {
        assertEquals(DelegateType.GPU, DelegateType.valueOf("GPU"))
    }

    @Test
    fun `CPU is a valid DelegateType value`() {
        assertEquals(DelegateType.CPU, DelegateType.valueOf("CPU"))
    }

    @Test
    fun `QNN_NPU ordinal is less than GPU ordinal`() {
        assertTrue(DelegateType.QNN_NPU.ordinal < DelegateType.GPU.ordinal)
    }

    @Test
    fun `GPU ordinal is less than CPU ordinal`() {
        assertTrue(DelegateType.GPU.ordinal < DelegateType.CPU.ordinal)
    }

    @Test
    fun `toString returns the enum name`() {
        assertEquals("QNN_NPU", DelegateType.QNN_NPU.toString())
        assertEquals("GPU", DelegateType.GPU.toString())
        assertEquals("CPU", DelegateType.CPU.toString())
    }
}
