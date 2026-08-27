package dev.pivisolutions.dictus.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.R
import dev.pivisolutions.dictus.core.service.SttEngineState

/** Full-surface gate shared by the app, onboarding, and the complete IME input view. */
@Composable
fun ModelLoadingOverlay(
    engineState: SttEngineState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = engineState is SttEngineState.Loading
    val failed = engineState is SttEngineState.Failed
    if (!loading && !failed) return

    val accessibilityLabel = stringResource(
        if (loading) R.string.model_loading_accessibility else R.string.model_loading_failed_accessibility,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A1628))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF4A90E2))
                Text(
                    text = stringResource(R.string.model_loading_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.model_loading_body),
                    color = Color(0xFFB8C5D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.model_loading_failed_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.model_loading_failed_body),
                    color = Color(0xFFB8C5D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.model_loading_retry))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.model_loading_cancel))
                }
            }
        }
    }
}
