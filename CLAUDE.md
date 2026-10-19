# CLAUDE.md — Dictus Android

## Projet

Dictus Android — Application native Android de dictation vocale offline.
Port de l'application iOS existante (voir repo dictus).
Voir PRD.md pour les specs completes.

## Stack

- Kotlin + Jetpack Compose
- WhisperKit / Whisper.cpp pour STT offline
- Material Design 3 / Material You
- Minimum SDK: 29 (Android 10)

## Design Reference

Les maquettes sont dans `design/dictus-android-design.pen`.
Utiliser les outils MCP Pencil (batch_get, get_screenshot) pour consulter les ecrans.
Voir `design/README.md` pour la liste des frames et IDs.

### Frames principales
- Home: `wrqi0` | Models: `kSWvw` | Settings: `CqKvx`
- Recording: `0yEaN` | Waveform Spec: `VUDbq`

## Architecture cible

- **app/** — Application principale (onboarding, settings, model manager, recording)
- **core/** — Module partage (modeles, preferences, design system)

## Conventions

- Nommage: camelCase pour variables/fonctions, PascalCase pour classes/composables
- Un fichier = une responsabilite
- Commentaires en anglais dans le code
- UI strings: francais (langue principale) + anglais

## Couleurs principales

- Background: #0A1628
- Surface: #161C2C
- Accent: #3D7EFF
- Accent highlight: #6BA3FF
- Recording: #EF4444
- Success: #22C55E

## Contexte utilisateur

- Pierre est debutant en Kotlin/Android — expliquer les concepts cles
- Toujours expliquer le "pourquoi" derriere les choix d'architecture Android
- L'app iOS (repo dictus) sert de reference fonctionnelle

## Agent skills

### Issue tracker

Issues live in GitHub Issues on `getdictus/dictus-android`, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Physical test label

`needs-physical-test` marks PRs touching audio/mic/transcription: test on a physical device with Pierre before merge. Auto-applied from `.github/labeler.yml`; only Titus removes it, after Pierre validates. See `docs/agents/physical-test-label.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
