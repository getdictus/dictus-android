package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.emoji.EmojiCategory
import dev.pivisolutions.dictus.ime.emoji.EmojiSearchIndex
import dev.pivisolutions.dictus.ime.emoji.RecentEmojiStore
import dev.pivisolutions.dictus.ime.emoji.RenderableEmoji
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private const val RECENTS_ID = "recents"
private const val DEFAULT_CATEGORY_ID = "smileys"
private const val GRID_COLUMNS = 8
private const val SEARCH_DEBOUNCE_MS = 100L
private val ROW_HEIGHT = 42.dp
private val CATEGORY_BAR_HEIGHT = 36.dp
private val SEARCH_ROW_HEIGHT = 38.dp
private val MINI_KEY_HEIGHT = 34.dp

/**
 * Emoji picker that replaces the keyboard area, ported from iOS `EmojiPickerView` and
 * `EmojiCategoryBar` (#192).
 *
 * Normal mode: the selected category's name, an 8-column grid of that category only, and the
 * category bar at the bottom (ABC, search, one emoji per category, delete). Search mode: a search
 * field, one row of results (the recents while the field is empty), and a mini letter keyboard
 * that types into the field, since the IME cannot open a keyboard for its own field.
 *
 * @param onEmojiSelected Inserts the emoji in the editor.
 * @param onReturnToKeyboard Closes the picker (ABC).
 * @param onDeleteBackward Deletes one character in the editor.
 * @param keyboardLayout "azerty" or "qwerty", the letters of the mini search keyboard.
 * @param languageCode Keyboard language: French searches the French keywords first.
 * @param onKeySound Key click of the mini search keyboard and the delete button.
 */
@Composable
fun EmojiPickerScreen(
    onEmojiSelected: (String) -> Unit,
    onReturnToKeyboard: () -> Unit,
    onDeleteBackward: () -> Unit,
    keyboardLayout: String,
    languageCode: String,
    onKeySound: (KeyType) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val recentStore = remember { RecentEmojiStore.create(context) }
    var recents by remember { mutableStateOf(recentStore.load().filter(RenderableEmoji::isRenderable)) }
    // Opens on the recents when there are any, like iOS.
    var selectedCategoryId by remember { mutableStateOf(if (recents.isEmpty()) DEFAULT_CATEGORY_ID else RECENTS_ID) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    // Filtering the catalog checks ~1500 glyphs: done off the main thread, once per process.
    val categories by produceState<List<EmojiCategory>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { RenderableEmoji.categories }
    }

    val insert: (String) -> Unit = { emoji ->
        HapticHelper.performKeyHaptic(view)
        onEmojiSelected(emoji)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(346.dp)
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cellWidth = (maxWidth - 4.dp) / GRID_COLUMNS
            Column(modifier = Modifier.fillMaxSize()) {
                if (isSearchActive) {
                    SearchMode(
                        searchText = searchText,
                        onSearchTextChange = { searchText = it },
                        onClose = { isSearchActive = false },
                        recents = recents,
                        searchIndex = RenderableEmoji.searchIndex,
                        languageCode = languageCode,
                        keyboardLayout = keyboardLayout,
                        cellWidth = cellWidth,
                        onEmoji = { emoji ->
                            insert(emoji)
                            // iOS records the emoji without refreshing the row under the finger.
                            recentStore.add(emoji)
                        },
                        onKeySound = onKeySound,
                    )
                } else {
                    NormalMode(
                        categories = categories.orEmpty(),
                        recents = recents,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { selectedCategoryId = it },
                        cellWidth = cellWidth,
                        onEmoji = { emoji ->
                            insert(emoji)
                            recents = recentStore.add(emoji)
                        },
                        onSearch = {
                            HapticHelper.performKeyHaptic(view)
                            searchText = ""
                            isSearchActive = true
                        },
                        onDelete = {
                            HapticHelper.performKeyHaptic(view)
                            onKeySound(KeyType.DELETE)
                            onDeleteBackward()
                        },
                        onDismiss = onReturnToKeyboard,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NormalMode(
    categories: List<EmojiCategory>,
    recents: List<String>,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    cellWidth: Dp,
    onEmoji: (String) -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedEmojis = if (selectedCategoryId == RECENTS_ID) {
        recents
    } else {
        categories.firstOrNull { it.id == selectedCategoryId }?.emojis.orEmpty()
    }
    val selectedNameRes = if (selectedCategoryId == RECENTS_ID) {
        R.string.ime_emoji_category_recents
    } else {
        categories.firstOrNull { it.id == selectedCategoryId }?.nameRes
    }

    // Category name, e.g. "SMILEYS".
    Text(
        text = selectedNameRes?.let { stringResource(it).uppercase(Locale.getDefault()) }.orEmpty(),
        color = LocalDictusColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 1.dp),
    )

    // One category at a time; key() starts each category at its top, as iOS rebuilds its grid.
    key(selectedCategoryId) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(selectedEmojis, key = { index, emoji -> "$index$emoji" }) { _, emoji ->
                EmojiCell(emoji = emoji, width = cellWidth, height = ROW_HEIGHT, fontSize = 32, onClick = { onEmoji(emoji) })
            }
        }
    }

    EmojiCategoryBar(
        categories = categories,
        hasRecents = recents.isNotEmpty(),
        selectedCategoryId = selectedCategoryId,
        onSelectCategory = onSelectCategory,
        onSearch = onSearch,
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
}

/** Bottom bar, as iOS `EmojiCategoryBar`: ABC | search | category emoji (scrolling) | delete. */
@Composable
private fun EmojiCategoryBar(
    categories: List<EmojiCategory>,
    hasRecents: Boolean,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().height(CATEGORY_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ime_emoji_return_keyboard),
            color = labelColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        val searchDescription = stringResource(R.string.ime_emoji_search_open)
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clickable(onClick = onSearch)
                .semantics { contentDescription = searchDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(22.dp),
            )
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasRecents) {
                item(key = RECENTS_ID) {
                    CategoryButton(
                        isSelected = selectedCategoryId == RECENTS_ID,
                        description = stringResource(R.string.ime_emoji_category_recents),
                        onClick = { onSelectCategory(RECENTS_ID) },
                    ) { isSelected ->
                        Icon(
                            painter = painterResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            tint = if (isSelected) labelColor else LocalDictusColors.current.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            items(categories, key = { it.id }) { category ->
                CategoryButton(
                    isSelected = selectedCategoryId == category.id,
                    description = stringResource(category.nameRes),
                    onClick = { onSelectCategory(category.id) },
                ) {
                    Text(text = category.representativeEmoji, fontSize = fixedSp(20))
                }
            }
        }

        val deleteDescription = stringResource(R.string.ime_emoji_delete)
        Text(
            text = "\u232B",
            color = labelColor,
            fontSize = 18.sp,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .semantics { contentDescription = deleteDescription }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CategoryButton(
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (isSelected) LocalDictusColors.current.keyBackground else Color.Transparent)
            .alpha(if (isSelected) 1f else 0.5f)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content(isSelected)
    }
}

@Composable
private fun ColumnScope.SearchMode(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit,
    recents: List<String>,
    searchIndex: EmojiSearchIndex,
    languageCode: String,
    keyboardLayout: String,
    cellWidth: Dp,
    onEmoji: (String) -> Unit,
    onKeySound: (KeyType) -> Unit,
) {
    // Debounced like iOS: the search runs 100 ms after the last keystroke, off the main thread.
    val results by produceState(initialValue = emptyList<String>(), searchText, languageCode) {
        if (searchText.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        delay(SEARCH_DEBOUNCE_MS)
        value = withContext(Dispatchers.Default) { searchIndex.search(searchText, languageCode) }
    }
    val row = when {
        searchText.isNotEmpty() -> results
        recents.isNotEmpty() -> recents
        else -> RenderableEmoji.allEmojis.take(EmojiSearchIndex.MAX_RESULTS)
    }

    SearchField(
        searchText = searchText,
        onClear = { if (searchText.isEmpty()) onClose() else onSearchTextChange("") },
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 1.dp),
    )

    if (row.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(SEARCH_ROW_HEIGHT), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.ime_emoji_no_results),
                color = LocalDictusColors.current.textSecondary,
                fontSize = 14.sp,
            )
        }
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(SEARCH_ROW_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(row) { _, emoji ->
                EmojiCell(emoji = emoji, width = cellWidth, height = SEARCH_ROW_HEIGHT, fontSize = 30, onClick = { onEmoji(emoji) })
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    MiniSearchKeyboard(
        keyboardLayout = keyboardLayout,
        onCharacter = { onSearchTextChange(searchText + it) },
        onDelete = { if (searchText.isNotEmpty()) onSearchTextChange(searchText.dropLast(1)) },
        onSpace = { onSearchTextChange("$searchText ") },
        onKeySound = onKeySound,
    )
}

/** Search field with a blinking cursor; × clears the text, or closes the search when empty. */
@Composable
private fun SearchField(
    searchText: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondary = LocalDictusColors.current.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LocalDictusColors.current.keyBackground)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.padding(end = 6.dp).size(18.dp),
        )
        if (searchText.isEmpty()) {
            BlinkingCursor()
            Text(
                text = stringResource(R.string.ime_emoji_search_placeholder),
                color = secondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = searchText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                maxLines = 1,
            )
            BlinkingCursor()
        }
        Spacer(modifier = Modifier.weight(1f))
        val clearDescription = stringResource(R.string.ime_emoji_search_clear)
        Icon(
            painter = painterResource(R.drawable.ic_cancel),
            contentDescription = clearDescription,
            tint = secondary,
            modifier = Modifier.size(20.dp).clickable(onClick = onClear),
        )
    }
}

@Composable
private fun BlinkingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(DictusColors.Accent),
    )
}

/**
 * Letter keys of the active layout, delete and space, as iOS `MiniSearchKeyboard`. The keys are
 * the keyboard's own [KeyButton]s (preview popup, haptic, click), only shorter.
 */
@Composable
private fun MiniSearchKeyboard(
    keyboardLayout: String,
    onCharacter: (String) -> Unit,
    onDelete: () -> Unit,
    onSpace: () -> Unit,
    onKeySound: (KeyType) -> Unit,
) {
    val letterRows = remember(keyboardLayout) {
        KeyboardLayouts.lettersForLayout(keyboardLayout)
            .take(3)
            .map { row -> row.filter { it.type == KeyType.CHARACTER } }
    }
    val deleteKey = remember { KeyDefinition("\u232B", type = KeyType.DELETE) }
    val spaceLabel = stringResource(R.string.ime_emoji_space)
    val spaceKey = remember(spaceLabel) { KeyDefinition(spaceLabel, output = " ", type = KeyType.SPACE) }
    val keyModifier = Modifier.height(MINI_KEY_HEIGHT)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        letterRows.forEachIndexed { index, row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { letter ->
                    KeyButton(
                        key = letter,
                        isShifted = false,
                        onPress = { onCharacter(letter.output) },
                        onSound = onKeySound,
                        modifier = keyModifier.weight(1f),
                    )
                }
                if (index == letterRows.lastIndex) {
                    KeyButton(
                        key = deleteKey,
                        isShifted = false,
                        onPress = onDelete,
                        onSound = onKeySound,
                        modifier = keyModifier.weight(1f),
                    )
                }
            }
        }
        KeyButton(
            key = spaceKey,
            isShifted = false,
            onPress = onSpace,
            onSound = onKeySound,
            modifier = keyModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    width: Dp,
    height: Dp,
    fontSize: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = fixedSp(fontSize), maxLines = 1)
    }
}

/** A size that ignores the system font scale, like iOS `.system(size:)` in the picker. */
@Composable
private fun fixedSp(size: Int): TextUnit = with(LocalDensity.current) { size.dp.toSp() }
