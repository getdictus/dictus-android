package dev.pivisolutions.dictus.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Stub theme for Task 1 build verification. Full implementation in Task 2.
private val DictusDarkColorScheme = darkColorScheme()

@Composable
fun DictusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DictusDarkColorScheme,
        content = content,
    )
}
