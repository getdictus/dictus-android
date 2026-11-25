package dev.pivisolutions.dictus.vocabulary

import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import java.util.UUID

/**
 * What the add / edit sheet decides from its two fields, iOS `VocabularyEditorView` (#208).
 *
 * WHY apart from the composable: these are the rules a unit test can hold the sheet to, and
 * the Save button and the store must never disagree. The candidate is built with
 * [VocabularyEntry.create] itself rather than validated by a copy of its rules.
 *
 * iOS's merge with a stored term (a sheet opened from History, #723) is not here: Android has no
 * History entry into this sheet yet.
 *
 * @param original the entry being edited, null for a new term.
 * @param entries what the store holds now, for the duplicate and limit checks.
 */
internal class VocabularyEditorRules(
    private val original: VocabularyEntry?,
    private val entries: List<VocabularyEntry>,
    private val term: String,
    private val variantsLine: String,
    private val newId: UUID,
    private val now: Long,
) {

    /** What Save would store, or null when the term is empty or too long. */
    val candidate: VocabularyEntry? = VocabularyEntry.create(
        term = term,
        variants = VocabularyEntry.variantsFromLine(variantsLine),
        isEnabled = original?.isEnabled ?: true,
        id = original?.id ?: newId,
        dateAdded = original?.dateAdded ?: now,
    )

    val duplicatesAnotherTerm: Boolean =
        term.isNotBlank() && VocabularyStore.containsTerm(entries, term, excluding = original?.id)

    /** A new term the store has no room for. The list's add button is disabled at the cap too. */
    val exceedsLimit: Boolean = original == null && entries.size >= VocabularyStore.MAX_ENTRIES

    /** A term with no variant yet: saved, to watch, and the sheet says it changes nothing (#819). */
    val variantsMissing: Boolean = candidate?.hasEffect == false

    val canSave: Boolean = candidate != null && !exceedsLimit && !duplicatesAnotherTerm
}
