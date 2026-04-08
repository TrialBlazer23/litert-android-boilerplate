/**
 * ModelLoaderTest.kt — Unit tests for [ModelLoader] file-system utilities.
 *
 * Tests that can run on the JVM without Android SDK (verifyModelFile,
 * getModelFileSize) use real [java.nio.file] operations against temp files.
 * Android-dependent functions (copyModelFromAssets, mapModelFile) are tested
 * in androidTest/.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileNotFoundException
import java.io.IOException

class ModelLoaderTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // ── verifyModelFile — happy path ──────────────────────────────────────────

    @Test
    fun `verifyModelFile returns true for valid non-empty file`() {
        val file = tmpFolder.newFile("valid.tflite")
        file.writeBytes(ByteArray(1024) { 0xFF.toByte() })
        assertTrue(ModelLoader.verifyModelFile(file.absolutePath))
    }

    // ── verifyModelFile — file does not exist ─────────────────────────────────

    @Test
    fun `verifyModelFile throws FileNotFoundException for missing path`() {
        val missingPath = tmpFolder.root.resolve("nonexistent.tflite").absolutePath
        assertThrows(FileNotFoundException::class.java) {
            ModelLoader.verifyModelFile(missingPath)
        }
    }

    // ── verifyModelFile — directory instead of file ───────────────────────────

    @Test
    fun `verifyModelFile throws FileNotFoundException when path is a directory`() {
        val dir = tmpFolder.newFolder("notafile")
        assertThrows(FileNotFoundException::class.java) {
            ModelLoader.verifyModelFile(dir.absolutePath)
        }
    }

    // ── verifyModelFile — empty file ──────────────────────────────────────────

    @Test
    fun `verifyModelFile throws IOException for empty file`() {
        val emptyFile = tmpFolder.newFile("empty.tflite")
        // Leave file empty (0 bytes).
        assertThrows(IOException::class.java) {
            ModelLoader.verifyModelFile(emptyFile.absolutePath)
        }
    }

    // ── getModelFileSize — happy path ─────────────────────────────────────────

    @Test
    fun `getModelFileSize returns correct byte count`() {
        val file = tmpFolder.newFile("model.tflite")
        file.writeBytes(ByteArray(4096))
        assertEquals(4096L, ModelLoader.getModelFileSize(file.absolutePath))
    }

    // ── getModelFileSize — missing file ───────────────────────────────────────

    @Test
    fun `getModelFileSize returns -1 for missing path`() {
        val missingPath = tmpFolder.root.resolve("gone.tflite").absolutePath
        assertEquals(-1L, ModelLoader.getModelFileSize(missingPath))
    }

    // ── getModelFileSize — zero-byte file ─────────────────────────────────────

    @Test
    fun `getModelFileSize returns 0 for empty file`() {
        val file = tmpFolder.newFile("zero.tflite")
        assertEquals(0L, ModelLoader.getModelFileSize(file.absolutePath))
    }
}
