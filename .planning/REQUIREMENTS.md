# Requirements: Dictus Android

**Defined:** 2026-03-30
**Core Value:** La dictée vocale on-device qui fonctionne comme clavier système — gratuite, privée, sans cloud.

## v1.1 Requirements

Requirements for v1.1 Public Beta. Each maps to roadmap phases.

### Suggestions texte

- [ ] **SUGG-01**: L'utilisateur voit des suggestions contextuelles basées sur un dictionnaire binaire FR+EN pendant la saisie
- [ ] **SUGG-02**: L'utilisateur peut taper une suggestion pour remplacer le mot en cours et insérer un espace
- [ ] **SUGG-03**: Le clavier apprend des mots fréquemment tapés par l'utilisateur (dictionnaire personnel)

### STT Multi-Provider

- [x] **STT-01**: L'architecture supporte N fournisseurs STT via une interface SttProvider extensible
- [ ] **STT-02**: L'utilisateur peut télécharger et utiliser un modèle Parakeet/Nvidia via sherpa-onnx
- [ ] **STT-03**: L'utilisateur peut sélectionner le fournisseur STT actif dans les paramètres
- [ ] **STT-04**: L'écran modèles affiche des notes descriptives par fournisseur (Whisper, Parakeet)
- [ ] **STT-05**: L'utilisateur est averti des limitations linguistiques du modèle sélectionné

### Distribution bêta

- [ ] **BETA-01**: Un workflow CI GitHub Actions exécute lint + tests + build debug sur chaque PR/push main
- [ ] **BETA-02**: Un workflow release build et signe l'APK automatiquement sur tag push vers GitHub Releases
- [ ] **BETA-03**: Le README fournit les instructions d'installation de l'APK bêta

### Repo OSS

- [ ] **OSS-01**: Le repo contient CONTRIBUTING.md avec instructions de build et soumission de PR
- [ ] **OSS-02**: Le repo contient des issue templates YAML (bug report, feature request) et un PR template
- [ ] **OSS-03**: Le repo contient CODE_OF_CONDUCT.md (Contributor Covenant)
- [ ] **OSS-04**: Le README est complet (description, screenshots, installation, contribution, licence)

### Licences

- [ ] **LIC-01**: L'écran licences référence correctement toutes les dépendances OSS utilisées (whisper.cpp, FlorisBoard, sherpa-onnx, etc.)
- [ ] **LIC-02**: Le projet utilise gradle-license-plugin pour auditer automatiquement les licences

### Tech Debt

- [x] **DEBT-01**: Le toggle AZERTY/QWERTY dans Settings propage correctement le changement à l'IME
- [x] **DEBT-02**: La permission POST_NOTIFICATIONS est déclarée dans AndroidManifest pour API 33+
- [x] **DEBT-03**: Les tests stales sont corrigés ou supprimés (AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Suggestions texte avancées

- **SUGG-04**: Le clavier utilise un modèle bigram pour des suggestions contextuelles (pas uniquement préfixe)
- **SUGG-05**: Le clavier propose des corrections post-dictation basées sur la confiance du modèle STT

### Distribution

- **DIST-01**: Publication sur le Google Play Store (compte développeur, listing, compliance)
- **DIST-02**: Publication sur F-Droid (builds reproductibles)
- **DIST-03**: Mise à jour in-app avec vérification de version

### STT avancé

- **STT-06**: Support du modèle Parakeet 0.6B TDT v3 (multi-langue, ~640 MB) pour appareils haut de gamme
- **STT-07**: Benchmark comparatif des moteurs STT intégré à l'app

## Out of Scope

| Feature | Reason |
|---------|--------|
| AOSP LatinIME C++ engine | Pas de Maven artifact, build AOSP requis, overkill pour suggestions post-dictation |
| Parakeet 0.6B TDT v3 (640 MB) | OOM dans le process IME sur la majorité des appareils — reporté en v2 si validation mémoire |
| Firebase Crashlytics | Introduit une dépendance cloud Google — contraire à la valeur "sans cloud" |
| Fastlane | Overhead Ruby, optimisé Play Store — pas de bénéfice pour GitHub Releases |
| Swipe typing | Coût de dev énorme, pas lié à la valeur voix |
| Google Play Store | Pas de compte dev, frais 25$, compliance review — reporté à v2 |
| In-app APK update | REQUEST_INSTALL_PACKAGES est flaggé sécurité — un lien GitHub suffit |
| ktlint CI gate | Bloquerait les contributeurs occasionnels au lancement |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DEBT-01 | Phase 7 | Complete |
| DEBT-02 | Phase 7 | Complete |
| DEBT-03 | Phase 7 | Complete |
| STT-01 | Phase 7 | Complete |
| SUGG-01 | Phase 8 | Pending |
| SUGG-02 | Phase 8 | Pending |
| SUGG-03 | Phase 8 | Pending |
| STT-02 | Phase 9 | Pending |
| STT-03 | Phase 9 | Pending |
| STT-04 | Phase 9 | Pending |
| STT-05 | Phase 9 | Pending |
| BETA-01 | Phase 10 | Pending |
| BETA-02 | Phase 10 | Pending |
| BETA-03 | Phase 10 | Pending |
| LIC-01 | Phase 10 | Pending |
| LIC-02 | Phase 10 | Pending |
| OSS-01 | Phase 11 | Pending |
| OSS-02 | Phase 11 | Pending |
| OSS-03 | Phase 11 | Pending |
| OSS-04 | Phase 11 | Pending |

**Coverage:**
- v1.1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-30*
*Last updated: 2026-03-30 after roadmap creation*
