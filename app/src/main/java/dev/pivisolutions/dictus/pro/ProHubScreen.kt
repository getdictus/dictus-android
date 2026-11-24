package dev.pivisolutions.dictus.pro

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProHub
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ui.DictusAppIcon

/** The hub, reading [ProViewModel]. */
@Composable
fun ProHubRoute(
    onClose: () -> Unit,
    onOpenFeature: (ProFeature) -> Unit,
    viewModel: ProViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ProHubScreen(state = state, onClose = onClose, onOpenFeature = onOpenFeature)
}

/**
 * The Dictus Pro hub for someone who has Pro (#207), laid out as iOS `PaywallView.hubContent`
 * with `ProHubHero`, `ProHubFeatureCard` and `ProHubMembershipCard`
 * (DictusApp/Views/PaywallView.swift, ProHubSections.swift): a cross floating top left, the hero
 * (glowing icon tile, « Dictus Pro », « Votre voix, sans limites »), one card per feature with
 * « Activé » / « Désactivé » and a chevron, then the block under the cards and the legal links.
 *
 * A card always opens its feature's screen, switched off included: that screen holds the switch
 * (iOS #216, decision 4 as amended on device).
 */
@Composable
fun ProHubScreen(
    state: ProViewModel.State,
    onClose: () -> Unit,
    onOpenFeature: (ProFeature) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val termsUrl = stringResource(R.string.settings_terms_url)
    val privacyUrl = stringResource(R.string.settings_privacy_url)
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pro_hub"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The cross floats over the content; the hero starts below it, as on iOS.
            Spacer(modifier = Modifier.height(52.dp))
            ProHubHero()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProHub.listedFeatures.forEach { feature ->
                    ProHubFeatureCard(
                        feature = feature,
                        isAvailable = state.isAvailable(feature),
                        onOpen = { onOpenFeature(feature) },
                    )
                }
            }
            if (state.showsDebugForcedNotice) DebugForcedNotice()
            LegalLinks(
                onTerms = { open(termsUrl) },
                onPrivacy = { open(privacyUrl) },
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
        }
        CloseButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 4.dp),
        )
    }
}

/** iOS `ProHubHero`: the app icon tile with a blue glow, the gradient title, the tagline. */
@Composable
private fun ProHubHero() {
    val tileShape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DictusAppIcon(
            size = 96.dp,
            shape = tileShape,
            modifier = Modifier.shadow(
                elevation = 20.dp,
                shape = tileShape,
                ambientColor = DictusColors.Accent,
                spotColor = DictusColors.Accent,
            ),
        )
        Text(
            text = stringResource(R.string.pro_title),
            style = TextStyle(
                brush = Brush.linearGradient(listOf(DictusColors.AccentHighlight, DictusColors.Accent)),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = stringResource(R.string.pro_hub_tagline),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 17.sp,
        )
    }
}

/**
 * iOS `ProHubFeatureCard`: a settings row, not a switch — the feature, its state as a value,
 * a chevron. The whole card is the target.
 */
@Composable
private fun ProHubFeatureCard(feature: ProFeature, isAvailable: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(12.dp)
            .testTag("pro_hub_feature_${feature.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = feature.icon,
            contentDescription = null,
            tint = if (feature == ProFeature.SMART_MODE) DictusColors.Accent else DictusColors.AccentHighlight,
            modifier = Modifier.width(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(feature.titleRes),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(feature.descriptionRes),
                color = LocalDictusColors.current.textSecondary,
                fontSize = 13.sp,
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(if (isAvailable) R.string.pro_feature_on else R.string.pro_feature_off),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 17.sp,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}

/** iOS `ProHubMembershipCard.debugForcedNotice`: nothing is sold, and why. Orange, as iOS. */
@Composable
private fun DebugForcedNotice() {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = stringResource(R.string.pro_hub_debug_forced),
        color = DebugOrange,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, shape)
            .padding(12.dp),
    )
}

/** « Conditions d'utilisation · Confidentialité », iOS `PaywallView.legalLinksRow`. */
@Composable
private fun LegalLinks(onTerms: () -> Unit, onPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalDictusColors.current.textSecondary
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.pro_hub_terms),
            color = color,
            fontSize = 12.sp,
            modifier = Modifier.clickable(role = Role.Button, onClick = onTerms).padding(4.dp),
        )
        Text(text = "·", color = color, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.pro_hub_privacy),
            color = color,
            fontSize = 12.sp,
            modifier = Modifier.clickable(role = Role.Button, onClick = onPrivacy).padding(4.dp),
        )
    }
}

/** iOS `floatingCloseButton`: a 44 dp glass circle with an accent cross. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pro_hub_close_cd)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("pro_hub_close"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = DictusColors.Accent)
    }
}

/** iOS `Color.orange` in the dark appearance. */
private val DebugOrange = Color(0xFFFF9F0A)
