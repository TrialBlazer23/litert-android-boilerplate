/**
 * BenchmarkTrackerTest.kt — Unit tests for [BenchmarkTracker].
 *
 * Validates accumulation logic, min/max CAS correctness, fallback recording,
 * and the [toReport] / [toBenchmarkData] output contracts.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.benchmark

import com.necessitylabs.litert.inference.delegate.DelegateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BenchmarkTrackerTest {

    private lateinit var tracker: BenchmarkTracker

    @Before
    fun setUp() {
        tracker = BenchmarkTracker()
    }

    // ── reset() ───────────────────────────────────────────────────────────────

    @Test
    fun `reset initialises model name and delegate correctly`() {
        tracker.reset("MobileNetV3", DelegateType.QNN_NPU, loadMs = 200L)
        val data = tracker.toBenchmarkData()
        assertEquals("MobileNetV3", data.modelName)
        assertEquals(DelegateType.QNN_NPU, data.delegateType)
        assertEquals(200L, data.loadTimeMs)
    }

    @Test
    fun `reset clears all previous run data`() {
        tracker.reset("ModelA", DelegateType.GPU, loadMs = 100L)
        tracker.recordRun(50L)
        tracker.recordRun(70L)

        // Reset to a new session.
        tracker.reset("ModelB", DelegateType.CPU, loadMs = 10L)
        val data = tracker.toBenchmarkData()

        assertEquals("ModelB", data.modelName)
        assertEquals(0L, data.totalInferences)
        assertEquals(0.0, data.avgInferenceMs, 0.001)
        assertEquals(0L, data.minInferenceMs)
        assertEquals(0L, data.maxInferenceMs)
        assertFalse(data.fallbackOccurred)
    }

    // ── recordRun() ───────────────────────────────────────────────────────────

    @Test
    fun `recordRun increments total inference count`() {
        tracker.reset("M", DelegateType.CPU, 0L)
        tracker.recordRun(10L)
        tracker.recordRun(20L)
        tracker.recordRun(30L)
        assertEquals(3L, tracker.toBenchmarkData().totalInferences)
    }

    @Test
    fun `recordRun tracks first inference separately`() {
        tracker.reset("M", DelegateType.CPU, 0L)
        tracker.recordRun(42L)
        tracker.recordRun(10L)
        assertEquals(42L, tracker.toBenchmarkData().firstInferenceMs)
    }

    @Test
    fun `recordRun computes correct average`() {
        tracker.reset("M", DelegateType.CPU, 0L)
        tracker.recordRun(10L)
        tracker.recordRun(20L)
        tracker.recordRun(30L)
        // Average = (10+20+30)/3 = 20.0
        assertEquals(20.0, tracker.toBenchmarkData().avgInferenceMs, 0.001)
    }

    @Test
    fun `recordRun tracks min and max correctly`() {
        tracker.reset("M", DelegateType.CPU, 0L)
        tracker.recordRun(50L)
        tracker.recordRun(10L)
        tracker.recordRun(100L)
        tracker.recordRun(25L)
        val data = tracker.toBenchmarkData()
        assertEquals(10L, data.minInferenceMs)
        assertEquals(100L, data.maxInferenceMs)
    }

    @Test
    fun `single run has identical min and max`() {
        tracker.reset("M", DelegateType.GPU, 0L)
        tracker.recordRun(77L)
        val data = tracker.toBenchmarkData()
        assertEquals(77L, data.minInferenceMs)
        assertEquals(77L, data.maxInferenceMs)
    }

    // ── recordFallback() ─────────────────────────────────────────────────────

    @Test
    fun `recordFallback sets fallbackOccurred true and stores from and to`() {
        tracker.reset("M", DelegateType.GPU, 0L)
        tracker.recordFallback(from = DelegateType.QNN_NPU, to = DelegateType.GPU)
        val data = tracker.toBenchmarkData()
        assertTrue(data.fallbackOccurred)
        assertEquals(DelegateType.QNN_NPU, data.fallbackFrom)
        assertEquals(DelegateType.GPU, data.fallbackTo)
    }

    @Test
    fun `no fallback recorded leaves fallbackOccurred false`() {
        tracker.reset("M", DelegateType.QNN_NPU, 0L)
        val data = tracker.toBenchmarkData()
        assertFalse(data.fallbackOccurred)
        assertNull(data.fallbackFrom)
        assertNull(data.fallbackTo)
    }

    // ── toReport() ────────────────────────────────────────────────────────────

    @Test
    fun `toReport contains model name and delegate`() {
        tracker.reset("YOLOv8n", DelegateType.QNN_NPU, 500L)
        tracker.recordRun(30L)
        val report = tracker.toReport()
        assertTrue(report.contains("YOLOv8n"))
        assertTrue(report.contains("QNN_NPU"))
        assertTrue(report.contains("500"))
    }

    @Test
    fun `toReport includes fallback line when fallback occurred`() {
        tracker.reset("M", DelegateType.CPU, 0L)
        tracker.recordFallback(DelegateType.QNN_NPU, DelegateType.CPU)
        val report = tracker.toReport()
        assertTrue(report.contains("Fallback"))
        assertTrue(report.contains("QNN_NPU"))
        assertTrue(report.contains("CPU"))
    }

    @Test
    fun `toReport does not include fallback line when no fallback`() {
        tracker.reset("M", DelegateType.GPU, 100L)
        val report = tracker.toReport()
        assertFalse(report.contains("Fallback"))
    }

    // ── toBenchmarkData() on empty session ────────────────────────────────────

    @Test
    fun `toBenchmarkData on empty session returns zeroed counters`() {
        tracker.reset("EmptyModel", DelegateType.CPU, 50L)
        val data = tracker.toBenchmarkData()
        assertEquals(0L, data.totalInferences)
        assertEquals(0.0, data.avgInferenceMs, 0.001)
        assertEquals(0L, data.minInferenceMs)
        assertEquals(0L, data.maxInferenceMs)
        assertEquals(0L, data.firstInferenceMs)
    }
}
