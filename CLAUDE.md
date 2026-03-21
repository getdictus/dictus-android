# CLAUDE.md — Dictus Android

## Projet

Dictus Android — Application native Android de dictation vocale offline.
Port de l'application iOS existante (voir repo dictus).
Voir PRD.md pour les specs completes.

## Stack

- Kotlin + Jetpack Compose
- WhisperKit / Whisper.cpp pour STT offline
- Material Design 3 / Material You
- Minimum SDK: 26 (Android 8.0)

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
