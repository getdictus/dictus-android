package dev.pivisolutions.dictus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.core.theme.DictusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen debug log viewer.
 *
 * Reads log lines from [TimberSetup.getLogFile] and displays them in a scrollable
 * [LazyColumn]. Each line is rendered in monospace font for easy reading of timestamps.
 *
 * Top bar has a back arrow, title "Debug Logs", and a 3-dot overflow menu with two actions:
 * - "Effacer" — clears the log file and the displayed list
 * - "Copier"  — copies all log lines to the system clipboard
 *
 * WHY LaunchedEffect(Unit): The log file is read once when the screen enters composition.
 * IO is dispatched to Dispatchers.IO to avoid blocking the main thread.
 * A full live-tail feature is out of scope for this phase.
 */
@Composable
fun DebugLogsScreen(onBack: () -> Unit) {
    val logFile = TimberSetup.getLogFile()
    val lines = remember { mutableStateListOf<String>() }
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        val fileLines = withContext(Dispatchers.IO) {
            logFile?.readLines() ?: emptyList()
        }
        lines.addAll(fileLines)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DictusColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
        ) {
            if (lines.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aucun log disponible",
                            color = DictusColors.TextSecondary,
                            fontSize = 16.sp,
                        )
                    }
                }
            } else {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DictusColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DictusColors.Background)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = DictusColors.TextPrimary,
                )
            }
            Text(
                text = "Debug Logs",
                color = DictusColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = DictusColors.TextPrimary,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Effacer") },
                        onClick = {
                            showMenu = false
                            logFile?.writeText("")
                            lines.clear()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copier") },
                        onClick = {
                            showMenu = false
                            clipboardManager.setText(AnnotatedString(lines.joinToString("\n")))
                        },
                    )
                }
            }
        }
    }
}
