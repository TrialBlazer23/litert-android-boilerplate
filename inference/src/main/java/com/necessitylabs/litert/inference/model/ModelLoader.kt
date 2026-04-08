/**
 * ModelLoader.kt — File-system utilities for locating and validating model files.
 *
 * Provides helpers for: copying small bundled models out of APK assets into the
 * app's private files directory; verifying that a model file is accessible and
 * non-empty before attempting to load it; and reporting file size for log output.
 *
 * Large models (LLMs, foundation models ≥ 500 MB) should be placed on-device by
 * the user (via `adb push` or DownloadManager) and their absolute paths passed
 * directly to [ModelConfig.modelPath].  [ModelLoader] validates those paths but
 * does not transfer them.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.model

import android.content.Context
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

private const val TAG = "ModelLoader"

/** Maximum asset size treated as a "small" model eligible for asset copying. 100 MB. */
private const val MAX_ASSET_COPY_BYTES = 100L * 1024 * 1024

object ModelLoader {

    /**
     * Copies a `.tflite` model from the APK's [assets] directory into the
     * app's private files directory and returns the absolute destination path.
     *
     * The copy is skipped when the destination file already exists and its size
     * matches the source — allowing subsequent app launches to skip I/O.
     *
     * This method is intended only for **small, bundled models** (≤ 100 MB).
     * Large models must be pushed to the device separately; use [verifyModelFile]
     * to confirm their presence.
     *
     * @param context   Android [Context] used to open assets and resolve [filesDir].
     * @param assetPath Relative path inside the `assets/` directory,
     *                  e.g. `"models/mobilenet_v3_small.tflite"`.
     * @param destDir   Destination directory inside the app's private storage.
     *                  Defaults to `<filesDir>/models/`.  Will be created if absent.
     * @return Absolute path of the copied model file.
     * @throws IOException If the asset cannot be opened or the destination write fails.
     * @throws IllegalArgumentException If [assetPath] resolves to a file larger than
     *                                  [MAX_ASSET_COPY_BYTES].
     */
    fun copyModelFromAssets(
        context: Context,
        assetPath: String,
        destDir: String = "${context.filesDir}/models",
    ): String {
        val destDirPath = Path(destDir)
        destDirPath.createDirectories()

        val fileName = Path(assetPath).fileName.toString()
        val destPath = destDirPath.resolve(fileName)

        // Check asset size before starting the copy.
        val assetSize = runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrElse { e ->
            throw IOException("Cannot open asset '$assetPath' to check size: ${e.message}", e)
        }

        require(assetSize <= MAX_ASSET_COPY_BYTES) {
            "Asset '$assetPath' is ${assetSize / (1024 * 1024)} MB, exceeding the " +
                "${MAX_ASSET_COPY_BYTES / (1024 * 1024)} MB asset-copy limit. " +
                "Push large models via adb or DownloadManager."
        }

        // Skip copy if the file already exists with the same size.
        if (destPath.exists() && destPath.fileSize() == assetSize) {
            Log.d(TAG, "Asset already copied: $destPath (${assetSize / 1024} KB)")
            return destPath.toString()
        }

        Log.i(TAG, "Copying asset '$assetPath' → $destPath (${assetSize / 1024} KB)")
        try {
            context.assets.open(assetPath).use { input ->
                destPath.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to copy asset '$assetPath' to $destPath: ${e.message}", e)
        }

        Log.i(TAG, "Asset copy complete: $destPath")
        return destPath.toString()
    }

    /**
     * Verifies that a model file exists at [path] and is readable.
     *
     * Performs three checks: path resolves to a regular file (not a directory),
     * the file is readable by the process, and the file is non-empty.
     *
     * @param path Absolute path to the `.tflite` model file.
     * @return [true] if the file passes all checks.
     * @throws FileNotFoundException If the file does not exist or is not a regular file.
     * @throws SecurityException     If the file exists but the process cannot read it.
     * @throws IOException           If the file exists but is empty (likely a failed
     *                               download or incomplete `adb push`).
     */
    fun verifyModelFile(path: String): Boolean {
        val p = Path(path)

        if (!p.exists() || !p.isRegularFile()) {
            throw FileNotFoundException(
                "Model file not found or is not a regular file: $path\n" +
                    "Push it with: adb push <local_model.tflite> $path"
            )
        }

        if (!p.isReadable()) {
            throw SecurityException(
                "Model file exists but is not readable — check file permissions: $path"
            )
        }

        val size = p.fileSize()
        if (size == 0L) {
            throw IOException(
                "Model file at $path is empty (0 bytes). " +
                    "The adb push or download may have been interrupted."
            )
        }

        Log.d(TAG, "Model verified: $path (${size / 1024} KB)")
        return true
    }

    /**
     * Returns the size of the model file at [path] in bytes.
     *
     * Useful for logging and for quick sanity checks when a model may have been
     * partially written.  Does not verify model integrity (magic bytes, etc.).
     *
     * @param path Absolute path to the model file.
     * @return File size in bytes, or -1L if the path does not exist or an error occurs.
     */
    fun getModelFileSize(path: String): Long {
        return try {
            val size = Path(path).fileSize()
            Log.d(TAG, "Model size: $path → ${size / 1024} KB")
            size
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read file size for $path: ${e.message}")
            -1L
        }
    }

    /**
     * Memory-maps a model file as a read-only [java.nio.ByteBuffer], which is
     * required by the LiteRT [Interpreter] constructor.
     *
     * Memory mapping avoids copying the model into the Java heap and is the
     * standard way to pass a `.tflite` model to LiteRT on Android.
     *
     * @param path Absolute path to the `.tflite` file.  Must pass [verifyModelFile] first.
     * @return Read-only [java.nio.MappedByteBuffer] backed by the file.
     * @throws IOException If the file cannot be opened or mapped.
     */
    fun mapModelFile(path: String): java.nio.MappedByteBuffer {
        return try {
            FileChannel.open(Path(path), StandardOpenOption.READ).use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        } catch (e: IOException) {
            throw IOException("Failed to memory-map model file at $path: ${e.message}", e)
        }
    }
}
