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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * Gboard-style 3-slot suggestion bar, always visible above the keyboard.
 *
 * Layout (matches standard Android keyboard behavior):
 *   - LEFT slot: current word being typed (raw input echo)
 *   - CENTER slot: primary suggestion (bold)
 *   - RIGHT slot: secondary suggestion
 *
 * Vertical separators always visible between slots, even when empty.
 * This prevents layout glitches from the bar appearing/disappearing.
 *
 * @param currentWord The word currently being typed (shown in left slot).
 * @param suggestions List of up to 2 suggestion strings (center + right slots).
 * @param onSuggestionSelected Callback with selected suggestion text.
 * @param onCurrentWordSelected Callback when user taps the left slot (commits raw input).
 * @param modifier Optional modifier.
 */
@Composable
fun SuggestionBar(
    currentWord: String,
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onCurrentWordSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Slots: [currentWord, suggestion1, suggestion2]
    val suggestion1 = suggestions.getOrElse(0) { "" }
    val suggestion2 = suggestions.getOrElse(1) { "" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEFT slot: current word being typed
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .then(
                    if (currentWord.isNotEmpty()) {
                        Modifier.clickable { onCurrentWordSelected() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (currentWord.isNotEmpty()) {
                Text(
                    text = currentWord,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Separator
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(LocalDictusColors.current.borderSubtle),
        )

        // CENTER slot: primary suggestion (bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .then(
                    if (suggestion1.isNotEmpty()) {
                        Modifier.clickable { onSuggestionSelected(suggestion1) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (suggestion1.isNotEmpty()) {
                Text(
                    text = suggestion1,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Separator
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(LocalDictusColors.current.borderSubtle),
        )

        // RIGHT slot: secondary suggestion
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .then(
                    if (suggestion2.isNotEmpty()) {
                        Modifier.clickable { onSuggestionSelected(suggestion2) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (suggestion2.isNotEmpty()) {
                Text(
                    text = suggestion2,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
