---
phase: 04-model-management-onboarding
plan: 07
subsystem: model-management
tags: [model-download, model-cards, ui, settings]
dependency_graph:
  requires: []
  provides: [model-download-persistence, model-card-redesign, model-tap-select]
  affects: [ModelsScreen, SettingsScreen, ModelManager]
tech_stack:
  added: []
  patterns: [minimum-size-threshold, tap-to-select, progress-bars]
key_files:
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
decisions:
  - "Use >= 95% threshold instead of exact size equality in getModelPath() to tolerate HuggingFace CDN mirror size variations"
  - "setActiveModel() added to ModelsViewModel (not SettingsViewModel) since model selection now lives on the Models screen"
  - "Metric bars implemented as custom Box with fractional fillMaxWidth rather than LinearProgressIndicator — better visual control for static decorative values"
metrics:
  duration: "~3 min"
  completed_date: "2026-03-27"
  tasks: 3
  files_modified: 5
requirements: [APP-02, APP-04]
---

# Phase 04 Plan 07: Model Download Fix + Card Redesign Summary

**One-liner:** Fixed download persistence with corrected byte sizes + 95% threshold, redesigned model cards with description and Precision/Vitesse bars matching iOS, and moved model selection from Settings to the Models screen.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Fix model download persistence + add model metadata | 1e468be | ModelManager.kt |
| 2 | Redesign ModelCard with description and Precision/Vitesse bars | 4dc662f | ModelCard.kt |
| 3 | Wire model selection on Models screen + remove Settings model picker | dbfd6c5 | ModelsScreen.kt, ModelsViewModel.kt, SettingsScreen.kt |

## What Was Built

### Task 1 — ModelManager.kt

- Corrected `expectedSizeBytes` for three models: base → 147,951,465 bytes, small → 487,601,967 bytes, small-q5_1 → 190,085,487 bytes (tiny was already correct at 77,691,713).
- Replaced `file.length() == info.expectedSizeBytes` with `file.length() >= info.expectedSizeBytes * 95 / 100` in `getModelPath()`. This tolerates minor CDN mirror size variations while still rejecting clearly partial downloads.
- Added `description: String`, `precision: Float`, and `speed: Float` fields to `ModelInfo` data class (default empty/0f for backward compatibility).
- Populated catalog entries with human-readable descriptions and normalized precision/speed values (0.0–1.0).

### Task 2 — ModelCard.kt

- Removed the 48dp mic icon box from the card header.
- Added description text (TextSecondary, 14sp) below the model name, shown only when non-blank.
- Replaced the size+quality icons row with two labeled progress bars: "Precision" (Accent color) and "Vitesse" (AccentHighlight color), each 6dp tall with RoundedCornerShape(3dp).
- Bars use a custom `ModelMetricBar` composable with a `Box` + `fillMaxWidth(fraction)` for precise visual control.
- Size label moved below bars as compact secondary text (12sp).
- Added `onSelect: () -> Unit = {}` callback parameter — invoked when a downloaded, non-active card is tapped.
- Active model card now shows a 2dp Accent border (instead of the default 1dp GlassBorder).
- Press animation (scale + alpha spring) preserved for all states.

### Task 3 — ModelsScreen.kt + ModelsViewModel.kt + SettingsScreen.kt

- `ModelsViewModel.setActiveModel(key)` added — writes to DataStore via `dataStore.edit { prefs[PreferenceKeys.ACTIVE_MODEL] = key }`.
- `ModelsScreen` passes `onSelect = { viewModel.setActiveModel(modelState.info.key) }` on each downloaded model card.
- `SettingsScreen`: removed `activeModel` and `downloadedModels` state collection, `showModelPicker` variable, the Active Model `SettingPickerRow` + its `SettingDivider`, and the `PickerBottomSheet` block. TRANSCRIPTION section now shows only the Langue picker.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

All 5 modified files exist on disk. All 3 task commits (1e468be, 4dc662f, dbfd6c5) confirmed in git log. Build successful.
