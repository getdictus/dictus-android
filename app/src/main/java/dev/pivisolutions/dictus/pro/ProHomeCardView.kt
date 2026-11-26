package dev.pivisolutions.dictus.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.premium.ProHomeCardContent
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * The Home Pro card, iOS `ProBannerView` (DictusApp/Views/ProBannerView.swift, #216 decision 16).
 *
 * Only the member card exists on Android (see `ProHomeCard`): the same family as Home's model
 * card, the crown, « Dictus Pro » and how many features are on, a chevron to the hub. No price,
 * no sales copy.
 */
@Composable
fun ProHomeCardView(content: ProHomeCardContent, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (content !is ProHomeCardContent.Member) return
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(16.dp)
            .testTag("home_pro_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.WorkspacePremium, contentDescription = null, tint = DictusColors.Accent)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pro_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // "0 features on" reads as a fault; every switch off is a choice (iOS).
            Text(
                text = if (content.activeFeatures == 0) {
                    stringResource(R.string.pro_home_card_all_off)
                } else {
                    pluralStringResource(R.plurals.pro_home_card_active_features, content.activeFeatures, content.activeFeatures)
                },
                color = LocalDictusColors.current.textSecondary,
                fontSize = 13.sp,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}
