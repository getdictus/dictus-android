package dev.pivisolutions.dictus.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.model.LanguageCoverage
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelLanguages
import java.text.Collator
import java.util.Locale

/**
 * Supported-languages detail for one model (issue #133, iOS parity with dictus-ios#240).
 *
 * WHY a bottom sheet instead of an inline list on the card: cards are already dense
 * and Whisper lists ~99 languages. A sheet leaves room for a scrollable list, the
 * Material equivalent of the iOS sheet opened from the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelLanguagesSheet(model: ModelInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ModelLanguagesContent(model)
    }
}

/**
 * Sheet body: summary, the tested languages (FR, EN, ES, DE) first, then the rest.
 *
 * WHY one Text per language: TalkBack moves through the list one language at a time
 * instead of reading a 99-name paragraph in one go. Section titles are headings so
 * TalkBack users can jump between them.
 */
@Composable
fun ModelLanguagesContent(model: ModelInfo) {
    val locale = LocalConfiguration.current.locales[0]
    val languages = model.languages
    val others = remember(languages, locale) { sortedByName(languages.others, locale) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp, bottom = 32.dp,
        ),
    ) {
        item {
            Text(
                text = model.displayName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = DictusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = languageSummary(languages),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                )
            }
            if (languages.coverage == LanguageCoverage.MULTILINGUAL) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.model_languages_quality_note),
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.model_languages_main)) }
        items(languages.highlights) { code -> LanguageRow(languageName(code, locale)) }
        if (others.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.model_languages_others)) }
            items(others) { name -> LanguageRow(name) }
        }
    }
}

/** One-line coverage summary, shared by the card and the sheet. */
@Composable
fun languageSummary(languages: ModelLanguages): String = when (languages.coverage) {
    LanguageCoverage.ENGLISH_ONLY -> stringResource(R.string.model_languages_english_only)
    LanguageCoverage.EUROPEAN -> pluralStringResource(R.plurals.model_languages_european, languages.codes.size, languages.codes.size)
    LanguageCoverage.MULTILINGUAL -> pluralStringResource(R.plurals.model_languages_multilingual, languages.codes.size, languages.codes.size)
}

/**
 * Language name in the UI language ("fr" -> "Français" / "French"), capitalized since
 * some locales return lowercase names. Falls back to the code if the OS cannot name it.
 */
internal fun languageName(code: String, displayLocale: Locale): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
    if (name.isBlank() || name == code) return code
    return name.replaceFirstChar { it.titlecase(displayLocale) }
}

/** Localized names sorted alphabetically in the UI language, for scanning. */
internal fun sortedByName(codes: List<String>, displayLocale: Locale): List<String> {
    val collator = Collator.getInstance(displayLocale)
    return codes.map { languageName(it, displayLocale) }.sortedWith(collator)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun LanguageRow(name: String) {
    Text(
        text = name,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}
