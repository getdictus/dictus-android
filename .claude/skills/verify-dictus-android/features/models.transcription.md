# Transcription models

The Modèles tab lists the offline speech models (Whisper and Parakeet), downloads them, picks the active one and deletes those no longer needed; models too heavy for the phone are shown greyed with the reason.

- **ID:** `models.transcription`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.models`, `app.models.loading` — see [screens.md](screens.md)

## Sub-features

- `models.catalogue`: Sections « Téléchargés » and « Disponibles »; each card shows name, engine badge (`WK` Whisper, `NV` Parakeet), a one-line description in the app's language (string resources since #236: « Rapide et léger » / « Fast and light »), « Précision » and « Vitesse » bars, size; Whisper Tiny/Base/Small/Small Q5/Medium, Parakeet 110M/0.6B. — présent
- `models.recommended`: the model onboarding recommends (by RAM: Parakeet 0.6B v3 from 6 GB, Small Q5 below) carries a « Recommandé » / « Recommended » pill next to its engine badge in Modèles (#188), the same rule as onboarding. — présent (iOS also weighs the dictation language, #649; every Android dictation language is a Parakeet language, so the answer is the same)
- `models.download`: « Télécharger » on an available card raises the full-screen preparation screen (`app.models.loading`, #181): ring around the Dictus mark, « On prépare votre modèle », a card « Téléchargement  N % » then « Préparation pour ce téléphone » (extraction and verification) then « Chargement du modèle », « X Mo sur Y Mo », « Restez sur cette page, ne quittez pas l'application. ». The downloaded model becomes the active one and loads (like iOS), except an English-only model while the language is not English; then « Votre modèle est prêt », green ring, and the screen closes after 1.5 s. The install runs in a WorkManager foreground worker with an « Installation de <modèle> » notification (#169): it survives leaving the tab, the screen off and the app in the background. A model counts as downloaded only when every file has its exact size; the SHA-256 is checked at the end of the install. — présent
- `models.select`: Tap a downloaded card to make it « Actif ». — présent
- `models.delete`: Trash icon (`Supprimer`) then « Supprimer <nom> ? ». — présent
- `models.retry`: the preparation screen shows « Le modèle vocal n'a pas pu être préparé », the failed step in red and « Réessayer » / « Fermer »; after « Fermer » the card keeps « Échec du téléchargement. » + « Réessayer » (network error, HTTP error, no byte for 30 s, or a file failing its size/SHA-256 check). Retry resumes from the bytes already downloaded (`Range`, log line `range offset=<N> status=206`, #131); a file failing its size/SHA-256 check is not kept. — partiel: no explicit way to delete a partial download.
- `models.languages`: — partiel: no per-model language sheet; only Parakeet 110M carries « Anglais uniquement » and activating it asks « Modèle anglais uniquement » (Parakeet 0.6B's description reads « 25 langues - Detection automatique »).
- `models.preparation`: tapping a downloaded card raises the same preparation screen on « Chargement du modèle » (row 1 already « Téléchargé · N Mo »), until the dictation engine holds the model (#181). The recorder keeps the « Préparation de la reconnaissance vocale » overlay. The keyboard shows nothing: its mic records at once and the transcription waits for the load (#247). — présent (recorder: different surface from iOS; keyboard: records during a real load, where iOS opens its preparation screen)
- `models.device-gating`: Models above the RAM threshold are greyed with « Nécessite N Go de RAM ou plus » (#121); « RAM insuffisante » dialog. — présent
- `models.english-only-warning` *(Spécifique Android)*: Parakeet 110M is English-only: badge on the card and a confirmation dialog before activation.
- `models.storage` *(Spécifique Android)*: « Stockage utilisé : X Mo / Y Mo » under the lists, plus a one-line explanation of Whisper and Parakeet.

## How to get to it (user POV)

1. Open Dictus, tab Modèles.
2. Tap « Télécharger » on a model, wait, then tap its card to make it active.

## Driving it with Argent

Preconditions: Launch and Doctor OK. Note the active model Doctor printed; it must be the active one at the end.

- **Open.** Tap `Modèles`. → `Téléchargés`, `Disponibles`, the active card shows `Actif`, the recommended card shows `Recommandé` (`screen.sh app.models`).
- **Scroll.** `mcp__argent__gesture-swipe` from (0.5, 0.8) to (0.5, 0.1) twice. → `Parakeet 110M` (`Anglais uniquement`), `Parakeet 0.6B` with `Télécharger` (or in `Téléchargés` once downloaded), `Stockage utilisé : 190 Mo / 2196 Mo`, the Whisper / Parakeet lines. **Trust the screenshot here**: after this scroll, `describe` kept returning the pre-scroll tree.
- **Delete dialog (no deletion).** Tap `Supprimer` on a downloaded non-active card, capture, tap `Annuler`. → card still in `Téléchargés`.
- **Proof.** The tree before/after and the unchanged `Actif` card. A download is proved by the preparation screen (`Téléchargement`, `N %`, `X Mo sur Y Mo`, then `Votre modèle est prêt`) then the card moving to `Téléchargés` as `Actif` and the file in `adb shell run-as dev.pivisolutions.dictus ls files/models`.

## Gotchas

- **Never download or delete a model** on Pierre's phone unless the task needs it (75 MB to over 600 MB, real traffic; deleting loses his setup).
- The active card has no trash icon: delete needs another downloaded model.
- Card descriptions are unaccented (« Rapide et leger », « Precis et equilibre », « Haute precision », « Detection automatique »).
- Selecting a model starts loading it at once (log `Active model changed to <key>, prewarming`, #175). A keyboard mic tap while it still loads, or while the engine still holds the previous model, records at once and the transcription waits for the selected model (#247). In the app's recorder the same tap shows the loading overlay and records once the model is ready; that overlay never covers a recording or a transcription (iOS #458).
- RAM gating rounds `ActivityManager.totalMem` up to the marketed size (#121); Parakeet 0.6B needs 6 GB, like iOS (#148): the 6 GB Pixel 4 (5.34 GiB reported) can download it; a phone below 6 GB sees it greyed with « Nécessite 6 Go de RAM ou plus ». Before #148 the gate was 8 GB and the Pixel showed « Nécessite 8 Go de RAM ou plus » (2026-10-20).
- Parakeet does not run on the x86_64 emulator (sherpa-onnx ships ARM libraries only): the engine fails cleanly, the app does not crash. Verify Parakeet on the Pixel only.
