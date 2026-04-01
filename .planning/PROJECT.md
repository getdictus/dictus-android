# Dictus Android

## What This Is

Dictus Android is a free, open-source custom keyboard with built-in voice dictation that runs 100% on-device. No server, no account, no subscription. L'utilisateur installe le clavier Dictus, appuie sur le micro, parle en français ou anglais, et le texte transcrit est inséré directement dans n'importe quel champ de texte. Includes AZERTY/QWERTY layouts, model management with multi-provider STT (Whisper + Parakeet), production text suggestions with personal dictionary, emoji picker, waveform animation, bilingual FR/EN UI, CI/CD with signed releases, and OSS-ready repository.

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
- ✓ Suggestions texte production FR+EN avec dictionnaire personnel — v1.1
- ✓ Architecture multi-provider STT via SttProvider interface — v1.1
- ✓ Parakeet/Nvidia STT via sherpa-onnx comme 2e moteur — v1.1
- ✓ Sélection du fournisseur STT dans les paramètres — v1.1
- ✓ Badges et notes descriptives par fournisseur dans l'écran modèles — v1.1
- ✓ Avertissement limitations linguistiques du modèle sélectionné — v1.1
- ✓ CI GitHub Actions (lint + tests + build debug) sur chaque PR/push — v1.1
- ✓ Release workflow : APK signée automatiquement sur GitHub Releases — v1.1
- ✓ README avec instructions d'installation bêta — v1.1
- ✓ Écran licences avec toutes les dépendances OSS — v1.1
- ✓ Audit automatique des licences via licensee — v1.1
- ✓ CONTRIBUTING.md avec instructions de build et soumission de PR — v1.1
- ✓ Issue templates YAML (bug report, feature request) et PR template — v1.1
- ✓ README complet (description, installation, contribution, licence) — v1.1
- ✓ Tech debt v1.0 corrigé (AZERTY toggle, POST_NOTIFICATIONS, tests stales) — v1.1

### Active

(None yet — define for next milestone)

### Out of Scope

- Parakeet 0.6B TDT v3 (640 MB) — OOM dans le process IME sur la majorité des appareils
- Smart modes (LLM) — pas encore implémenté sur iOS non plus
- GPU/NNAPI acceleration — whisper.cpp NNAPI est expérimental, on reste en CPU multi-thread
- Swipe typing — coût de développement énorme, pas dans le scope voix
- ML-powered autocorrect — pas lié à la valeur voix
- Google Play Store — pas de compte dev, frais 25$, compliance review — reporté à v2
- In-app APK update — REQUEST_INSTALL_PACKAGES est flaggé sécurité — un lien GitHub suffit
- Firebase Crashlytics — introduit une dépendance cloud Google — contraire à la valeur "sans cloud"

## Context

Shipped v1.1 Public Beta with ~18K LOC Kotlin across 5 modules (app, ime, core, whisper, asr).
Tech stack: Kotlin + Jetpack Compose, whisper.cpp (NDK/CMake/JNI), sherpa-onnx (vendored Kotlin API + native libs), Material Design 3, Hilt DI, DataStore.
Port de l'app iOS Dictus — parité fonctionnelle dépassée (Parakeet non dispo côté iOS).
Target device: Pixel 4 (Snapdragon 855, 6 GB RAM).
Distribution: GitHub Releases (signed APK) + sideloading.

### Known Issues
- OSS-03: CODE_OF_CONDUCT.md absent (content filtering prevented generation — user-accepted deviation)
- Release variant Robolectric test failures (ImeStatusCardTest, RecordingTestAreaTest) — pre-existing, debug baseline green

## Constraints

- **Device cible**: Pixel 4 — Snapdragon 855, 6 GB RAM, API 29 (Android 10)
- **Min SDK**: API 29 (Android 10)
- **Langue**: Kotlin avec Jetpack Compose
- **STT Engines**: whisper.cpp via JNI + sherpa-onnx via vendored Kotlin API
- **License**: MIT (open source)
- **Distribution**: GitHub Releases (signed APK) + sideloading
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
| Hilt DI + EntryPointAccessors pour IME | @AndroidEntryPoint ne marche pas sur InputMethodService — EntryPointAccessors.fromApplication() | ✓ Good — pattern stable |
| AOSP dictionaries (50k words) for suggestions | Pre-decoded .combined format, Apache 2.0, no NDK needed — covers normal usage at 2MB/file | ✓ Good — fast prefix lookup, accent-insensitive |
| sherpa-onnx vendored as source (not AAR) | v1.12.34 not on Maven Central, Kotlin API source in asr/ module | ✓ Good — full control, works with Parakeet CTC models |
| Parakeet 110M CTC INT8 (126 MB) only | 640 MB model OOMs in IME process — only small model feasible | ✓ Good — works within 6GB RAM budget |
| cashapp/licensee for license audit | Gradle plugin, auto-generates artifacts.json consumed by LicencesScreen | ✓ Good — zero manual license maintenance |
| GitHub Actions for CI/CD | Free for public repos, direct GitHub Releases integration | ✓ Good — PR checks + signed APK release on tag |

---
*Last updated: 2026-04-01 after v1.1 milestone*
