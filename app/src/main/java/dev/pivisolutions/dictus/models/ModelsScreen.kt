package dev.pivisolutions.dictus.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.ui.models.ModelCard
import dev.pivisolutions.dictus.ui.models.ModelPreparationContext
import dev.pivisolutions.dictus.ui.models.ModelPreparationScreen
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.SttEngineState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.view.WindowManager
import androidx.core.view.WindowCompat
import dev.pivisolutions.dictus.ui.models.ModelLanguagesSheet
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
    dictationController: DictationController?,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsState()
    val activeModelKey by viewModel.activeModelKey.collectAsState()
    val storageUsedBytes by viewModel.storageUsedBytes.collectAsState()
    val parakeetDialogKey by viewModel.showParakeetLanguageDialog.collectAsState()

    val downloadedModels = models.filter { it.isDownloaded }
    val availableModels = models.filter { !it.isDownloaded }

    // Deletion confirmation bottom sheet state
    var modelToDelete by remember { mutableStateOf<ModelUiState?>(null) }
    // Model whose supported-languages sheet is open (issue #133)
    var languagesModel by remember { mutableStateOf<ModelInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Full-screen model preparation: download, preparation, engine load (#181)
    val preparation by viewModel.preparation.collectAsState()
    preparation?.let { target ->
        val engineState = dictationController?.engineState?.collectAsState()?.value
        ModelPreparationDialog(
            target = target,
            engine = engineState,
            onRetryInstall = { viewModel.retryDownload(target.info.key) },
            onLoadEngine = { dictationController?.prewarmEngine() },
            onClose = { viewModel.closePreparation() },
        )
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

    Scaffold(
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
                    key(modelState.info.key) {
                    ModelCard(
                        model = modelState.info,
                        isDownloaded = true,
                        isActive = modelState.info.key == activeModelKey,
                        canDelete = downloadedModels.size > 1 && modelState.info.key != activeModelKey,
                        installState = modelState.installState,
                        onDownload = { viewModel.downloadModel(modelState.info.key) },
                        onDelete = {
                            modelToDelete = modelState
                            scope.launch { sheetState.show() }
                        },
                        onRetry = { viewModel.retryDownload(modelState.info.key) },
                        onSelect = { viewModel.requestSetActiveModel(modelState.info.key) },
                        requiredRamGb = modelState.requiredRamGb,
                        isRecommended = modelState.isRecommended,
                        onShowLanguages = { languagesModel = modelState.info },
                    )
                    }
                }
            }
        }

        // Disponibles section
        if (availableModels.isNotEmpty()) {
            ModelSection(title = stringResource(R.string.models_section_available)) {
                availableModels.forEach { modelState ->
                    key(modelState.info.key) {
                    ModelCard(
                        model = modelState.info,
                        isDownloaded = false,
                        isActive = false,
                        canDelete = false,
                        installState = modelState.installState,
                        onDownload = { viewModel.downloadModel(modelState.info.key) },
                        onDelete = {},
                        onRetry = { viewModel.retryDownload(modelState.info.key) },
                        requiredRamGb = modelState.requiredRamGb,
                        isRecommended = modelState.isRecommended,
                        onShowLanguages = { languagesModel = modelState.info },
                    )
                    }
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

        Spacer(modifier = Modifier.height(16.dp))

        // Provider descriptions
        ProviderFooterItem(
            icon = Icons.Default.GraphicEq,
            text = stringResource(R.string.models_provider_whisper),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProviderFooterItem(
            icon = Icons.Default.Bolt,
            text = stringResource(R.string.models_provider_parakeet),
        )
    }

    languagesModel?.let { model ->
        ModelLanguagesSheet(model = model, onDismiss = { languagesModel = null })
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

/**
 * The preparation screen over the whole app, bottom bar included, as iOS's full-screen cover.
 *
 * WHY a Dialog: the screen is drawn from the Models tab, inside the tab Scaffold, and the
 * bottom bar belongs to the Scaffold above it. A full-screen Dialog window covers both without
 * moving the preparation state up to the navigation host. Back does nothing while the work
 * runs (iOS refuses the dismissal too); after a failure it closes, like « Fermer ».
 */
@Composable
private fun ModelPreparationDialog(
    target: ModelPreparationTarget,
    engine: SttEngineState?,
    onRetryInstall: () -> Unit,
    onLoadEngine: () -> Unit,
    onClose: () -> Unit,
) {
    val failed = ModelPreparationPhase.of(target, engine) == ModelPreparationPhase.Failed
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = failed,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // A Dialog window stops at the status bar and dims what is behind it, which left a
        // darker band above the screen. Full window, no dim, drawn under the system bars.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
        ModelPreparationScreen(
            target = target,
            engine = engine,
            context = ModelPreparationContext.ModelSelection,
            onRetryInstall = onRetryInstall,
            onLoadEngine = onLoadEngine,
            onFinished = onClose,
            onClose = onClose,
        )
    }
}

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

@Composable
private fun ProviderFooterItem(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
