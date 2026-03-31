package dev.pivisolutions.dictus.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.ui.models.ModelCard
import kotlinx.coroutines.launch

/**
 * Models tab screen — lists downloaded and available models with download/delete actions.
 *
 * WHY sections split by download state: The "Téléchargés" / "Disponibles" split matches
 * the iOS Dictus model screen and makes it immediately clear which models are ready to use
 * vs. which ones need downloading.
 *
 * WHY ModalBottomSheet for delete confirmation: Android Material 3 recommends bottom sheets
 * for destructive confirmation actions. An AlertDialog would be more intrusive for an action
 * the user may trigger accidentally.
 *
 * WHY hiltViewModel() here: ModelsScreen is a navigation destination composable, so using
 * hiltViewModel() ties the ViewModel lifecycle to the NavBackStackEntry, not the Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsState()
    val activeModelKey by viewModel.activeModelKey.collectAsState()
    val storageUsedBytes by viewModel.storageUsedBytes.collectAsState()
    val parakeetDialogKey by viewModel.showParakeetLanguageDialog.collectAsState()
    val ramWarningKey by viewModel.showRamWarningDialog.collectAsState()

    val downloadedModels = models.filter { it.isDownloaded }
    val availableModels = models.filter { !it.isDownloaded }

    // Deletion confirmation bottom sheet state
    var modelToDelete by remember { mutableStateOf<ModelUiState?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Snackbar for engine swap feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Parakeet language mismatch confirmation dialog
    if (parakeetDialogKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissParakeetDialog() },
            title = { Text(stringResource(R.string.parakeet_lang_dialog_title)) },
            text = { Text(stringResource(R.string.parakeet_lang_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmParakeetActivation() }) {
                    Text(stringResource(R.string.dialog_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissParakeetDialog() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    // RAM insufficient warning dialog
    if (ramWarningKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRamWarningDialog() },
            title = { Text(stringResource(R.string.ram_warning_dialog_title)) },
            text = { Text(stringResource(R.string.ram_warning_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRamWarningDialog() }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Screen title
        Text(
            text = stringResource(R.string.models_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        // Téléchargés section
        if (downloadedModels.isNotEmpty()) {
            ModelSection(title = stringResource(R.string.models_section_downloaded)) {
                downloadedModels.forEach { modelState ->
                    ModelCard(
                        model = modelState.info,
                        isDownloaded = true,
                        isActive = modelState.info.key == activeModelKey,
                        downloadProgress = modelState.downloadPercent,
                        canDelete = downloadedModels.size > 1 && modelState.info.key != activeModelKey,
                        hasDownloadError = modelState.hasError,
                        onDownload = { viewModel.downloadModel(modelState.info.key) },
                        onDelete = {
                            modelToDelete = modelState
                            scope.launch { sheetState.show() }
                        },
                        onRetry = { viewModel.retryDownload(modelState.info.key) },
                        onSelect = { viewModel.requestSetActiveModel(modelState.info.key) },
                    )
                }
            }
        }

        // Disponibles section
        if (availableModels.isNotEmpty()) {
            ModelSection(title = stringResource(R.string.models_section_available)) {
                availableModels.forEach { modelState ->
                    ModelCard(
                        model = modelState.info,
                        isDownloaded = false,
                        isActive = false,
                        downloadProgress = modelState.downloadPercent,
                        canDelete = false,
                        hasDownloadError = modelState.hasError,
                        onDownload = { viewModel.downloadModel(modelState.info.key) },
                        onDelete = {},
                        onRetry = { viewModel.retryDownload(modelState.info.key) },
                    )
                }
            }
        }

        // Storage footer
        val storageUsedMb = storageUsedBytes / 1_000_000
        val totalCatalogMb = dev.pivisolutions.dictus.model.ModelCatalog.ALL
            .sumOf { it.expectedSizeBytes } / 1_000_000
        Text(
            text = stringResource(R.string.models_storage_used, storageUsedMb, totalCatalogMb),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    // Delete confirmation bottom sheet
    val pendingDelete = modelToDelete
    if (pendingDelete != null) {
        ModalBottomSheet(
            onDismissRequest = {
                modelToDelete = null
                scope.launch { sheetState.hide() }
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DeleteConfirmationSheet(
                modelName = pendingDelete.info.displayName,
                onConfirm = {
                    viewModel.deleteModel(pendingDelete.info.key)
                    modelToDelete = null
                    scope.launch { sheetState.hide() }
                },
                onCancel = {
                    modelToDelete = null
                    scope.launch { sheetState.hide() }
                },
            )
        }
    }

    } // end Scaffold content lambda
} // end ModelsScreen

@Composable
private fun ModelSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        content()
    }
}

@Composable
private fun DeleteConfirmationSheet(
    modelName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.models_delete_confirm_title, modelName),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.models_delete_confirm_body),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.models_delete_confirm_button),
                color = DictusColors.Destructive,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider(color = LocalDictusColors.current.borderSubtle)
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.models_delete_cancel_button),
                color = LocalDictusColors.current.textSecondary,
                fontSize = 16.sp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
