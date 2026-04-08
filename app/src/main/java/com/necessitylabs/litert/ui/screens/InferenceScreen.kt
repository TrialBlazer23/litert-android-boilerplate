/**
 * InferenceScreen.kt — Compose screen demonstrating classical ML inference.
 *
 * Displays the engine lifecycle state, allows the user to load a model and run a
 * single inference pass, then shows the benchmark results. Intentionally minimal —
 * this is a demo scaffold to be replaced when forking the boilerplate.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.necessitylabs.litert.R
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.InferenceState

/**
 * Inference demo screen. Wired to [InferenceViewModel] via Hilt.
 *
 * @param viewModel Hilt-injected ViewModel. Defaults to the hiltViewModel() factory
 *                  so the composable can be previewed by supplying a test ViewModel.
 */
@Composable
fun InferenceScreen(
    viewModel: InferenceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Heading ───────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.inference_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.inference_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // ── Engine state card ─────────────────────────────────────────────────
        StateCard(engineState = uiState.engineState)

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.load() },
                enabled = uiState.engineState is InferenceState.Idle ||
                    uiState.engineState is InferenceState.Error,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.btn_load_model))
            }

            OutlinedButton(
                onClick = { viewModel.runInference() },
                enabled = uiState.engineState is InferenceState.Ready,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.btn_run_inference))
            }
        }

        // ── Results card ──────────────────────────────────────────────────────
        uiState.lastResult?.let { result ->
            BenchmarkCard(result = result)
        }

        // ── Error message ─────────────────────────────────────────────────────
        uiState.errorMessage?.let { error ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

/**
 * Displays the current engine lifecycle state with an appropriate visual treatment.
 *
 * @param engineState The state to render.
 */
@Composable
private fun StateCard(engineState: InferenceState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (engineState is InferenceState.Loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                )
            }

            val (label, description) = when (engineState) {
                is InferenceState.Idle -> "Idle" to "No model loaded."
                is InferenceState.Loading ->
                    "Loading…" to "Selecting delegate and loading model."
                is InferenceState.Ready ->
                    "Ready" to "Delegate: ${engineState.delegateName}  |  " +
                        "Load: ${engineState.loadTimeMs} ms"
                is InferenceState.Error ->
                    "Error" to (engineState.message)
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (engineState) {
                        is InferenceState.Ready -> MaterialTheme.colorScheme.primary
                        is InferenceState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Displays benchmark metrics from the last completed inference run.
 *
 * @param result The [InferenceResult.Success] containing timing and memory data.
 */
@Composable
private fun BenchmarkCard(result: InferenceResult) {
    if (result !is InferenceResult.Success) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Benchmark Results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            BenchmarkRow("Delegate", result.delegateName)
            BenchmarkRow("Inference time", "${result.inferenceTimeMs} ms")
            BenchmarkRow("Memory usage", "${result.memoryUsageKb} KB")
        }
    }
}

/**
 * A single label/value row inside a benchmark card.
 *
 * @param label The metric name.
 * @param value The formatted metric value.
 */
@Composable
private fun BenchmarkRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
