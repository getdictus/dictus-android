---
name: verify-dictus-android
description: Verify the Dictus Android app (dev.pivisolutions.dictus — the app, its Dictus keyboard IME and the dictation service) on Pierre's physical Pixel 4 over USB, driven with Argent — build and install the debug APK without losing data, check the device is worth driving, drive a mapped feature by its French labels, capture evidence outside the repo, clean up. Use when a change to app/, ime/ or core/ has to be seen working on the real phone rather than in unit tests, when a feature of features/README.md must be exercised, or when the screen map needs new captures.
---

# Verify Dictus on the Pixel

Five steps, always in this order: **Launch → Doctor → Drive → Evidence → Cleanup**. Helpers live in `helpers/` and run from the repository root. The phone is Pierre's own Pixel 4 (serial `99071FFAZ00EBN`, LineageOS, French): it holds his models, settings and notes. There is no second device and no way to run two instances side by side, so the run is serialized by a lock and **never** wipes or uninstalls anything.

Read first: `features/README.md` maps every user feature to its recipe, `features/screens.md` every screen. A pass here proves the debug build on this phone, nothing more.

**One driver at a time.** Argent (the `mcp__argent__*` tools, or `argent run <tool>` on the CLI) is the only UI driver. Its `describe` runs `uiautomator dump`, which needs UiAutomation for itself: never run mobile-mcp, Maestro, Appium or a hand-made `uiautomator dump` alongside it. mobile-mcp is removed from this machine; do not reinstall it.

## Test tiers

Pick the lowest tier that can prove the change. Each tier proves what the one above it cannot. The PR body names the tier used and what was left to the next one.

| Tier | Who and where | Proves | Does not prove |
| --- | --- | --- | --- |
| 1. Emulator | An agent on the AVD `dictus_api35` | UI, navigation, keyboard and IME, lifecycle, rotation, dictation from an injected WAV clip (pending) | A real microphone, real timings |
| 2. Pixel 4, Andy | Andy with this skill on `99071FFAZ00EBN` | Real microphone, permissions, notifications, transcription time with Tiny, Base and Small Q5 | A real voice, text quality, keyboard feel |
| 3. Pixel 4, Pierre | Pierre, in a batch device test | Real voice, text quality, keyboard feel in real apps | Phones faster than the Pixel 4 |
| 4. Out of reach | Nobody | Nothing | Models larger than Small Q5, Parakeet 0.6B v3 (needs 8 GB of RAM) |

**1. Emulator.** This is the default tier for every PR and for ship-issue agents. One emulator runs at a time, and the agent that drives it holds a lock, as on the Pixel. It covers the UI, navigation, the Dictus keyboard in a real text field, activity and service lifecycle, rotation, and dictation fed by a WAV clip instead of the microphone. Three parts are **pending** and come in follow-up PRs: an x86_64 debug build (`whisper/build.gradle.kts` builds `arm64-v8a` and `armeabi-v7a` only today), the virtual microphone that plays the WAV clip, and the emulator lock. Until they land, do not claim a tier 1 proof of dictation.

**2. Pixel 4, driven by Andy.** Use the steps below: Launch, Doctor, Drive, Evidence, Cleanup. This tier proves what needs the real hardware:

- the microphone opens and closes: `DictationService started foreground with ACTION_START`, then `Recording cancelled, samples discarded` or `Transcribed text inserted: length=…` (see Drive);
- `RECORD_AUDIO` is granted (Doctor) and the service runs in the foreground; notifications are denied for Dictus on this Pixel, so the proof is « 1 appli est active » in Quick Settings, not the notification;
- transcription time with Tiny, Base and Small Q5, measured as the gap between the logcat timestamps of `confirmAndTranscribe: … samples captured` and `Transcribed text inserted`. The app logs no duration of its own. The Pixel 4 is the performance floor: a model too slow on it is too slow for Dictus.

Andy's voice is not a test input. The room noise proves that the chain runs, not that the text is right.

**3. Pixel 4, Pierre.** Pierre checks what only a person can judge: dictation with his own voice in French and in other languages, the quality of the text, and how the keyboard feels in his own apps. A PR that touches this tier carries the `needs-physical-test` label (see `docs/agents/physical-test-label.md`: the label is added by path, and only Titus removes it after Pierre validates). It is not merged before Pierre has tested it in a batch device test.

Batch device tests follow the shared `batch-device-test` skill (proposed in Pivii/skills#20, not merged yet). Pierre replies per PR with `#<num> OK` or `#<num> KO: <what failed>`.

**4. Out of reach.** Pierre has no phone more recent than the Pixel 4. `small`, `medium-q5_0` and `parakeet-tdt-0.6b-v3` (gated at 8 GB of RAM: `Nécessite 8 Go de RAM ou plus` in Modèles) cannot be validated on the phones they are meant for. `small-q5_1` is not in this tier: it is the onboarding model below 8 GB and is measured in tier 2. A PR that changes them says "not tested on a recent phone". Onboarding installs Small Q5 below 8 GB of RAM (so on the Pixel 4) and Parakeet v3 from 8 GB (`ModelCatalog.recommendedModelKey`); `DEFAULT_KEY` (`tiny`) is only a fallback when no model is active.

## Launch

Take the Pixel lock first. Other threads install APKs on the same phone; the lock is the only thing that keeps two of them from installing over each other.

**The lock belongs to a session, not to a name.** `lock.sh acquire` enters only when `/home/pierre/Dev/dictus-proofs/.pixel-lock` does not exist (atomic `mkdir`), and writes a token unique to this session (pid + epoch ms + random) both in the lock's `owner` file and in the session directory `…/verify-dictus-android/sessions/<session>/token`. The session is this checkout (its basename plus a hash of its full path); two agents sharing one checkout must each export a distinct `DICTUS_VERIFY_SESSION`. `launch.sh` and `doctor.sh` refuse a lock whose token is not this session's; `lock.sh release` (and `cleanup.sh`) release only that token's lock and print the owner otherwise. The optional label after `acquire` is informative only.

```bash
H=.claude/skills/verify-dictus-android/helpers
$H/lock.sh acquire                # waits (retry every 60 s, max 30 min) while another session holds it
$H/lock.sh status                 # free / held by this session / held by another session: token=… session=…
$H/launch.sh                      # build + install -r + IME + launch
$H/launch.sh --no-build           # same, reusing app/build/outputs/apk/debug/app-debug.apk
```

`launch.sh`, in order:

1. Refuses to run without **this session's** lock, without the phone (`adb devices`), or if the phone is locked (`dumpsys window`: `isKeyguardShowing=true` or `mDreamingLockscreen=true`). **Never unlock the phone**: run `cleanup.sh` (it releases the lock even without a run), stop, report.
2. Creates the run directory `/home/pierre/Dev/dictus-proofs/verify-dictus-android/<YYYYMMDD-HHMMSS>-<sha>/`, writes the session token in its `run.env` and points the session's `run` file at it (`<token> <run-dir>`): helpers resolve only a run carrying the current token, so they never touch another session's run or an older one.
3. `git submodule update --init` then `./gradlew assembleDebug` (log in `build.log`; ~1 min warm, longer cold: whisper.cpp is compiled by CMake).
4. `adb install -r app-debug.apk` — **`-r` keeps the data. Never `adb uninstall`, never `pm clear`.** The debug keystore is shared with hermesbot, so the install updates in place.
5. Saves the settings snapshot as `prefs-before.digest`: key names and the sha256 of each value, never a value; volatile keys (last transcription, timestamps) are skipped.
6. Makes `dev.pivisolutions.dictus/.ime.DictusImeService` the default IME (`ime enable` + `ime set`) only if it is not already, and writes the previous IME to `run.env` for Cleanup.
7. `am start -W -n dev.pivisolutions.dictus/.MainActivity`.

**Ready means** the process is alive (`pidof`), `MainActivity` is the resumed activity, and the pid is unchanged 3 s later (an app that crashes on start fails here). Last line on success, the only one to rely on:

```
READY serial=99071FFAZ00EBN run=/home/pierre/Dev/dictus-proofs/verify-dictus-android/<run-id> pid=<pid> version=1.0.0 apk_sha=<16 hex>
```

Then confirm with Argent that the first screen is usable: `mcp__argent__describe` (udid `99071FFAZ00EBN`) lists the bottom tabs `Accueil`, `Modèles`, `Réglages` (onboarding completed) or `Commencer` (fresh install, onboarding step 1).

## Doctor

```bash
.claude/skills/verify-dictus-android/helpers/doctor.sh             # this worktree's run
.claude/skills/verify-dictus-android/helpers/doctor.sh <run-dir>   # a given run
```

Read-only: installs, taps and changes nothing. One `OK` / `WARN` / `FAIL` line per check, then `DOCTOR OK` or `DOCTOR FAIL <n>` (exit 1):

- `adb` and `argent` present; Pixel connected; **unlocked**; locale `fr-FR` (the labels below are French).
- The `.pixel-lock` is held **by this session** (FAIL if free or held by another session's token).
- `settings:` the harmless ones only (`active_model`, `transcription_language`, `keyboard_language`, `keyboard_layout`, `keyboard_mode`, `theme`): what the run must leave as it found them. Never the last transcription.
- Dictus installed (`versionName`, `lastUpdateTime`) and **the installed APK is this run's build** (sha256 of the on-device `base.apk` against `apk_sha` in `run.env`; reading 170 MB takes ~20 s).
- Dictus is the default IME; `RECORD_AUDIO` granted; the process runs; `files/dictus.log` readable with `run-as` (debug build only); which models are in `files/models` (none = every dictation ends on « Aucun modèle vocal »).
- No other UI driver: no long-lived `uiautomator`/Appium/Maestro on the phone, no `mobile-mcp`/Maestro/Appium on the host.

Run it after Launch and again before Evidence. A `FAIL` stops the run: fix it or report it, never drive past it.

## Drive with Argent

Device id for every call: `99071FFAZ00EBN`. Coordinates are normalized 0–1, taken from the tree that each interaction returns (`describe` when no fresh tree is at hand). Never guess them from pixels.

```text
mcp__argent__describe        { udid }                                  # tree with labels + frames
mcp__argent__gesture-tap     { udid, x, y }                            # centre of a frame from that tree
mcp__argent__await-ui-element{ udid, condition: "visible", selector: { text: "Réglages" }, timeoutMs: 8000 }
mcp__argent__keyboard        { udid, text: "…" }                       # types through adb, NOT through the Dictus keys
mcp__argent__screenshot      { udid }                                  # may show the previous frame: prove with evidence.sh
```

CLI equivalent: `argent run describe --udid 99071FFAZ00EBN`.

**Handles.** The app sets no `resource-id` (Compose without `testTagsAsResourceId`), so the stable handles are the French texts and content descriptions below, as Argent's tree shows them on the Pixel (seen 2026-10-20). Select by `text` (case-insensitive substring); prefer the longest unique label. A settings row is one node whose label joins title and value with ` / `.

| Where | Handles (FR device, as listed by `describe`) |
| --- | --- |
| Bottom tabs | `Accueil`, `Modèles`, `Réglages` |
| Accueil | `Modèle actif`, the model name (`Small Q5`), `Nouvelle dictée`; `Dernière transcription` + `Copier` only after a dictation |
| In-app recording | `Retour`, the timer `0:06`, `Arrêter`, `Toucher pour arrêter`; then `Transcription en cours…`, `Résultat` + `Copier` |
| Modèles | `Téléchargés`, `Disponibles`, card names `Tiny` `Base` `Small` `Small Q5` `Medium` `Parakeet 110M` `Parakeet 0.6B`, badges `WK` / `NV`, `Actif`, `Télécharger`, `Anglais uniquement`, `Nécessite 8 Go de RAM ou plus`, `Stockage utilisé : …` |
| Réglages | `TRANSCRIPTION`, `Langue / Automatique`, `CLAVIER`, `Langue du clavier / …`, `Disposition du clavier / AZERTY`, `Mode par défaut / ABC`, `Suggestions`, `Correction automatique`, `Retour haptique`, `Sons du clavier`, `Sons`, `APPARENCE`, `Thème / Sombre`, `Langue de l'interface / Automatique`, `A PROPOS`, `Version`, `Debug Logs`, `Exporter les logs de débogage`, `Licences`, `GitHub`, `Mentions légales` |
| Picker sheets | title (`Disposition du clavier`…), options with `[checked]`, `Fermer la feuille` (tap it to dismiss without choosing) |
| Sons | `Retour`, `GENERAL`, `Son de dictée`, `Volume`, `SONS`, `Son de début / Electronic 01F`, `Son de fin / …`, `Son d'annulation / …` (`[disabled]` while « Son de dictée » is off); in a list `Electronic 01F / Sélectionné` |
| Onboarding (code only, not seen) | `Commencer`, `Autoriser le micro`, `Continuer`, `Ouvrir les paramètres`, `Sélectionner Dictus`, `ABC` / `123`, `Télécharger`, `Réessayer` |

**The Dictus keyboard is fully in Argent's tree** (its own `ComposeView` at the bottom of the screen, above the app's nodes): each key is a `StaticText` with its label (`a`…`m`, `⇧`, `'`, `⌫`, `?123`, `😊`, `espace`, `retour`; `Leertaste` / `Eingabe` when the keyboard language is DE), the language code is `Keyboard language FR` (tap cycles FR→EN→ES→DE, long-press opens Dictus Réglages without tabs), the mic is `Microphone` (`Microphone indisponible pendant le chargement…` while the model loads), suggestions are three `View`s labelled with the words. While recording: `✕` (cancel), `✓` (insert), the timer `00:05`, `En écoute…`, `Settings` (opens the system keyboard picker). The emoji page uses androidx resource-ids `dev.pivisolutions.dictus:id/emoji_picker_header` / `emoji_picker_body`, category names (`UTILISÉS RÉCEMMENT`…), then `ABC` and `⌫`. Tap the centre of a key's frame.

**The tree can lag.** `describe` returned with an action sometimes shows the screen *before* the action (seen after taps on Sons, after scrolling Modèles, after a sub-screen's back). Give the step `delayMs: 1000` in `run-sequence`, or call `describe` again, and compare with the screenshot before tapping. Returning from a sub-screen can restore Réglages at a different scroll offset: re-read the frames, never reuse old ones.

**Text fields to type into.** The notes app on the Pixel is Simple Notes (`dev.dettmer.simplenotes`). Create a scratch note (`Nouvelle note` → `Note textuelle`), type in `Titre`, never open one of Pierre's notes (keep the « bonjour » notes untouched). Delete it at the end: `Plus d'options` → `Supprimer` moves it to the bin; then `Paramètres` → `Corbeille` → `Supprimer la note définitivement` on **your** note only (other threads' test notes may be there too) → `Supprimer définitivement`.

**Logs that prove an action** (Timber, logcat lines of the Dictus app selected by its uid, so they survive a restart; `helpers/log.sh`):

| Action | Line (regex) |
| --- | --- |
| Recording started (keyboard mic or Nouvelle dictée) | `DictationService started foreground with ACTION_START` |
| ✕ in the keyboard | `Recording cancelled, samples discarded` (service), between `AudioRecord cancelled, samples discarded` and the IME's `Recording cancelled` |
| ✓ in the keyboard | `confirmAndTranscribe: \d+ samples captured`, then `Transcribed text inserted: length=\d+` (never the text) |
| Undo chip | `Dictation undone: length=\d+` or `Dictation undo refused` |
| Model warm-up | `Engine prewarm complete for model=…` / `Engine prewarm skipped` |

```bash
H=.claude/skills/verify-dictus-android/helpers
M=$($H/log.sh mark)                          # device epoch with ms (e.g. 1792525951.295): assert only on what follows
$H/log.sh wait 'Recording cancelled' 15 "$M" # prints the line, exit 1 after 15 s
$H/log.sh since "$M"                         # everything Dictus logged since the mark
```

**The recipe this skill is proven on** — `dictation.cancel` from the keyboard, 2026-10-20, runs `20261020-215152-0f15824` and, with the session lock and value-free settings check, `20261020-223327-b2ecc45`:

```bash
H=.claude/skills/verify-dictus-android/helpers
$H/lock.sh acquire && $H/launch.sh && $H/doctor.sh
# Argent: launch-app dev.dettmer.simplenotes → tap « Nouvelle note » → « Note textuelle » (Titre focused, keyboard up)
$H/evidence.sh 01-dictation.cancel-keyboard-before
M=$($H/log.sh mark)
# Argent: tap the centre of « Microphone »
$H/log.sh wait 'ACTION_START' 15 "$M"
$H/evidence.sh 02-dictation.cancel-recording "$M"
# Argent: tap the centre of « ✕ »
$H/log.sh wait 'Recording cancelled, samples discarded' 10 "$M"
$H/evidence.sh 03-dictation.cancel-after "$M"
# Argent: describe → TextField "Titre" has no value; then delete the scratch note
$H/cleanup.sh                   # then mcp__argent__stop-all-simulator-servers {devices:["99071FFAZ00EBN"]}
```

Every other feature's recipe is in `features/<id>.md`, under "Driving it with Argent".

Rules that hold for every feature:

- **Do not change Pierre's settings**, or put them back exactly: transcription `Langue` stays `Automatique`, the active model stays the one Doctor reported, theme, keyboard language and layout unchanged. Open a picker only to capture it, then close it without choosing (system back).
- **Never download a model** unless the task needs one (Tiny is 75 MB; Medium and Parakeet 0.6B are over 500 MB). Never delete one.
- A dictation records the room through the phone's microphone. Cancel (`✕`) proves the chain without a transcript; a `✓` inserts whatever was heard.
- The keyboard only appears over a focused text field of another app; it is never in Dictus's own tree.
- **Never tap the nav bar's keyboard button** (`Changer le mode de saisie`, bottom right while a keyboard is up): a tap switches straight to the next keyboard (AOSP) and changes Pierre's default IME. Long-press it (`gesture-custom` Down, 900 ms, Up) to get the picker; if it happened, `adb -s 99071FFAZ00EBN shell ime set dev.pivisolutions.dictus/.ime.DictusImeService`.
- **« Nouvelle dictée » starts recording at once**, and `Retour` leaves the screen without stopping it (the service keeps the mic; seen 2026-10-20). Stop it with `Arrêter` (that transcribes the room and writes « Dernière transcription ») or, to discard, Quick Settings (swipe down twice) → `1 appli est active` → Dictus `Arrêter`, which stops the app process.
- Notifications are **denied** for Dictus on this Pixel: the recording notification never shows; the running service appears only as « 1 appli est active » in Quick Settings.
- Before changing the keyboard language, read it: Doctor's `settings:` line (`keyboard_language=…`) or Réglages → `Langue du clavier / …`. On 2026-10-20 it was DE. Cycle a full turn (4 taps) when you change it for a capture, and check Doctor's line again afterwards.

## Evidence

```bash
H=.claude/skills/verify-dictus-android/helpers
$H/evidence.sh 01-before            # screenshot only
$H/evidence.sh 02-after "$M"        # screenshot + every Dictus log line since the mark
$H/screen.sh kbd.recording          # a screen for the map: screens/kbd.recording.png in this skill
```

Everything for a run lands in `/home/pierre/Dev/dictus-proofs/verify-dictus-android/<run-id>/`, **never in the repository**:

| File | Written by | Content |
| --- | --- | --- |
| `report.txt` | all helpers | commit, branch, install, `READY`, each capture, `CLEANUP OK` |
| `run.env` | `launch.sh` | `PREV_IME`, `SERIAL`, `PID`, `APK_SHA`, `VERSION` |
| `build.log`, `install.log`, `am-start.log`, `00-launch.png` | `launch.sh` | build, install, first screen |
| `<name>.png` | `evidence.sh` | `adb exec-out screencap -p`, 1080×2280 |
| `<name>.log` | `evidence.sh` (with a mark) | Dictus logcat lines since the mark |
| `prefs-before.digest` / `prefs-after.digest` | `launch.sh` / `cleanup.sh` | per setting: key name + sha256 of its value (no value, volatile keys skipped), to prove settings were put back |
| `dictus.full.log` | `cleanup.sh` | the app's own `files/dictus.log` (privacy-safe: lengths, never text) |

Proof standards: drive the real user path (keys, buttons, the keyboard over a real app), not an internal setter or `am broadcast`; capture the screen **before** and **after** the action and the log line that proves it happened; check the side effect too (for a cancel: the field is unchanged; for an insert: the field holds the text). A screenshot without its log line is a picture, not a proof. Argent's `screenshot` can lag one frame: use `evidence.sh` (adb screencap) for the file you keep. Only screen-map captures (`screen.sh`, resized to 1140 px high and quantized with python3 PIL, < 300 KB) go in the repository, under `screens/`.

## Cleanup

```bash
.claude/skills/verify-dictus-android/helpers/cleanup.sh             # this session's run (if any), then its lock
.claude/skills/verify-dictus-android/helpers/cleanup.sh <run-dir>   # a run of this session (refused if its token is not ours)
```

Then stop Argent's services **for the Pixel only**: `mcp__argent__stop-all-simulator-servers { devices: ["99071FFAZ00EBN"] }` (CLI: `argent run stop-all-simulator-servers --devices 99071FFAZ00EBN`). Never call it without `devices`: one tool-server serves every agent on this machine.

With a run of this session, `cleanup.sh` copies `files/dictus.log` into it, writes `prefs-after.digest` and prints only `PREFS same` or `PREFS differ: <key names>` (never a value), and restores the IME that Launch replaced (if it replaced one). Then, run or no run (e.g. Launch refused because the phone was locked), it releases the lock **only if this session's token holds it**, ends with `CLEANUP OK run=…` and a `NEXT:` line reminding to stop Argent for the Pixel. It never uninstalls, never clears data, never kills a process by name, never deletes anything under `dictus-proofs/`. Delete your scratch note yourself before Cleanup. Run Cleanup after a failed run too, then check the evidence survived: `ls -la <run-dir>`.

## Emulator tier (x86_64 AVD)

For checks that do not need the real phone: the AVD `dictus_api35` (x86_64, API 35). The helpers above target the Pixel; on the emulator pass `-s emulator-5554` yourself. One emulator at a time on this 12 GB machine: take `/home/pierre/Dev/dictus-proofs/.emulator-lock` with `DICTUS_LOCK_NAME=emulator helpers/lock.sh acquire <label>` (or let `emulator-mic.sh start` take it; wait while another thread holds it, never kill its emulator), run `./gradlew --stop` before booting, and never build while the emulator runs.

The default debug build has **no x86_64 whisper/trie libs** (it compiles ARM only, like CI): the emulator installs it as x86_64 because AndroidX ships x86_64 libs, then whisper fails to load. Build the emulator APK with the opt-in property:

```bash
./gradlew assembleDebug -Pdictus.emulatorAbi=true    # adds lib/x86_64/libwhisper.so, libggml*.so, libdictus_trie.so
./gradlew --stop
emulator -avd dictus_api35 -memory 2048 -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# ... drive with Argent (udid emulator-5554), then:
adb -s emulator-5554 emu kill
DICTUS_LOCK_NAME=emulator helpers/lock.sh release   # releases only this session's token
```

Release the lock after `emu kill`, also when install or verification failed (kill the emulator first); `emulator-mic.sh stop` does both. Never remove another session's lock.

Release never contains x86_64 whisper/trie, with or without the property.

- **Use Tiny.** whisper on x86_64 is built for SSE4.2 only (the emulator CPU lacks AVX2/FMA): Tiny takes ~12 s for 3 s of audio; Small Q5 ~75 s.
- **Parakeet is unavailable on the emulator.** sherpa-onnx (`:asr`) ships arm64-v8a and armeabi-v7a only. Activating a Parakeet model there must end in a failed engine (log `ParakeetProvider: sherpa-onnx native library unavailable on x86_64`), never in a crash. Test Parakeet on the Pixel.

### Dictation from a WAV clip (virtual microphone)

A dictation can be checked on the emulator without the Pixel and without anyone speaking: `helpers/emulator-mic.sh` creates a PipeWire virtual microphone (`pw-loopback`: a sink `dictus_feed_N` whose sound comes out of a source `dictus_mic_N`), boots the AVD on it and plays a WAV clip into the sink.

```bash
H=.claude/skills/verify-dictus-android/helpers
A=.claude/skills/verify-dictus-android/fixtures/audio
$H/emulator-mic.sh build                       # ./gradlew assembleDebug -Pdictus.emulatorAbi=true, then --stop
$H/emulator-mic.sh start 1                     # lock, virtual mic, boot; last line: SERIAL emulator-5554
$H/emulator-mic.sh install 1                   # install -r, Dictus as IME, RECORD_AUDIO granted
# Argent on emulator-5554: Home → « New dictation » (recording starts), then:
$H/emulator-mic.sh play $A/fr-bonjour-test.wav 1
# Argent: Stop → compare the text with $A/fr-bonjour-test.txt
$H/emulator-mic.sh stop 1                      # emulator, virtual mic, lock; safe to run twice
$H/emulator-mic.sh status
```

- Set Réglages → Transcription → Language to **Français** first. On `Automatic`, Tiny takes the synthetic voice for Japanese.
- `start` boots without `-no-window`: that flag selects `qemu-system-x86_64-headless`, whose PulseAudio driver is a stub in emulator 37.1.11 (« Could not init `pa' audio driver », the guest records silence). It uses the Qt binary with a hidden window (`-qt-hide-window`, `QT_QPA_PLATFORM=offscreen`), `PULSE_SOURCE=dictus_mic_N`, `-allow-host-audio`, the guest's speaker on a sink linked to nothing (`dictus_spk_N`, so its sounds do not loop into the mic through the default sink), gRPC with a token (Argent needs it), then `adb emu avd hostmicon`. It refuses to finish if `pa` failed.
- The Gradle property is named once, in `env.sh` (`DICTUS_EMULATOR_GRADLE_PROP`); `install` refuses an APK without `lib/x86_64`.
- The clips in `fixtures/audio/` are synthesized French sentences (libespeak-ng, see its README), 16 kHz mono, each with its expected text in a `.txt` beside it.

**The emulator lock** is the Pixel lock's code (`lock.sh` with `DICTUS_LOCK_NAME=emulator`): atomic `mkdir`, a token unique to the session in `owner` and in the session's `token.emulator`, release by the owner only; `DICTUS_LOCK_NAME=emulator $H/lock.sh status` shows the owner. `start` adds three rules:

- It waits while another session holds the lock, or while any `qemu-system` or Gradle daemon runs (retry every 2 min, give up after 30 min; `DICTUS_EMU_WAIT_MIN=0` refuses at once). It waits for a free machine before taking the lock, so a waiting session never blocks a build.
- A lock older than 45 min while no `qemu-system` and no `pw-loopback` runs is stale: `start` removes it and prints its owner. The Pixel lock has no such rule.
- A failed `start` undoes itself (emulator, virtual mic, lock). Hold the lock only while the emulator is in use: `stop` as soon as the check is done, then `mcp__argent__stop-all-simulator-servers { devices: ["emulator-5554"] }`.

## Helpers

All executable, run from the repository root; all read `helpers/env.sh` (overrides: `DICTUS_SERIAL`, `DICTUS_PROOFS`, `DICTUS_EVIDENCE_ROOT`, `DICTUS_VERIFY_RUN`, `DICTUS_VERIFY_SESSION`).

| Script | Invocation | Ends with |
| --- | --- | --- |
| `helpers/lock.sh` | `lock.sh acquire [label]` · `lock.sh release` · `lock.sh status` | `LOCK ACQUIRED token=… session=…` / `LOCK RELEASED token=…` / `LOCK REFUSED: held by […]` / `free` |
| `helpers/emulator-mic.sh` | `emulator-mic.sh build` · `start [N]` · `install [N]` · `play <wav> [N]` · `stop [N]` · `status` | `BUILT …` / `SERIAL emulator-5554` / `INSTALLED on …` / `PLAYED <clip>` / `STOPPED <N>` |
| `helpers/launch.sh` | `launch.sh [--no-build]` | `READY serial=… run=… pid=… version=… apk_sha=…` |
| `helpers/doctor.sh` | `doctor.sh [run-dir]` | `DOCTOR OK` / `DOCTOR FAIL <n>` |
| `helpers/log.sh` | `log.sh mark` · `log.sh wait '<regex>' [secs] [mark]` · `log.sh since <mark>` | `found: <line>` / `error: Dictus is running but logged no line …` / `error: no Dictus process running …` |
| `helpers/evidence.sh` | `evidence.sh <name> [mark] [run-dir]` | `evidence <name>: <path>.png` |
| `helpers/screen.sh` | `screen.sh <screen-id>` | `screen <id>: <path> (<n> KB)` |
| `helpers/cleanup.sh` | `cleanup.sh [run-dir]` | `PREFS same` / `PREFS differ: <keys>`, `CLEANUP OK run=…`, `NEXT: stop Argent…` |
| `helpers/env.sh` | sourced, not run | `adbs`, `pixel_locked`, `device_mark`, `lock_is_mine`, `session_token`, `resolve_run`, `report`, `prefs_tool digest\|show`, `pull_prefs` |

## Keeping the map current

**A PR that adds or changes a screen or a user-facing feature MUST update `features/` and `features/screens.md` in the same PR.** Reuse the IDs of the iOS map (`getdictus/dictus-ios`, `.claude/skills/verify-dictus-ios/features/`) for the same feature or screen, and set the Android status (present, partial, absent, iOS-only). A feature that exists only on Android gets a new ID marked *Android-only*. Run `/maintain-verification-skill` from pstack (see `CLAUDE.md`) for a full pass over the map; a user has to invoke it.

TODO sync after dictus-ios #762 merges: it adds four screen IDs, `kbd.transcribing`, `kbd.dictation-undo`, `kbd.numbers` and `kbd.symbols`. Add them to the "All IDs" table of `features/README.md` and to `features/screens.md` with their Android status, then update the totals.
