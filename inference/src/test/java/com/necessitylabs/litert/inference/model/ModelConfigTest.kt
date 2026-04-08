/**
 * ModelConfigTest.kt — Unit tests for [ModelConfig] equals / hashCode and defaults.
 *
 * Verifies that [IntArray]-aware structural equality works correctly — the
 * standard data-class implementation treats IntArray by reference, which would
 * break cache-key comparisons.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.model

import com.necessitylabs.litert.inference.delegate.DelegateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigTest {

    private val baseDelegate = DelegateConfig(
        enableQnn = true,
        enableGpu = true,
        nativeLibraryDir = "/data/app/com.example/lib/arm64",
    )

    private fun makeConfig(
        modelPath: String = "/data/local/tmp/model.tflite",
        modelName: String = "TestModel",
        inputShape: IntArray? = null,
        numThreads: Int = 4,
        useXnnpack: Boolean = true,
    ) = ModelConfig(
        modelPath = modelPath,
        modelName = modelName,
        delegateConfig = baseDelegate,
        inputShape = inputShape,
        numThreads = numThreads,
        useXnnpack = useXnnpack,
    )

    // ── equals ────────────────────────────────────────────────────────────────

    @Test
    fun `equal configs with null inputShape are equal`() {
        val a = makeConfig(inputShape = null)
        val b = makeConfig(inputShape = null)
        assertEquals(a, b)
    }

    @Test
    fun `equal configs with same IntArray contents are equal`() {
        val a = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        val b = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        assertEquals(a, b)
    }

    @Test
    fun `configs with different IntArray contents are not equal`() {
        val a = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        val b = makeConfig(inputShape = intArrayOf(1, 320, 320, 3))
        assertNotEquals(a, b)
    }

    @Test
    fun `config with null inputShape is not equal to config with non-null inputShape`() {
        val a = makeConfig(inputShape = null)
        val b = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        assertNotEquals(a, b)
    }

    @Test
    fun `configs differing only in modelPath are not equal`() {
        val a = makeConfig(modelPath = "/data/a.tflite")
        val b = makeConfig(modelPath = "/data/b.tflite")
        assertNotEquals(a, b)
    }

    @Test
    fun `configs differing only in numThreads are not equal`() {
        val a = makeConfig(numThreads = 2)
        val b = makeConfig(numThreads = 4)
        assertNotEquals(a, b)
    }

    // ── hashCode ─────────────────────────────────────────────────────────────

    @Test
    fun `equal configs produce the same hashCode`() {
        val a = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        val b = makeConfig(inputShape = intArrayOf(1, 224, 224, 3))
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ── defaults ─────────────────────────────────────────────────────────────

    @Test
    fun `default numThreads is 4`() {
        assertEquals(4, makeConfig().numThreads)
    }

    @Test
    fun `default useXnnpack is true`() {
        assertTrue(makeConfig().useXnnpack)
    }

    @Test
    fun `default inputShape is null`() {
        assertEquals(null, makeConfig().inputShape)
    }

    // ── reflexive equality ────────────────────────────────────────────────────

    @Test
    fun `config is equal to itself`() {
        val a = makeConfig(inputShape = intArrayOf(1, 640, 640, 3))
        assertEquals(a, a)
    }
}
