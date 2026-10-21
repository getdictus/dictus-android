package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.core.R as CoreR
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
    isDictationUndoAvailable: Boolean = false,
    onUndoDictation: () -> Unit = {},
    isNoWordsNoticeVisible: Boolean = false,
) {
    if (isNoWordsNoticeVisible) {
        NoWordsNoticeBar(modifier = modifier)
        return
    }
    if (isDictationUndoAvailable) {
        DictationUndoBar(onUndoDictation = onUndoDictation, modifier = modifier)
        return
    }
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

/**
 * Shown in place of the suggestion slots right after a dictation insertion: the slots are empty
 * at that point, and the offer disappears with the first keystroke or caret move.
 */
@Composable
private fun DictationUndoBar(onUndoDictation: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ime_undo_dictation_description)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clickable(onClick = onUndoDictation)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onUndoDictation(); true }
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ime_undo_dictation),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * Shown in place of the suggestion slots after a dictation that produced no text (#153). Grey,
 * like the iOS toolbar: the app works, there was simply nothing to transcribe. A polite live
 * region so TalkBack users hear it too.
 */
@Composable
private fun NoWordsNoticeBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(CoreR.string.dictation_no_words_detected),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
