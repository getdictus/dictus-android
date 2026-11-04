package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.ui.KeyboardView
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * The language step (#198): the keyboard language and its layout, ported from iOS
 * `DictusApp/Onboarding/LanguageSetupPage.swift`.
 *
 * The page opens on the phone's language ([LanguageSetup.prefilled]), so « Continuer » is the
 * whole interaction for most users. « Changer » lists the four keyboard languages, the
 * segment picks the layout, and the preview below draws the keyboard in that layout.
 *
 * WHY ONE « Dictée et clavier » ROW: dictation follows the keyboard language (#292), so the
 * keyboard language is the only language there is to choose.
 *
 * @param setup the choices on screen.
 * @param model the model « Continuer » starts downloading, named on the model line.
 * @param onLanguage a language picked from « Changer ».
 * @param onLayout a layout picked from the segment.
 * @param onContinue « Continuer »: saves and moves on.
 */
@Composable
fun OnboardingLanguageScreen(
    setup: LanguageSetup,
    model: ModelInfo,
    onLanguage: (SupportedLanguage) -> Unit,
    onLayout: (KeyboardLayout) -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_language_title),
        subtitle = stringResource(R.string.onboarding_language_subtitle),
        bottom = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_language_cta),
                onClick = onContinue,
            )
        },
    ) {
        LanguageCard(language = setup.language, onLanguage = onLanguage)

        Text(
            text = stringResource(R.string.onboarding_language_layout).uppercase(),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp),
        )
        LayoutSegment(selected = setup.layout, onLayout = onLayout)

        // Above the preview, as on iOS, so it stays above the fold on a small screen.
        ModelLine(model = model, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))

        LayoutPreview(setup = setup)
    }
}

/** The keyboard language in a card: its code badge, its name, « Dictée et clavier » and « Changer ». */
@Composable
private fun LanguageCard(language: SupportedLanguage, onLanguage: (SupportedLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DictusColors.Accent.copy(alpha = 0.12f))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = language.profile.shortCode,
                color = DictusColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = language.profile.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.onboarding_language_role),
                color = LocalDictusColors.current.textSecondary,
                fontSize = 15.sp,
            )
        }
        Box {
            Text(
                text = stringResource(R.string.onboarding_language_change),
                color = DictusColors.Accent,
                fontSize = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SupportedLanguage.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.profile.displayName, color = MaterialTheme.colorScheme.onBackground) },
                        onClick = {
                            onLanguage(option)
                            expanded = false
                        },
                        leadingIcon = {
                            if (option == language) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DictusColors.Accent)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.semantics { selected = option == language },
                    )
                }
            }
        }
    }
}

/** AZERTY / QWERTY / QWERTZ, one tap each, as iOS's segmented picker. */
@Composable
private fun LayoutSegment(selected: KeyboardLayout, onLayout: (KeyboardLayout) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        KeyboardLayout.entries.forEach { layout ->
            val isSelected = layout == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(DictusColors.Accent)
                                .border(1.dp, LocalDictusColors.current.borderSubtle, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        },
                    )
                    .selectable(selected = isSelected, role = Role.Tab) { onLayout(layout) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = layout.name,
                    color = if (isSelected) DictusColors.OnBackground else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The model « Continuer » downloads, in small type. Informs; asks nothing.
 *
 * WHY « votre langue » and not the language's name: French needs an article that changes
 * with the language (« le français », « l'anglais »), which a format string cannot choose.
 */
@Composable
private fun ModelLine(model: ModelInfo, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Outlined.Memory,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                R.string.onboarding_language_model,
                model.displayName,
                model.expectedSizeBytes / 1_000_000,
            ),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/** Test tag of the keyboard preview, for the tests that check where it ends. */
internal const val LANGUAGE_PREVIEW_TAG = "onboarding_language_preview"

/**
 * The letter keyboard in the chosen layout, drawn by the keyboard's own [KeyboardView], so
 * the preview is exactly what the user will type on. Touches stop here: it is a picture.
 *
 * WHY SCALED DOWN (#198): at its own size (48 dp keys, ~212 dp in all) the page outgrew a
 * Pixel 4 and the last row ran under « Continuer ». [PREVIEW_SCALE] brings the keys near
 * iOS's preview (40 pt, `LanguageSetupPage.swift`); the keyboard is laid out at the width
 * divided by the scale, so once scaled it still fills the width.
 */
@Composable
private fun LayoutPreview(setup: LanguageSetup) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.background)
            .clearAndSetSemantics { testTag = LANGUAGE_PREVIEW_TAG }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // The initial pass reaches this box before the keys, which then see consumed touches.
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .padding(vertical = 4.dp)
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val keyboard = measurable.measure(Constraints.fixedWidth((width / PREVIEW_SCALE).roundToInt()))
                layout(width, (keyboard.height * PREVIEW_SCALE).roundToInt()) {
                    keyboard.placeWithLayer(0, 0) {
                        scaleX = PREVIEW_SCALE
                        scaleY = PREVIEW_SCALE
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) {
        KeyboardView(
            layer = KeyboardLayer.LETTERS,
            isShifted = false,
            layout = setup.layout.persistedValue,
            languageProfile = setup.language.profile,
            onKeyPress = {},
            onAccentSelected = {},
        )
    }
}

/** The preview's size against the keyboard's own: 48 dp keys drawn 38 dp tall. */
private const val PREVIEW_SCALE = 0.8f
