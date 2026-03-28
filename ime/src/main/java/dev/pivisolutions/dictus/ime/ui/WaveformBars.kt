package dev.pivisolutions.dictus.ime.ui

// Re-export from core for backward compatibility within ime module.
// RecordingScreen.kt and any other ime code can continue importing
// from dev.pivisolutions.dictus.ime.ui.WaveformBars without changes.
import dev.pivisolutions.dictus.core.ui.WaveformBars as CoreWaveformBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WaveformBars(
    energyLevels: List<Float>,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    processingPhase: Double = 0.0,
) = CoreWaveformBars(
    energyLevels = energyLevels,
    modifier = modifier,
    isProcessing = isProcessing,
    processingPhase = processingPhase,
)
