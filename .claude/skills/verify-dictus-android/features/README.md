# Dictus Android feature map

Every user-facing feature of Dictus Android, one file each, with **the same IDs as the iOS map** (`getdictus/dictus-ios`, `.claude/skills/verify-dictus-ios/features/`, develop at `685b4b2`). An ID names what the user gets, never a module, a composable or a class, so both platforms compare line by line. An ID never changes once published; a feature that goes away keeps its ID retired. A feature that exists only on Android gets a new ID in the same format, marked *Android-only*.

Each feature file has the same four sections: **Sub-features** (each with its stable ID and its Android status), **How to get to it (user POV)**, **Driving it with Argent** (the recipe, with the helpers of `../SKILL.md`), **Gotchas**. Screens are mapped in [screens.md](screens.md), captures in [`../screens/`](../screens/).

## Baseline preconditions

- Pierre's Pixel 4 (`99071FFAZ00EBN`, LineageOS, French), unlocked, on USB; the `.pixel-lock` taken by this session (`helpers/lock.sh acquire`: one token per session, see `../SKILL.md` → Launch).
- `launch.sh` printed `READY`, `doctor.sh` printed `DOCTOR OK`: debug build of the current branch installed with `adb install -r` (data kept), Dictus is the default IME, `RECORD_AUDIO` granted, onboarding completed, at least one model downloaded (Small Q5 at the time of writing).
- Pierre's data stays as it is: his notes (including the « bonjour » ones), his active model, his settings (transcription language `Automatique`). A journey types only into a scratch note it creates and deletes.
- What a journey cannot reach without wiping data (onboarding, a download in progress) is reported as not driven, with the precondition; the data is never cleared to reach it.

## Driving conventions

- Argent is the only driver (`mcp__argent__*`, device id `99071FFAZ00EBN`). Coordinates come from the tree Argent returns, never from guesses.
- Select by French label or content description (no resource-ids in this app); the handles are listed in `../SKILL.md` → Drive.
- Inside the keyboard, type by tapping the Dictus keys; `mcp__argent__keyboard` sends `adb input text` and bypasses the keyboard.
- Start every assertion from a log mark (`M=$(helpers/log.sh mark)`) and wait on the line (`helpers/log.sh wait '<regex>' 15 "$M"`).
- Leave every setting as found; a picker is captured, then closed with system back without choosing.

## Proof and skip reporting

- Capture the user action and the resulting state: a screenshot before, one after (`helpers/evidence.sh <name> "$M"`), the log line that proves the action, and the side effect (the field's value in the tree, the file on the device).
- Record the feature ID and the entry point with every artifact (name the file `<NN>-<feature-id>-<step>`).
- An unreachable path is reported with the command attempted and the unmet precondition. A skipped entry point is never reported as verified through a different path.
- Evidence lives in `/home/pierre/Dev/dictus-proofs/verify-dictus-android/<run-id>/`, never in the repository. Only screen-map captures go in `../screens/`.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behaviour, then a short metadata list (ID, tier, Android status, screens), then exactly four H2 in this order:

1. `Sub-features`: short IDs with one line each, ending with the Android status (present / partial: what is missing / absent / iOS-only); Android-only ones are marked *(Android-only)*.
2. `How to get to it (user POV)`: every user entry point.
3. `Driving it with Argent`: starts with `Preconditions:`, then labelled bullets pairing each user action with an exact call or helper and its observable result, ending with a **Proof.** bullet.
4. `Gotchas`: traps that waste or invalidate a run, including what the Pixel cannot show and what would touch Pierre's data.

Implementation details stay out of the map: only user paths, stable handles, required state, commands and observable proof.

## Features

**Tier on Android.** Nothing is sold on Android yet: there is no purchase flow and no Pro entitlement. The only switch is `PremiumFlags.HISTORY_VISIBLE = false` (`core/.../premium/PremiumFlags.kt`), which hides history entirely (no entry point, no route, nothing recorded). Everything else that exists is **Free**. When iOS sells a feature Android ships free, the table says "Free (Pro on iOS)" (no such case today); iOS Pro features absent on Android say "Pro on iOS — absent".

| ID | Feature | Tier | Android status | Drivable on the Pixel |
| --- | --- | --- | --- | --- |
| [`dictation.keyboard`](dictation.keyboard.md) | Dictation from the keyboard | Free | present | Yes — start + cancel proven on the Pixel (2026-10-20); ✓ records the room |
| [`dictation.in-app`](dictation.in-app.md) | Dictation inside the app | Free | present | Screens yes; opening the recorder records at once (and `Retour` does not stop it) |
| [`models.transcription`](models.transcription.md) | Transcription models | Free | present | Browsing yes; no download/delete on Pierre's phone |
| [`typing.autocorrect`](typing.autocorrect.md) | Autocorrect and suggestions | Free | present | Yes, keys by coordinate in a scratch note; the ⌫ undo step on a test device only |
| [`typing.learned-words`](typing.learned-words.md) | Learned words | Free | partial | No on Pierre's Pixel (learning is permanent, no reset): test device only |
| [`keyboard.languages`](keyboard.languages.md) | Keyboard languages and layouts | Free | partial | Yes (cycle a full turn) |
| [`keyboard.emoji`](keyboard.emoji.md) | Emoji | Free | partial | Yes |
| [`feedback.sounds-haptics`](feedback.sounds-haptics.md) | Sounds and haptics | Free | present | Settings only; no sound or vibration captured |
| [`onboarding`](onboarding.md) | Onboarding | Free | partial | No: needs a fresh install (data wipe forbidden) |
| [`diagnostics.logs`](diagnostics.logs.md) | Debug logs and diagnostics | Free | present | Yes (never « Effacer », never send) |
| [`appearance`](appearance.md) | Appearance: theme, interface language | Free | Android-only | Pickers only |

Not in the map as files, on purpose: the iOS features absent on Android (`smart-modes`, `history`, `vocabulary`, `voice-notes`, `pro.subscription`) and those that only make sense on iOS (`dictation.return-to-app`, `polish`, `live-activity`). They keep their row in the table below. `history` is the special case: implemented and unit-tested, but compiled out of every user path by `PremiumFlags.HISTORY_VISIBLE`. The debug-only `TestSurfaceScreen` is not reachable from any route and is not a feature.

## All IDs

Every iOS ID (the 176 of the iOS « All IDs » table, same order: 18 features, 109 sub-features, 49 screens), then the Android-only IDs. **Vocabulary.** *Free/Pro* is the tier: Free, Pro on iOS (absent on Android), or Pro hidden behind `PremiumFlags.HISTORY_VISIBLE`; N/A for iOS-only IDs. **Android status**: present, partial (with what is missing), absent, or iOS-only (N/A: the iOS keyboard-to-app hand-off and Full Access, return to the host app, Apple Intelligence, Dynamic Island, App Group). Statuses come from the code of `develop` at `f2b15c5`, checked on the Pixel for every screen marked "yes" in [screens.md](screens.md) (2026-10-20). **Android-only** is yes for an ID that exists only on Android, no otherwise.

| ID | Free/Pro | Android status | Android-only |
| --- | --- | --- | --- |
| [`dictation.keyboard`](dictation.keyboard.md) | Free | present | no |
| [`dictation.in-app`](dictation.in-app.md) | Free | present | no |
| `dictation.return-to-app` | N/A | iOS-only (N/A) | no |
| [`models.transcription`](models.transcription.md) | Free | present | no |
| `polish` | N/A | iOS-only (N/A) | no |
| `smart-modes` | Pro on iOS — absent | absent | no |
| `history` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — implemented (list, detail, delete) but hidden | no |
| `vocabulary` | Pro on iOS — absent | absent | no |
| `voice-notes` | Pro on iOS — absent | absent | no |
| [`typing.autocorrect`](typing.autocorrect.md) | Free | present | no |
| [`typing.learned-words`](typing.learned-words.md) | Free | partial — no reset | no |
| [`keyboard.languages`](keyboard.languages.md) | Free | partial — no panel, no QWERTZ, no number row | no |
| [`keyboard.emoji`](keyboard.emoji.md) | Free | partial — no search | no |
| [`feedback.sounds-haptics`](feedback.sounds-haptics.md) | Free | present | no |
| `live-activity` | N/A | iOS-only (N/A) | no |
| [`onboarding`](onboarding.md) | Free | partial — no language step, no Pro scenes, no What's new | no |
| `pro.subscription` | Pro on iOS — absent | absent | no |
| [`diagnostics.logs`](diagnostics.logs.md) | Free | present | no |
| `dictation.start` | Free | present | no |
| `dictation.stop` | Free | present | no |
| `dictation.cancel` | Free | present | no |
| `dictation.status` | Free | present | no |
| `dictation.undo-insert` | Free | present — « Annuler » chip (#137) | no |
| `dictation.handoff` | N/A | iOS-only (N/A) | no |
| `dictation.requires-full-access` | N/A | iOS-only (N/A) | no |
| `dictation.device-gating` | Free | absent — no device is refused outright; only the models' RAM filter exists | no |
| `dictation.start-in-app` | Free | present | no |
| `dictation.copy-last` | Free | present | no |
| `dictation.download-prompt` | Free | partial — no prompt on Home; only the mic answers « Aucun modèle vocal » | no |
| `dictation.from-widget` | Free | absent — no widget | no |
| `return.swipe-back` | N/A | iOS-only (N/A) | no |
| `return.auto-return` | N/A | iOS-only (N/A) | no |
| `models.catalogue` | Free | present | no |
| `models.recommended` | Free | partial — recommendation only in onboarding, no badge in Models | no |
| `models.download` | Free | present | no |
| `models.select` | Free | present | no |
| `models.delete` | Free | present | no |
| `models.retry` | Free | partial — « Réessayer » yes; no explicit deletion of a partial download | no |
| `models.languages` | Free | partial — no language sheet; only Parakeet 110M carries « Anglais uniquement » | no |
| `models.preparation` | Free | present — overlay in the keyboard and the recorder, snackbar in Models | no |
| `models.device-gating` | Free | present — RAM threshold, card greyed out with the reason (#121) | no |
| `polish.toggle` | N/A | iOS-only (N/A) | no |
| `polish.clean-up` | N/A | iOS-only (N/A) | no |
| `polish.in-keyboard` | N/A | iOS-only (N/A) | no |
| `polish.debug` | N/A | iOS-only (N/A) | no |
| `smart-modes.fan` | Pro on iOS — absent | absent | no |
| `smart-modes.structured` | Pro on iOS — absent | absent | no |
| `smart-modes.list` | Pro on iOS — absent | absent | no |
| `smart-modes.message` | Pro on iOS — absent | absent | no |
| `smart-modes.summary` | Pro on iOS — absent | absent | no |
| `smart-modes.translate` | Pro on iOS — absent | absent | no |
| `smart-modes.pin` | Pro on iOS — absent | absent | no |
| `smart-modes.toggle` | Pro on iOS — absent | absent | no |
| `history.list` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — code present, hidden | no |
| `history.search` | Pro on iOS — absent | absent — not in the code | no |
| `history.delete` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — code present, hidden | no |
| `history.detail` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — code present (copy, share), hidden | no |
| `history.clear` | Pro on iOS — absent | absent — not in the code | no |
| `history.toggle` | Pro on iOS — absent | absent — not in the code | no |
| `history.home-entry` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — swipe up on Home, hidden | no |
| `vocabulary.add` | Pro on iOS — absent | absent | no |
| `vocabulary.edit` | Pro on iOS — absent | absent | no |
| `vocabulary.delete` | Pro on iOS — absent | absent | no |
| `vocabulary.reset` | Pro on iOS — absent | absent | no |
| `vocabulary.suggestions` | Pro on iOS — absent | absent | no |
| `vocabulary.apply` | Pro on iOS — absent | absent | no |
| `vocabulary.toggle` | Pro on iOS — absent | absent | no |
| `voice-notes.share-in` | Pro on iOS — absent | absent | no |
| `voice-notes.transcribe` | Pro on iOS — absent | absent | no |
| `voice-notes.after-mode` | Pro on iOS — absent | absent | no |
| `voice-notes.language` | Pro on iOS — absent | absent | no |
| `voice-notes.reader` | Pro on iOS — absent | absent | no |
| `voice-notes.insert` | Pro on iOS — absent | absent | no |
| `voice-notes.delete` | Pro on iOS — absent | absent | no |
| `voice-notes.toggle` | Pro on iOS — absent | absent | no |
| `voice-notes.progress-activity` | N/A | iOS-only (N/A) | no |
| `typing.suggestions` | Free | present | no |
| `typing.autocorrect-on-space` | Free | present | no |
| `typing.undo-correction` | Free | partial — ⌫ right after the correction; no chip in the bar | no |
| `typing.auto-full-stop` | Free | absent | no |
| `typing.accents` | Free | present — long press, accent strip | no |
| `typing.dead-keys` | Free | absent | no |
| `typing.autocorrect-toggle` | Free | present | no |
| `learned-words.learn` | Free | present | no |
| `learned-words.reset` | Free | absent — no button; only clearing the app's data | no |
| `keyboard.language-switch` | Free | present — tap on the language code (FR→EN→ES→DE), no panel | no |
| `keyboard.layout` | Free | partial — AZERTY / QWERTY, no QWERTZ | no |
| `keyboard.number-row` | Free | absent | no |
| `keyboard.default-page` | Free | present | no |
| `keyboard.transcription-language` | Free | partial — `Automatique` or a fixed language; no « Suivre la langue du clavier » | no |
| `keyboard.open-app` | Free | present — long press on the language code → Settings | no |
| `emoji.picker` | Free | present | no |
| `emoji.recents` | Free | present — « UTILISÉS RÉCEMMENT » (EmojiPickerView), seen on the Pixel | no |
| `emoji.search` | Free | absent | no |
| `emoji.back-to-letters` | Free | present | no |
| `sounds.toggle` | Free | present | no |
| `sounds.volume` | Free | present | no |
| `sounds.per-event` | Free | present | no |
| `haptics.toggle` | Free | present | no |
| `live-activity.standby` | N/A | iOS-only (N/A) | no |
| `live-activity.states` | N/A | iOS-only (N/A) | no |
| `live-activity.stop-standby` | N/A | iOS-only (N/A) | no |
| `live-activity.controls` | N/A | iOS-only (N/A) | no |
| `live-activity.toggle` | N/A | iOS-only (N/A) | no |
| `onboarding.welcome` | Free | present | no |
| `onboarding.language` | Free | absent — no language/layout step | no |
| `onboarding.keyboard-setup` | N/A | iOS-only (N/A) — Android equivalent: onboarding.ime-setup | no |
| `onboarding.apple-intelligence` | N/A | iOS-only (N/A) | no |
| `onboarding.pro-scenes` | Free | absent | no |
| `onboarding.smart-mode-pick` | Free | absent | no |
| `onboarding.model-download` | Free | present | no |
| `onboarding.microphone` | Free | present | no |
| `onboarding.first-dictation` | Free | present | no |
| `onboarding.stop-standby` | N/A | iOS-only (N/A) | no |
| `onboarding.completion` | Free | present | no |
| `onboarding.resume` | Free | partial — survives process death, not a force stop | no |
| `onboarding.whats-new` | Free | absent | no |
| `pro.hub` | Pro on iOS — absent | absent | no |
| `pro.paywall` | Pro on iOS — absent | absent | no |
| `pro.restore` | Pro on iOS — absent | absent | no |
| `pro.trial` | Pro on iOS — absent | absent | no |
| `pro.trial-end` | Pro on iOS — absent | absent | no |
| `pro.demo` | Pro on iOS — absent | absent | no |
| `diagnostics.log-view` | Free | present | no |
| `diagnostics.log-export` | Free | present | no |
| `diagnostics.app-group-check` | N/A | iOS-only (N/A) | no |
| `diagnostics.about` | Free | present | no |
| `app.onboarding.welcome` | Free | present | no |
| `app.onboarding.language` | Free | absent | no |
| `app.onboarding.keyboard-setup` | N/A | iOS-only (N/A) — equivalent: app.onboarding.ime-setup | no |
| `app.onboarding.apple-intelligence` | N/A | iOS-only (N/A) | no |
| `app.onboarding.smart-modes-scene` | Free | absent | no |
| `app.onboarding.smart-mode-pick` | Free | absent | no |
| `app.onboarding.voice-notes-scene` | Free | absent | no |
| `app.onboarding.vocabulary-scene` | Free | absent | no |
| `app.onboarding.history-scene` | Free | absent | no |
| `app.onboarding.model-preparation` | Free | present | no |
| `app.onboarding.microphone` | Free | present | no |
| `app.onboarding.first-dictation` | Free | present | no |
| `app.onboarding.stop-standby` | N/A | iOS-only (N/A) | no |
| `app.onboarding.completion` | Free | present | no |
| `app.whats-new` | Free | absent | no |
| `app.home` | Free | present | no |
| `app.recording` | Free | present | no |
| `app.history` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — code present, route not registered | no |
| `app.history.detail` | Pro (hidden: `PremiumFlags.HISTORY_VISIBLE = false`) | absent — code present, hidden | no |
| `app.models` | Free | present | no |
| `app.models.languages` | Free | absent | no |
| `app.models.loading` | Free | partial — no full-screen overlay in Models; see kbd.model-loading | no |
| `app.settings` | Free | present | no |
| `app.settings.sounds` | Free | present | no |
| `app.settings.diagnostic` | N/A | iOS-only (N/A) | no |
| `app.settings.debug-logs` | Free | present | no |
| `app.settings.licences` | Free | present | no |
| `app.settings.polish-debug` | N/A | iOS-only (N/A) | no |
| `app.pro.hub` | Pro on iOS — absent | absent | no |
| `app.pro.smart-modes` | Pro on iOS — absent | absent | no |
| `app.pro.history` | Pro on iOS — absent | absent | no |
| `app.pro.vocabulary` | Pro on iOS — absent | absent | no |
| `app.pro.vocabulary.term-editor` | Pro on iOS — absent | absent | no |
| `app.pro.voice-notes` | Pro on iOS — absent | absent | no |
| `app.paywall` | Pro on iOS — absent | absent | no |
| `app.pro.trial-announcement` | Pro on iOS — absent | absent | no |
| `app.voice-note.result` | Pro on iOS — absent | absent | no |
| `kbd.keys` | Free | present | no |
| `kbd.no-full-access` | N/A | iOS-only (N/A) | no |
| `kbd.dictation-error` | Free | present — « Aucun modèle vocal » / « Impossible de démarrer la reconnaissance vocale » | no |
| `kbd.recording` | Free | present | no |
| `kbd.panel` | Free | absent | no |
| `kbd.emoji` | Free | present | no |
| `kbd.voice-note-reader` | Pro on iOS — absent | absent | no |
| `kbd.smart-mode-fan` | Pro on iOS — absent | absent | no |
| `kbd.suggestions` | Free | present | no |
| `sys.settings.full-access` | N/A | iOS-only (N/A) | no |
| `share.voice-note` | Pro on iOS — absent | absent | no |
| `widget.live-activity` | N/A | iOS-only (N/A) | no |
| [`appearance`](appearance.md) | Free | present — theme and interface language | yes |
| `appearance.theme` | Free | present | yes |
| `appearance.ui-language` | Free | present | yes |
| `dictation.notification` | Free | present — « Dictus - Recording » notification + Stop (not visible on the Pixel: notifications denied) | yes |
| `dictation.switch-keyboard` | Free | present — gear during a recording → keyboard picker | yes |
| `typing.trackpad` | Free | present — long press on space + drag | yes |
| `typing.adaptive-accent-key` | Free | present — AZERTY ' key → é à ù î ô | yes |
| `typing.suggestions-toggle` | Free | present | yes |
| `typing.private-fields` | Free | present — no suggestions or learning in sensitive fields | yes |
| `sounds.key-clicks` | Free | present — « Sons du clavier » | yes |
| `onboarding.ime-setup` | Free | present | yes |
| `onboarding.default-page` | Free | present — ABC / 123 | yes |
| `models.english-only-warning` | Free | present — Parakeet 110M | yes |
| `models.storage` | Free | present — « Stockage utilisé » | yes |
| `app.onboarding.ime-setup` | Free | present | yes |
| `app.onboarding.default-page` | Free | present | yes |
| `app.settings.sound-picker` | Free | present | yes |
| `app.settings.picker` | Free | present — Settings choice sheets | yes |
| `kbd.model-loading` | Free | present | yes |
| `sys.ime-picker` | Free | present | yes |
| `sys.settings.ime` | Free | present | yes |
| `sys.notification.recording` | Free | present | yes |

**Count** (198 IDs: 176 iOS + 22 Android-only): present **59**, partial **13**, absent **75**, iOS-only (N/A) **29**, Android-only **22**.

Of the 147 iOS IDs that apply to Android: 59 present, 13 partial, 75 absent. 55 of these absent IDs are iOS Pro features (Smart Modes, history, vocabulary, voice notes, subscription, with their screens); the other 20 are free on iOS.

## Design gaps with iOS

Screen by screen, what looks different between the Android captures of `../screens/` and the iOS design (captures in `docs/agents/screens/` of the iOS repo, iPhone 17 simulator in light theme, and `verify-dictus-ios/features/`). Each gap is worded to become an issue: **screen**, **iOS**, **Android**, **proposal**. Gaps that come from a missing feature (Smart Modes, Pro…) are in the table above, not here. The theme differs by design: iOS follows the system appearance, Android defaults to the Dark theme; the comparisons are about structure, not colours.

1. **Keyboard bar — `kbd.keys`, `kbd.suggestions`** (issue #139, waiting for a mockup validated by Pierre; not started here).
   - iOS: a single bar above the keys: ☰ on the left, suggestions in the middle, the mic (blue pill) on the right; « Annuler » also shows there.
   - Android: two stacked rows: the suggestion bar (3 slots, « Annuler » chip after a dictation) then the row with the language code `FR` on the left + mic on the right. The keyboard is taller and the mic is separate from the suggestions.
   - Proposal: merge into a single bar following the #139 mockup.
2. **Dictation in the keyboard — `kbd.recording`**
   - iOS: ✕ and ✓ in the keyboard bar, waveform above the keys.
   - Android: a panel that replaces the whole keyboard: ✕ / ✓ at the top, waveform + timer + « En écoute… » in the middle, and at the bottom left a gear stuck to the system keyboard's navigation bar (next to ⌄), whose accessibility label is in English (`Settings`) and which opens… the Android keyboard picker, not the settings.
   - Proposal: align the recording bar with #139; replace the gear with a switch-keyboard icon labelled « Changer de clavier ».
3. **Keyboard languages — `kbd.panel`**
   - iOS: long press on ☰: a panel lists « Français » / « English » / « Español » / « Deutsch » and the layouts, closed with ✕.
   - Android: no panel; a tap on the `FR` code moves to the next language (FR→EN→ES→DE), a long press opens Settings. Nothing hints at these gestures.
   - Proposal: a ☰ menu (or a long press on the code) that lists the languages and the layout, like iOS.
4. **Home without a model — `app.home`**
   - iOS: without a model, a « Télécharger un modèle pour commencer » card links to Models and replaces « Nouvelle dictée ».
   - Android: Home always shows « Modèle actif » and « Nouvelle dictée »; the missing model only shows up when tapping the mic (« Aucun modèle vocal »).
   - Proposal: the same prompt card on Home when no model is downloaded.
5. **Model cards — `app.models`**
   - iOS: engine badge (`WK`, `PK`), « Recommandé » badge on the model advised for the device, ⓘ « Langues prises en charge » on every card.
   - Android: engine badges `WK` / `NV` present, but no Recommended badge (only in onboarding) and no ⓘ; only Parakeet 110M carries « Anglais uniquement »; descriptions without accents (« Rapide et leger », « Precis et equilibre », « Haute precision », « Detection automatique »); « Stockage utilisé » line at the bottom.
   - Proposal: « Recommandé » badge and a language sheet per model; add the accents to the descriptions.
6. **Settings — `app.settings`**
   - iOS: section headers in normal case (« Transcription », « Clavier »), transcription language « Suivre la langue du clavier », ABC / 123 choice as a segment with a keyboard preview.
   - Android: no page title (the screen starts straight with « TRANSCRIPTION », while Models has its large title); headers in capitals, including « A PROPOS » without its accent; every choice goes through sheets (`app.settings.picker`); an extra APPARENCE section; no « Suivre la langue du clavier » option.
   - Proposal: a « Réglages » title like on Models; fix « À PROPOS »; an inline ABC / 123 segment; add « Suivre la langue du clavier » to the transcription language.
7. **Sounds — `app.settings.sounds`**
   - iOS: « Activer les sons » with an explanatory note, « Sons par événement »: « Début », « Fin », « Annuler » with the chosen sound shown on the right.
   - Android: « Son de dictée » without an explanatory note, sections « GENERAL » (without its accent) and « SONS », rows « Son de début / de fin / d'annulation » with the sound on the right (greyed out while sound is off).
   - Proposal: « GÉNÉRAL »; the short iOS labels (« Début », « Fin », « Annuler ») and a note under the switch.
8. **Recording notification — `sys.notification.recording`**
   - iOS: no notification (Dynamic Island, iOS-only).
   - Android: notification in English « Dictus - Recording » with the « Stop » action, on a French device.
   - Proposal: translate it (« Dictus — Enregistrement », « Arrêter »).
9. **Onboarding — `app.onboarding.*`**
   - iOS: dashed progress bar at the top, illustrations, tagline « Parlez. Dictus écrit. », language + layout step.
   - Android: progress dots, tagline « Dictation vocale, 100 % offline » (« Dictation » is English; in French: « Dictée vocale »), no language step but an ABC / 123 step.
   - Proposal: fix the tagline; replace the ABC / 123 step with the iOS language + layout step (the default mode stays in Settings).
10. **Emoji — `kbd.emoji`**
    - iOS: categories at the bottom with 🔍 « Rechercher des Emoji » and ABC.
    - Android: androidx `EmojiPickerView`, categories at the top, no search; ABC and ⌫ at the bottom; the mic row disappears while picking an emoji (on iOS, ☰ and the mic stay visible).
    - Proposal: add emoji search (French keywords) and keep the keyboard bar visible (with #139).
11. **System keyboard picker — `sys.ime-picker`**
    - iOS: the keyboard shows up once, « Dictus », in the globe list.
    - Android: Dictus declares two subtypes there, « AZERTY (Français) » and « QWERTY (Anglais) », unrelated to the four real languages (FR, EN, ES, DE) and to the layout chosen in Settings.
    - Proposal: a single « Dictus » subtype (or one per language actually supported).
12. **Keyboard accessibility labels — `kbd.keys`, `kbd.recording`**
    - iOS: labels in French on a French device.
    - Android: `Keyboard language DE`, `Settings` in English (the rest is in French: `Microphone`, `Annuler la dernière dictée`).
    - Proposal: translate these content descriptions (strings.xml FR/EN).

## Functional anomaly seen during the captures (not design)

- **`app.recording` — `Retour` does not stop the recording** (Pixel 4, `develop` build `f2b15c5`, 2026-10-20 21:43). « Nouvelle dictée » starts recording at once; leaving the screen with `Retour` goes back to Home but `DictationService` stays in the foreground (`isForeground=true`), the mic indicator stays green, no `Recording cancelled` line. It took « Applis actives → Arrêter » to release the mic. Proof outside the repo: `/home/pierre/Dev/dictus-proofs/verify-dictus-android/20261020-214307-0f15824/90-bug-recording-continues-after-retour.png`. Proposal: cancel the recording (or ask for confirmation) when leaving the screen; to be opened as an issue with the `needs-physical-test` label.
