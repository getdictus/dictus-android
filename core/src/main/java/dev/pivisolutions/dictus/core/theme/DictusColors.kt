package dev.pivisolutions.dictus.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand color tokens for the Dictus design system.
 *
 * These values are ported directly from the iOS Dictus app to ensure
 * visual consistency across platforms. Each color maps to a specific
 * role in the UI (background, accent, recording state, etc.).
 */
object DictusColors {
    val Background = Color(0xFF0A1628)
    val Accent = Color(0xFF3D7EFF)
    val AccentHighlight = Color(0xFF6BA3FF)
    val Surface = Color(0xFF161C2C)
    val Recording = Color(0xFFEF4444)
    val SmartMode = Color(0xFF8B5CF6)
    val Success = Color(0xFF22C55E)
    val OnBackground = Color.White
    val OnSurface = Color(0xFFE0E0E0)
    val KeyBackground = Color(0xFF1E2538)
    val KeyText = Color.White
    val KeySpecialBackground = Color(0xFF2A3347)
}
