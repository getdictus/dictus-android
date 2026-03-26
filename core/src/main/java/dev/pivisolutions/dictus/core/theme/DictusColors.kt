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
    // --- Core brand colors ---
    val Background = Color(0xFF0A1628)
    val Accent = Color(0xFF3D7EFF)
    val AccentHighlight = Color(0xFF6BA3FF)
    val AccentDark = Color(0xFF2563EB)
    val Surface = Color(0xFF161C2C)

    // --- Semantic state colors ---
    val Recording = Color(0xFFEF4444)
    val Destructive = Color(0xFFEF4444) // Semantic alias for Recording
    val SmartMode = Color(0xFF8B5CF6)
    val Success = Color(0xFF22C55E)
    val SuccessDark = Color(0xFF16A34A)
    val SuccessSubtle = Color(0x3322C55E) // #22C55E at 20% opacity

    // --- Text colors ---
    val TextPrimary = Color(0xFFFAFAF9)
    val TextSecondary = Color(0xFF6B6B70)
    val OnBackground = Color.White
    val OnSurface = Color(0xFFE0E0E0)

    // --- Border / overlay colors ---
    val BorderSubtle = Color(0xFF2A2A2E)
    val GlassBorder = Color(0x26FFFFFF)    // White at ~15% opacity
    val InactiveDot = Color(0x4DFFFFFF)    // White at ~30% opacity

    // --- Component colors ---
    val IconBackground = Color(0xFF1E2A4A)
    val KeyBackground = Color(0xFF1E2538)
    val KeyText = Color.White
    val KeySpecialBackground = Color(0xFF2A3347)
}
