package dev.pivisolutions.dictus.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Glass-morphism card used across all Phase 4 screens.
 *
 * Renders a rounded, dark-surface card with a subtle white border (~15% opacity).
 * This visual style matches the iOS Dictus "glass card" design language.
 *
 * WHY GlassCard in core: Both the app module (model management, settings) and
 * future modules need this card. Placing it in core avoids duplication and keeps
 * the design system centralized.
 *
 * @param modifier Modifier applied to the outer clip/background container.
 * @param content Composable content rendered inside the card column.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content,
    )
}
