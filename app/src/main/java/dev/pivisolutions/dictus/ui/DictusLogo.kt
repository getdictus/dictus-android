package dev.pivisolutions.dictus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * The three-bar Dictus logo with the app icon's proportions (iOS `DictusLogo` and
 * `DictusLogoGeometry`, read from `assets/brand/appicon-dark.svg`): bars 18, 42 and 27 units
 * high, 9 wide, 7.5 apart, with round ends; side bars at 45 % and 65 % opacity, the centre one
 * in the brand gradient.
 *
 * WHY every size derives from [height], the tallest bar: a logo is a picture. Fixed widths and
 * gaps next to a free height are what stretched the Home logo upward (#376). Derived, the logo
 * keeps the icon's shape at any size, and is as wide as it is tall (3 × 9 + 2 × 7.5 = 42).
 *
 * @param sideBarColor white on a dark background, grey on a light one, as iOS does.
 */
@Composable
fun DictusLogo(height: Dp, sideBarColor: Color, modifier: Modifier = Modifier) {
    val barWidth = height * (9f / 42f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(height * (7.5f / 42f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoBar(barWidth, height * (18f / 42f), SolidColor(sideBarColor.copy(alpha = 0.45f)))
        LogoBar(barWidth, height, Brush.verticalGradient(listOf(DictusColors.AccentHighlight, DictusColors.AccentDark)))
        LogoBar(barWidth, height * (27f / 42f), SolidColor(sideBarColor.copy(alpha = 0.65f)))
    }
}

@Composable
private fun LogoBar(width: Dp, height: Dp, brush: Brush) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(brush),
    )
}

/**
 * The launcher icon, drawn from its adaptive layers in [shape] (iOS `DictusIconTile`). A
 * launcher shows the middle 72 dp of the 108 dp layers, so each layer is drawn at 1.5 times
 * [size] and clipped to it. Drawing the layers rather than a copy keeps one source of truth.
 */
@Composable
fun DictusAppIcon(size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}
