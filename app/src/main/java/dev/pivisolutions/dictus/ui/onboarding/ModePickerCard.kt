package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme

/**
 * Two side-by-side keyboard mode selection cards for onboarding step 4.
 *
 * Presents two options:
 * - "ABC" (azerty/abc layout) — starts with letters visible
 * - "123" (numeric layout) — starts with numbers visible
 *
 * The selected card gets an accent-blue border (2dp) and blue text.
 * The unselected card gets a subtle border (1dp, #2A2A2E) and grey text.
 *
 * WHY two cards (not radio buttons / Chips): The UI-SPEC calls for large, tappable
 * cards that clearly show what each layout looks like. The visual differentiation
 * (size, emoji-style label, color) is intentional for users unfamiliar with IME settings.
 *
 * @param selectedLayout Layout key — "azerty" for letters, "numeric" for numbers.
 * @param onSelect       Called with the newly selected layout key.
 */
@Composable
fun ModePickerCard(
    selectedLayout: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LayoutOptionCard(
            label = "ABC",
            description = "Lettres",
            layoutKey = "azerty",
            isSelected = selectedLayout == "azerty",
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        LayoutOptionCard(
            label = "123",
            description = "Chiffres",
            layoutKey = "numeric",
            isSelected = selectedLayout == "numeric",
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Individual layout option card within the mode picker.
 */
@Composable
private fun LayoutOptionCard(
    label: String,
    description: String,
    layoutKey: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card_scale_$layoutKey",
    )

    val borderColor = if (isSelected) DictusColors.Accent else LocalDictusColors.current.borderSubtle
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val labelColor = if (isSelected) DictusColors.Accent else LocalDictusColors.current.textSecondary
    val descColor = if (isSelected) MaterialTheme.colorScheme.onBackground else LocalDictusColors.current.textSecondary

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onSelect(layoutKey) },
                )
            }
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Large layout label ("ABC" or "123")
        Text(
            text = label,
            color = labelColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        // Description label
        Text(
            text = description,
            color = descColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        // Selection indicator: filled check circle (selected) or empty circle outline (unselected)
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "S\u00e9lectionn\u00e9",
                tint = DictusColors.Accent,
                modifier = Modifier.size(24.dp),
            )
        } else {
            // Empty circle outline for unselected state
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(2.dp, LocalDictusColors.current.borderSubtle, CircleShape),
            )
        }
    }
}
