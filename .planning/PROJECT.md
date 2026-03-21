# Dictus Android

## What This Is

Dictus Android is the Android port of Dictus iOS — a free, open-source custom keyboard with built-in voice dictation that runs 100% on-device. No server, no account, no subscription. L'utilisateur installe le clavier Dictus, appuie sur le micro, parle en français ou anglais, et le texte transcrit est inséré directement dans n'importe quel champ de texte. L'app cible en priorité le Pixel 4 (Snapdragon 855, 6 GB RAM, API 29+).

## Core Value

La dictée vocale on-device qui fonctionne comme clavier système — gratuite, privée, sans cloud.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward these. -->

- [ ] Custom keyboard IME (AZERTY/QWERTY) avec shift, caps, chiffres, symboles, caractères accentués
- [ ] Moteur STT on-device via whisper.cpp (JNI/NDK) avec modèles GGML
- [ ] Model manager : téléchargement depuis HuggingFace, suppression, indicateur de stockage
- [ ] Enregistrement audio via AudioRecord API avec foreground Service
- [ ] Insertion de texte via InputConnection.commitText()
- [ ] IPC entre IME et DictationService via Bound Service
- [ ] Machine d'états de dictée : idle → requested → recording → transcribing → ready → idle
- [ ] Onboarding : bienvenue, permission micro, choix layout, activation IME, téléchargement modèle, test
- [ ] Écran de paramètres : modèle actif, langue, layout, haptic, son, thème, export logs
- [ ] Waveform animation temps réel pendant l'enregistrement
- [ ] Feedback sonore (start/stop recording) configurable
- [ ] Feedback haptique configurable
- [ ] Barre de suggestions texte
- [ ] Emoji picker avec catégories
- [ ] Debug logging structuré (Timber + file appender)
- [ ] UI bilingue FR/EN (français par défaut)
- [ ] Thème Material You : dark Dictus par défaut + option "Follow System"

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- Parakeet engine — sera ajouté après le MVP, une fois Whisper fonctionnel
- Smart modes (LLM) — pas encore implémenté sur iOS non plus
- Auto-return to source app — moins pertinent sur Android (le clavier reste in-process)
- Live Activity / Dynamic Island — concept iOS uniquement
- Support multi-device — MVP cible Pixel 4, extension device testing en v1.1
- GPU/NNAPI acceleration — whisper.cpp NNAPI est expérimental, on reste en CPU multi-thread

## Context

- Port de l'app iOS Dictus (repo: github.com/Pivii/dictus, dossier local: ~/dev/dictus)
- iOS est en v1.2 Beta Ready (TestFlight), l'objectif est la parité fonctionnelle
- L'architecture iOS split en 3 targets : DictusApp, DictusKeyboard, DictusCore → Android : app, ime, core modules
- L'enregistrement audio tourne dans le main app process (foreground Service), pas dans l'IME, pour éviter les ANR
- Les mockups UI seront créés en parallèle via Pencil.dev depuis le projet iOS — le design sera fourni plus tard
- On avance sur l'architecture et le moteur STT en attendant les designs

## Constraints

- **Device cible**: Pixel 4 — Snapdragon 855, 6 GB RAM, API 29 (Android 10)
- **Min SDK**: API 29 (Android 10)
- **Langue**: Kotlin avec Jetpack Compose
- **STT Engine**: whisper.cpp via JNI (GGML models)
- **License**: MIT (open source)
- **Distribution**: Play Store (gratuit) + sideloading APK
- **Performance**: Modèle small quantized (q5_0) doit tourner en <8s pour 10s d'audio sur Pixel 4
- **Pas de cloud**: Aucune requête réseau sauf téléchargement des modèles

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| whisper.cpp plutôt que Sherpa-ONNX | Parité maximale avec iOS (mêmes modèles Whisper), communauté active, MIT license | — Pending |
| Whisper seul pour le MVP (pas Parakeet) | Réduire la complexité du MVP, ajouter Parakeet une fois Whisper stable | — Pending |
| Bound Service pour IPC (IME ↔ DictationService) | Type-safe, lifecycle-aware, meilleur que SharedPreferences + Broadcast | — Pending |
| Foreground Service pour audio | Pattern Android standard pour long-running work, notification visible | — Pending |
| Jetpack Compose pour l'IME | Parité avec SwiftUI côté iOS, mais à surveiller pour la performance dans InputMethodService | — Pending |
| Design en parallèle du dev | Mockups Pencil.dev depuis le projet iOS, fournis plus tard — on avance sur architecture/moteur | — Pending |
| Repo séparé dictus-android | Même structure que iOS (dictus), repos distincts par plateforme | — Pending |

---
*Last updated: 2026-03-21 after initialization*
