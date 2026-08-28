package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken

enum class SuggestionPresentationMode { COMPLETION, PREDICTION }

/** Stable three-slot bar with distinct completion and next-word prediction presentation. */
@Composable
fun SuggestionBar(
    currentWord: String,
    suggestions: List<String>,
    onSuggestionSelected: (String, NextWordPredictionToken?) -> Unit,
    onCurrentWordSelected: () -> Unit,
    modifier: Modifier = Modifier,
    mode: SuggestionPresentationMode = SuggestionPresentationMode.COMPLETION,
    predictionToken: NextWordPredictionToken? = null,
) {
    val slots = when (mode) {
        SuggestionPresentationMode.COMPLETION -> listOf(currentWord) + suggestions.take(2)
        SuggestionPresentationMode.PREDICTION -> suggestions.take(3)
    }.let { it + List(3 - it.size) { "" } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, text ->
            val isRawInput = mode == SuggestionPresentationMode.COMPLETION && index == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(
                        if (text.isNotEmpty()) {
                            Modifier.clickable(role = Role.Button) {
                                if (isRawInput) {
                                    onCurrentWordSelected()
                                } else {
                                    onSuggestionSelected(
                                        text,
                                        predictionToken.takeIf {
                                            mode == SuggestionPresentationMode.PREDICTION
                                        },
                                    )
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = if (
                            mode == SuggestionPresentationMode.COMPLETION && index == 1
                        ) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index < 2) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(LocalDictusColors.current.borderSubtle),
                )
            }
        }
    }
}
