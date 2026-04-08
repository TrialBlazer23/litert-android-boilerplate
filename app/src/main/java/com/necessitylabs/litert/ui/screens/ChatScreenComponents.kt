/**
 * ChatScreenComponents.kt — Reusable Compose components for ChatScreen.
 *
 * Split from ChatScreen.kt to keep both files within the 300-line limit.
 * Contains: MessageBubble, BackendBadge, ChatInputRow.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.necessitylabs.litert.R
import com.necessitylabs.litert.lm.backend.LmBackendType
import com.necessitylabs.litert.ui.theme.CpuBadge
import com.necessitylabs.litert.ui.theme.GpuBadge
import com.necessitylabs.litert.ui.theme.NpuBadge

/**
 * Renders a single chat message as a left-aligned (model) or right-aligned (user) bubble.
 *
 * @param message The [ChatMessage] to render.
 */
@Composable
internal fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = message.text.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                // Streaming indicator — tiny spinner shown while tokens are arriving.
                if (message.isStreaming) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Coloured pill badge indicating which hardware backend is active.
 *
 * @param backend The [LmBackendType] to display.
 */
@Composable
internal fun BackendBadge(backend: LmBackendType) {
    val (label, bgColor) = when (backend) {
        LmBackendType.NPU -> "NPU" to NpuBadge
        LmBackendType.GPU -> "GPU" to GpuBadge
        LmBackendType.CPU -> "CPU" to CpuBadge
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Bottom text input field and send button row.
 *
 * The field is disabled while the engine is loading or generation is in flight.
 * Pressing IME Send triggers the same callback as tapping the icon button.
 *
 * @param isReady      True when the engine is in [com.necessitylabs.litert.lm.engine.LmEngineState.Ready].
 * @param isGenerating True while a streaming response is currently active.
 * @param onSend       Invoked with the trimmed user message text.
 */
@Composable
internal fun ChatInputRow(
    isReady: Boolean,
    isGenerating: Boolean,
    onSend: (String) -> Unit,
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val canSend = isReady && !isGenerating && inputText.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
                Text(
                    text = if (isReady) {
                        stringResource(R.string.chat_input_hint)
                    } else {
                        stringResource(R.string.chat_input_hint_loading)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            enabled = isReady && !isGenerating,
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (canSend) {
                    onSend(inputText.trim())
                    inputText = ""
                }
            }),
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = {
                if (canSend) {
                    onSend(inputText.trim())
                    inputText = ""
                }
            },
            enabled = canSend,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.btn_send),
                tint = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
