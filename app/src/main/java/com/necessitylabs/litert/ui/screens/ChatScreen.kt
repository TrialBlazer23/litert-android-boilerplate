/**
 * ChatScreen.kt — Compose screen demonstrating streaming LLM chat via LiteRT-LM.
 *
 * Features:
 *   • Scrollable message list with user and model bubbles.
 *   • Streaming token animation — model bubble grows as tokens arrive.
 *   • Benchmark badge showing tok/s and active backend.
 *   • Text input with Send button; disabled during generation and before engine is ready.
 *
 * Chat input row and bubble composables are split into ChatScreenComponents.kt to
 * keep this file within the 300-line limit.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.necessitylabs.litert.R
import com.necessitylabs.litert.lm.engine.LmEngineState

/**
 * Chat demo screen. Wired to [ChatViewModel] via Hilt.
 *
 * @param viewModel Hilt-injected ViewModel. Override in tests with a fake.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom whenever new messages or tokens arrive.
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // ── Engine state / benchmark bar ──────────────────────────────────────
        EngineStatusBar(engineState = uiState.engineState, uiState = uiState)

        // ── Message list ──────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // ── Error snack ───────────────────────────────────────────────────────
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // ── Input row ─────────────────────────────────────────────────────────
        ChatInputRow(
            isReady = uiState.engineState is LmEngineState.Ready,
            isGenerating = uiState.messages.any { it.isStreaming },
            onSend = { text -> viewModel.sendMessage(text) },
        )
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

/**
 * Top bar showing engine lifecycle state and benchmark metrics when available.
 *
 * @param engineState Current engine lifecycle state.
 * @param uiState     Full UI state providing benchmark snapshot access.
 */
@Composable
internal fun EngineStatusBar(engineState: LmEngineState, uiState: ChatUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusText = when (engineState) {
                is LmEngineState.Idle -> stringResource(R.string.chat_engine_idle)
                is LmEngineState.Loading ->
                    stringResource(R.string.chat_engine_loading, engineState.backend.name)
                is LmEngineState.Ready ->
                    stringResource(R.string.chat_engine_ready, engineState.loadTimeMs)
                is LmEngineState.Error ->
                    stringResource(R.string.chat_engine_error)
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = when (engineState) {
                    is LmEngineState.Ready -> MaterialTheme.colorScheme.primary
                    is LmEngineState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            uiState.benchmark?.let { bench ->
                BackendBadge(backend = bench.activeBackend)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "%.1f tok/s".format(bench.lastTokensPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (engineState is LmEngineState.Loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
