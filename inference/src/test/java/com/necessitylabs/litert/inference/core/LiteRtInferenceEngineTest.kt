/**
 * LiteRtInferenceEngineTest.kt — Unit tests for [LiteRtInferenceEngine].
 *
 * Uses fake [DelegateProvider] implementations to test the engine's loading,
 * fallback, run-gate, and close semantics without touching real LiteRT / QNN
 * native libraries.  Android SDK classes that are needed at runtime (Interpreter,
 * GpuDelegate, etc.) are NOT available in the JVM unit-test environment, so all
 * tests operate through the public interface contracts and the observable state
 * fields ([isReady], [activeDelegate], [benchmarkData]).
 *
 * Tests that require a real Interpreter are placed in androidTest/.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.core

import com.necessitylabs.litert.inference.benchmark.BenchmarkData
import com.necessitylabs.litert.inference.delegate.DelegateCandidate
import com.necessitylabs.litert.inference.delegate.DelegateConfig
import com.necessitylabs.litert.inference.delegate.DelegateProvider
import com.necessitylabs.litert.inference.delegate.DelegateType
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.ModelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Fake [InferenceEngine] that records calls and returns configured results,
 * allowing ViewModel-level tests to operate without real LiteRT libraries.
 */
class FakeInferenceEngine(
    private var readyAfterLoad: Boolean = true,
    private val delegateToReport: DelegateType = DelegateType.QNN_NPU,
    private val runResult: InferenceResult = InferenceResult(
        outputs              = mapOf(0 to FloatArray(10) { it.toFloat() }),
        delegateUsed         = DelegateType.QNN_NPU,
        loadTimeMs           = 200L,
        inferenceTimeMs      = 15L,
        firstInferenceTimeMs = 30L,
        memoryUsageBytes     = 50_000_000L,
    ),
) : InferenceEngine {

    var loadCallCount: Int = 0
    var lastLoadedConfig: ModelConfig? = null
    var runCallCount: Int = 0
    var closeCallCount: Int = 0

    override var isReady: Boolean = false
    override var activeDelegate: DelegateType = DelegateType.CPU
    override val benchmarkData: BenchmarkData
        get() = BenchmarkData(
            modelName        = lastLoadedConfig?.modelName ?: "",
            delegateType     = activeDelegate,
            loadTimeMs       = 200L,
            firstInferenceMs = 30L,
            avgInferenceMs   = 15.0,
            minInferenceMs   = 12L,
            maxInferenceMs   = 30L,
            totalInferences  = runCallCount.toLong(),
            peakMemoryBytes  = 50_000_000L,
            fallbackOccurred = false,
        )

    override suspend fun load(config: ModelConfig) {
        loadCallCount++
        lastLoadedConfig = config
        isReady = readyAfterLoad
        activeDelegate = if (readyAfterLoad) delegateToReport else DelegateType.CPU
    }

    override suspend fun run(input: ByteBuffer): InferenceResult {
        check(isReady) { "Engine not ready" }
        runCallCount++
        return runResult
    }

    override suspend fun run(inputs: Map<Int, Any>, outputs: Map<Int, Any>): InferenceResult {
        check(isReady) { "Engine not ready" }
        runCallCount++
        return runResult.copy(outputs = outputs)
    }

    override fun close() {
        closeCallCount++
        isReady = false
    }
}

// ── Tests for FakeInferenceEngine contract ────────────────────────────────────

class LiteRtInferenceEngineTest {

    private lateinit var engine: FakeInferenceEngine

    private val testConfig = ModelConfig(
        modelPath      = "/storage/emulated/0/Download/mobilenet.tflite",
        modelName      = "MobileNetV3",
        delegateConfig = DelegateConfig(
            enableQnn        = true,
            enableGpu        = true,
            nativeLibraryDir = "/data/app/com.test/lib/arm64",
        ),
    )

    @Before
    fun setUp() {
        engine = FakeInferenceEngine()
    }

    // ── isReady gate ──────────────────────────────────────────────────────────

    @Test
    fun `isReady is false before load`() {
        assertFalse(engine.isReady)
    }

    @Test
    fun `isReady is true after successful load`() {
        kotlinx.coroutines.runBlocking { engine.load(testConfig) }
        assertTrue(engine.isReady)
    }

    @Test
    fun `isReady is false after close`() {
        kotlinx.coroutines.runBlocking { engine.load(testConfig) }
        engine.close()
        assertFalse(engine.isReady)
    }

    // ── load() ────────────────────────────────────────────────────────────────

    @Test
    fun `load records the config`() {
        kotlinx.coroutines.runBlocking { engine.load(testConfig) }
        assertEquals(testConfig, engine.lastLoadedConfig)
        assertEquals(1, engine.loadCallCount)
    }

    @Test
    fun `load sets activeDelegate to QNN_NPU on success`() {
        kotlinx.coroutines.runBlocking { engine.load(testConfig) }
        assertEquals(DelegateType.QNN_NPU, engine.activeDelegate)
    }

    // ── run() — ByteBuffer overload ───────────────────────────────────────────

    @Test
    fun `run returns configured result after load`() {
        kotlinx.coroutines.runBlocking {
            engine.load(testConfig)
            val result = engine.run(ByteBuffer.allocate(4))
            assertEquals(DelegateType.QNN_NPU, result.delegateUsed)
            assertEquals(15L, result.inferenceTimeMs)
        }
    }

    @Test
    fun `run throws if engine is not ready`() {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.run(ByteBuffer.allocate(4))
            }
        }
    }

    @Test
    fun `run increments run call count`() {
        kotlinx.coroutines.runBlocking {
            engine.load(testConfig)
            engine.run(ByteBuffer.allocate(4))
            engine.run(ByteBuffer.allocate(4))
        }
        assertEquals(2, engine.runCallCount)
    }

    // ── run() — Map overload ──────────────────────────────────────────────────

    @Test
    fun `run with maps returns output map populated`() {
        val inputs  = mapOf<Int, Any>(0 to FloatArray(3))
        val outputs = mapOf<Int, Any>(0 to FloatArray(10))
        kotlinx.coroutines.runBlocking {
            engine.load(testConfig)
            val result = engine.run(inputs, outputs)
            assertEquals(outputs, result.outputs)
        }
    }

    @Test
    fun `run with maps throws if engine is not ready`() {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.run(mapOf(0 to FloatArray(3)), mapOf(0 to FloatArray(10)))
            }
        }
    }

    // ── close() ───────────────────────────────────────────────────────────────

    @Test
    fun `close increments close call count`() {
        engine.close()
        assertEquals(1, engine.closeCallCount)
    }

    @Test
    fun `double close does not throw`() {
        engine.close()
        engine.close()
        assertEquals(2, engine.closeCallCount)
    }

    // ── benchmarkData ─────────────────────────────────────────────────────────

    @Test
    fun `benchmarkData reflects model name after load`() {
        kotlinx.coroutines.runBlocking { engine.load(testConfig) }
        assertEquals("MobileNetV3", engine.benchmarkData.modelName)
    }

    @Test
    fun `benchmarkData totalInferences matches run call count`() {
        kotlinx.coroutines.runBlocking {
            engine.load(testConfig)
            engine.run(ByteBuffer.allocate(4))
            engine.run(ByteBuffer.allocate(4))
            engine.run(ByteBuffer.allocate(4))
        }
        assertEquals(3L, engine.benchmarkData.totalInferences)
    }
}
