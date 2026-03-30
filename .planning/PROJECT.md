# Dictus Android

## What This Is

Dictus Android is a free, open-source custom keyboard with built-in voice dictation that runs 100% on-device. No server, no account, no subscription. L'utilisateur installe le clavier Dictus, appuie sur le micro, parle en français ou anglais, et le texte transcrit est inséré directement dans n'importe quel champ de texte. Includes AZERTY/QWERTY layouts, model management, emoji picker, waveform animation, and bilingual FR/EN UI.

## Core Value

La dictée vocale on-device qui fonctionne comme clavier système — gratuite, privée, sans cloud.

## Requirements

### Validated

- ✓ Custom keyboard IME (AZERTY/QWERTY) avec shift, caps, chiffres, symboles, caractères accentués — v1.0
- ✓ Moteur STT on-device via whisper.cpp (JNI/NDK) avec modèles GGML — v1.0
- ✓ Model manager : téléchargement depuis HuggingFace, suppression, indicateur de stockage — v1.0
- ✓ Enregistrement audio via AudioRecord API avec foreground Service — v1.0
- ✓ Insertion de texte via InputConnection.commitText() — v1.0
- ✓ IPC entre IME et DictationService via Bound Service — v1.0
- ✓ Machine d'états de dictée : idle → requested → recording → transcribing → ready → idle — v1.0
- ✓ Onboarding : bienvenue, permission micro, choix layout, activation IME, téléchargement modèle, test — v1.0
- ✓ Écran de paramètres : modèle actif, langue, layout, haptic, son, thème, export logs — v1.0
- ✓ Waveform animation temps réel pendant l'enregistrement — v1.0
- ✓ Feedback sonore (start/stop recording) configurable — v1.0
- ✓ Feedback haptique configurable — v1.0
- ✓ Barre de suggestions texte — v1.0
- ✓ Emoji picker avec catégories — v1.0
- ✓ Debug logging structuré (Timber + file appender) — v1.0
- ✓ UI bilingue FR/EN (français par défaut) — v1.0
- ✓ Thème Material You : dark Dictus par défaut + option "Follow System" — v1.0

### Active

<!-- Current scope. Building toward these for v1.1 Public Beta. -->

- [ ] Suggestions texte production (AOSP LatinIME ou équivalent natif C++)
- [ ] Parakeet/Nvidia STT — 2e fournisseur de modèles avec architecture multi-provider
- [ ] Audit et correction des licences OSS dans l'app
- [ ] Préparation bêta publique — distribution APK/GitHub Releases, collecte feedback
- [ ] Repo GitHub OSS — CONTRIBUTING.md, issue templates, PR template, CODE_OF_CONDUCT, README
- [ ] Tech debt v1.0 — vérifier et corriger settings toggle, POST_NOTIFICATIONS, tests stales

### Out of Scope

- Parakeet engine — sera ajouté après le MVP, une fois Whisper fonctionnel
- Smart modes (LLM) — pas encore implémenté sur iOS non plus
- Auto-return to source app — moins pertinent sur Android (le clavier reste in-process)
- Live Activity / Dynamic Island — concept iOS uniquement
- GPU/NNAPI acceleration — whisper.cpp NNAPI est expérimental, on reste en CPU multi-thread
- Swipe typing — coût de développement énorme, pas dans le scope voix
- ML-powered autocorrect — pas lié à la valeur voix

## Context

Shipped v1.0 with ~15K LOC Kotlin across 4 modules (app, ime, core, whisper).
Tech stack: Kotlin + Jetpack Compose, whisper.cpp (NDK/CMake/JNI), Material Design 3, Hilt DI, DataStore.
Port de l'app iOS Dictus — parité fonctionnelle atteinte.
Target device: Pixel 4 (Snapdragon 855, 6 GB RAM).

### Known Issues
- Settings AZERTY/QWERTY toggle doesn't propagate to IME (DataStore key written, never read by DictusImeService)
- POST_NOTIFICATIONS permission not declared in AndroidManifest (affects API 33+ notification display)
- 4 stale test assertions (AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest)

## Constraints

- **Device cible**: Pixel 4 — Snapdragon 855, 6 GB RAM, API 29 (Android 10)
- **Min SDK**: API 29 (Android 10)
- **Langue**: Kotlin avec Jetpack Compose
- **STT Engine**: whisper.cpp via JNI (GGML models)
- **License**: MIT (open source)
- **Distribution**: Play Store (gratuit) + sideloading APK
- **Performance**: Modèle small quantized (q5_1) doit tourner en <8s pour 10s d'audio sur Pixel 4
- **Pas de cloud**: Aucune requête réseau sauf téléchargement des modèles

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| whisper.cpp plutôt que Sherpa-ONNX | Parité maximale avec iOS (mêmes modèles Whisper), communauté active, MIT license | ✓ Good — fonctionne bien, mêmes modèles GGML |
| Whisper seul pour le MVP (pas Parakeet) | Réduire la complexité du MVP, ajouter Parakeet une fois Whisper stable | ✓ Good — focus payant |
| Bound Service pour IPC (IME ↔ DictationService) | Type-safe, lifecycle-aware, meilleur que SharedPreferences + Broadcast | ✓ Good — fiable en production |
| Foreground Service pour audio | Pattern Android standard pour long-running work, notification visible | ✓ Good — START_NOT_STICKY approprié |
| Jetpack Compose pour l'IME | Parité avec SwiftUI côté iOS, mais à surveiller pour la performance dans InputMethodService | ⚠️ Revisit — fonctionne mais lifecycle complexe |
| Design en parallèle du dev | Mockups Pencil.dev depuis le projet iOS, fournis plus tard — on avance sur architecture/moteur | ✓ Good — n'a pas bloqué le dev |
| Repo séparé dictus-android | Même structure que iOS (dictus), repos distincts par plateforme | ✓ Good |
| Hilt DI + EntryPointAccessors pour IME | @AndroidEntryPoint ne marche pas sur InputMethodService — EntryPointAccessors.fromApplication() | ✓ Good — pattern stable |
| NDK 27.2 (pas 25.2) | NDK 25.2 install corrompue, 27.2 stable avec source.properties | ✓ Good |
| q5_1 au lieu de q5_0 | HuggingFace fournit pre-built q5_1, pas q5_0 | ✓ Good |
| when() dispatch pour onboarding | One-time flow, pas besoin de NavHost/AnimatedContent | ✓ Good — simple et efficace |

## Current Milestone: v1.1 Public Beta

**Goal:** Peaufiner l'app (suggestions texte, Parakeet STT), préparer le repo et la distribution pour une bêta publique.

**Target features:**
- Suggestions texte production (remplacer le stub par un moteur natif C++)
- Parakeet/Nvidia comme 2e fournisseur STT avec architecture multi-provider extensible
- Audit et mise à jour des licences OSS
- Distribution bêta via APK/GitHub Releases + collecte feedback
- Repo GitHub prêt pour les contributions open source
- Correction du tech debt v1.0

---
*Last updated: 2026-03-30 after v1.1 milestone started*
