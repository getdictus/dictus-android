package dev.pivisolutions.dictus.pro

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.graphics.vector.ImageVector
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.premium.ProFeature

/** Name on the hub card, iOS `ProFeature.displayName`. */
@get:StringRes
val ProFeature.titleRes: Int
    get() = when (this) {
        ProFeature.SMART_MODE -> R.string.pro_feature_smart_mode
        ProFeature.HISTORY -> R.string.pro_feature_history
        ProFeature.VOCABULARY -> R.string.pro_feature_vocabulary
        ProFeature.VOICE_NOTES -> R.string.pro_feature_voice_notes
    }

/** Line under the name, iOS `ProFeature.paywallDescription`. */
@get:StringRes
val ProFeature.descriptionRes: Int
    get() = when (this) {
        ProFeature.SMART_MODE -> R.string.pro_feature_smart_mode_description
        ProFeature.HISTORY -> R.string.pro_feature_history_description
        ProFeature.VOCABULARY -> R.string.pro_feature_vocabulary_description
        ProFeature.VOICE_NOTES -> R.string.pro_feature_voice_notes_description
    }

/** The Material counterpart of iOS `ProFeature.icon` (sparkles, clock, book, waveform). */
val ProFeature.icon: ImageVector
    get() = when (this) {
        ProFeature.SMART_MODE -> Icons.Outlined.AutoAwesome
        ProFeature.HISTORY -> Icons.Outlined.History
        ProFeature.VOCABULARY -> Icons.AutoMirrored.Outlined.MenuBook
        ProFeature.VOICE_NOTES -> Icons.Outlined.GraphicEq
    }
