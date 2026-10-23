# Transcription models

The Modèles tab lists the offline speech models (Whisper and Parakeet), downloads them, picks the active one and deletes those no longer needed; models too heavy for the phone are shown greyed with the reason.

- **ID:** `models.transcription`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.models`, `app.models.loading`, `kbd.model-loading` — see [screens.md](screens.md)

## Sub-features

- `models.catalogue`: Sections « Téléchargés » and « Disponibles »; each card shows name, engine badge (`WK` Whisper, `NV` Parakeet), a one-line description, « Précision » and « Vitesse » bars, size; Whisper Tiny/Base/Small/Small Q5/Medium, Parakeet 110M/0.6B. — présent
- `models.recommended`: — partiel: the recommendation (by RAM) appears only in onboarding (« Recommandé pour votre appareil »); no badge in Modèles.
- `models.download`: « Télécharger » on an available card, with « Téléchargement — N% », « Extraction — N% » (Parakeet) and « Vérification des fichiers… ». The install runs in a WorkManager foreground worker with an « Installation de <modèle> » notification (#169): it survives leaving the tab, the screen off and the app in the background. A model counts as downloaded only when every file has its exact size; the SHA-256 is checked at the end of the install. — présent
- `models.select`: Tap a downloaded card to make it « Actif ». — présent
- `models.delete`: Trash icon (`Supprimer`) then « Supprimer <nom> ? ». — présent
- `models.retry`: « Échec du téléchargement. » + « Réessayer » (network error, HTTP error, no byte for 30 s, or a file failing its size/SHA-256 check). Retry resumes from the bytes already downloaded (`Range`, log line `range offset=<N> status=206`, #131); a file failing its size/SHA-256 check is not kept. — partiel: no explicit way to delete a partial download.
- `models.languages`: — partiel: no per-model language sheet; only Parakeet 110M carries « Anglais uniquement » and activating it asks « Modèle anglais uniquement » (Parakeet 0.6B's description reads « 25 langues - Detection automatique »).
- `models.preparation`: Loading the model shows « Chargement du modèle… » in Modèles and the « Préparation de la reconnaissance vocale » overlay over the keyboard or the recorder. — présent (different surface from iOS)
- `models.device-gating`: Models above the RAM threshold are greyed with « Nécessite N Go de RAM ou plus » (#121); « RAM insuffisante » dialog. — présent
- `models.english-only-warning` *(Spécifique Android)*: Parakeet 110M is English-only: badge on the card and a confirmation dialog before activation.
- `models.storage` *(Spécifique Android)*: « Stockage utilisé : X Mo / Y Mo » under the lists, plus a one-line explanation of Whisper and Parakeet.

## How to get to it (user POV)

1. Open Dictus, tab Modèles.
2. Tap « Télécharger » on a model, wait, then tap its card to make it active.

## Driving it with Argent

Preconditions: Launch and Doctor OK. Note the active model Doctor printed; it must be the active one at the end.

- **Open.** Tap `Modèles`. → `Téléchargés`, `Disponibles`, the active card shows `Actif` (`screen.sh app.models`).
- **Scroll.** `mcp__argent__gesture-swipe` from (0.5, 0.8) to (0.5, 0.1) twice. → `Parakeet 110M` (`Anglais uniquement`), `Parakeet 0.6B` with `Télécharger` (or in `Téléchargés` once downloaded), `Stockage utilisé : 190 Mo / 2196 Mo`, the Whisper / Parakeet lines. **Trust the screenshot here**: after this scroll, `describe` kept returning the pre-scroll tree.
- **Delete dialog (no deletion).** Tap `Supprimer` on a downloaded non-active card, capture, tap `Annuler`. → card still in `Téléchargés`.
- **Proof.** The tree before/after and the unchanged `Actif` card. A download is proved by `Téléchargement — N%` then the card moving to `Téléchargés` and the file in `adb shell run-as dev.pivisolutions.dictus ls files/models`.

## Gotchas

- **Never download or delete a model** on Pierre's phone unless the task needs it (75 MB to over 600 MB, real traffic; deleting loses his setup).
- The active card has no trash icon: delete needs another downloaded model.
- Card descriptions are unaccented (« Rapide et leger », « Precis et equilibre », « Haute precision », « Detection automatique »).
- Selecting a model starts loading it at once (log `Active model changed to <key>, prewarming`, #175). A mic tap while it still loads, or while the engine still holds the previous model, shows the loading overlay at the tap and records once the selected model is ready. The overlay never covers a recording or a transcription (iOS #458).
- RAM gating rounds `ActivityManager.totalMem` up to the marketed size (#121); Parakeet 0.6B needs 6 GB, like iOS (#148): the 6 GB Pixel 4 (5.34 GiB reported) can download it; a phone below 6 GB sees it greyed with « Nécessite 6 Go de RAM ou plus ». Before #148 the gate was 8 GB and the Pixel showed « Nécessite 8 Go de RAM ou plus » (2026-10-20).
- Parakeet does not run on the x86_64 emulator (sherpa-onnx ships ARM libraries only): the engine fails cleanly, the app does not crash. Verify Parakeet on the Pixel only.
