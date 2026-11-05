# Google Play declarations: proposed answers

The answers proposed for the Play Console forms of Dictus Android (#220). Nothing here is filed yet: Pierre reviews them, then fills the Console. Each answer gives the code it rests on, so a change that breaks one is easy to spot.

What the app does, as far as Play is concerned: an offline dictation keyboard. The microphone records only during a dictation the user starts; speech becomes text on the phone with whisper.cpp or Parakeet; the text goes into the field the user is typing in. There is no account, no ads, no analytics, no crash reporter and no server of ours. The network is used to download speech models, nothing else (`ModelDownloader.kt` and `ResumableDownload.kt` hold the only network code).

## Data safety

**Does your app collect or share any of the required user data types?** No.

Google counts data as *collected* when it leaves the device, and as *shared* when it goes to a third party. Data processed only on the phone is neither ([Play Console Help, Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)).

| Data | What happens | Collected | Shared |
| --- | --- | --- | --- |
| Audio (voice) | Recorded in memory during a dictation the user starts (`DictationService`, foreground service of type microphone), transcribed on the phone, then dropped. Never written to disk, never sent. | No | No |
| Dictated text | Inserted into the field the user is typing in (by the keyboard) or shown in the app with « Copier ». The last result stays in memory; the raw text of each dictation is also kept 7 days in an app-private file for the transcription debug screen (`TranscriptionDebugStore`, #295). Dictus sends it nowhere; what the receiving app does with it is that app's business, as with any keyboard. | No | No |
| Typed text | Handled by the keyboard on the phone; the words it learns stay in the app's private storage (`Réinitialiser les mots appris` clears them). | No | No |
| Diagnostic logs | Written on the phone, without any dictated or typed text: counts and timings only (`PrivacySafeLog`). They leave only through « Exporter les logs de débogage », which opens the Android share sheet; the user picks the destination. | No (user-initiated share, see below) | No |
| Transcription debug JSON | Hidden screen (tap the version five times, #295). Holds the raw text of recent dictations. Leaves only through « Exporter le JSON… » and the share sheet. | No (user-initiated share) | No |
| Speech models | Downloaded from `huggingface.co` (Whisper) and GitHub releases of `k2-fsa/sherpa-onnx` (Parakeet) after the user confirms the language step or taps « Télécharger » in Modèles. The request carries no user data, only the model's path. These files are app content, not user data. | No | No |
| Device or other IDs, location, contacts, files, app activity | Never read. | No | No |

**Why the exports are not "collection".** Google exempts data that leaves the device through a user-initiated action the user expects to share, such as the share sheet. Dictus never sends the logs or the JSON itself; the user picks the app and the recipient. If Pierre later adds a "send logs to support" button that uploads them, this answer changes.

**IP address.** Downloading a model, like any HTTP request, gives Hugging Face or GitHub the phone's IP address. Dictus does not send it on purpose and receives nothing about the user. The usual reading is that this is not a declared data type; noted here so the choice is deliberate.

Since nothing is collected, the "Security practices" section (encryption in transit, deletion requests) does not apply. No account means no account deletion URL.

## Privacy policy

URL for the Console: `https://www.getdictus.com/en/privacy` (French page: `/fr/privacy`). The app links both from Réglages → À propos, with the terms of use.

**Blocker before submission:** on 2026-11-05 both pages say they cover "the Dictus apps for iOS and Desktop". Play reviews the policy against the Android app: it must name Android, the keyboard, the microphone, on-device processing and the log export. Asked of Wes on the website repo (outside this repo).

## Foreground services

The manifest declares two types. Play Console asks, for each, the use case, whether the user starts it, and a video link (an unlisted YouTube video is the usual answer). The videos below are in this repo; they need uploading.

| Type | Service | Use case | Started by | Video |
| --- | --- | --- | --- | --- |
| `microphone` | `DictationService` | Dictation: records the user's voice while they dictate, so the recording survives the keyboard's process being backgrounded. Notification « Dictus – Enregistrement » with « Arrêter ». | A tap on the microphone in the keyboard or « Nouvelle dictée » in the app. Never in the background, never at start-up. Refused without RECORD_AUDIO (`DictationService.startRecording`). | [`play-fgs/microphone.mp4`](play-fgs/microphone.mp4) (26 s, 215 KB): keyboard in Contacts, mic tap, « Listening… », the notification, ✓, transcription |
| `dataSync` | WorkManager `SystemForegroundService` (`ModelInstallWorker`) | Downloads and installs a speech model (77 MB to 640 MB) so it completes with the screen off. Notification « Installation de <modèle> » with progress. | The language step's « Continuer » in onboarding (the screen says the model downloads when you continue), « Télécharger » or « Réessayer » in Modèles. Nothing starts an install without a tap; WorkManager may resume one the user started after the process died. | [`play-fgs/data-sync.mp4`](play-fgs/data-sync.mp4) (26 s, 140 KB): Modèles → « Télécharger » on Base, progress, the notification |

The videos come from the emulator (Android 15), with POST_NOTIFICATIONS granted by adb so the notification shows: **the app never asks for that permission**, so on Android 13+ the notifications stay hidden unless the user turns them on (the services still run and show in the task manager). Worth a follow-up issue; Play does not require it.

## Ads

**Does your app contain ads?** No. No ad SDK in any module.

## App access

All functionality is available without an account or special access. Reviewers need a phone that can run a model: the onboarding picks one by RAM.

## Content rating (IARC questionnaire)

Category: **Utility, productivity, communication or other**.

| Question | Answer |
| --- | --- |
| Violence, blood, fear | No |
| Sexuality, nudity | No |
| Profanity or crude humour | No (the app writes what the user says; it generates nothing) |
| Controlled substances (drugs, alcohol, tobacco) | No |
| Gambling, simulated gambling | No |
| Users interact or exchange content with each other | No |
| Shares the user's location with others | No |
| Digital purchases | No (Dictus Pro is not on Android) |
| Unrestricted internet access (a browser) | No (links open the system browser) |

Expected rating: Everyone / PEGI 3 / USK 0.

## Target audience: open question for Pierre

Play asks for the age groups the app targets. Two choices make sense:

- **13 and over.** The keyboard is a general tool; a teenager typing homework is a fair user. Anything that includes under-13s puts the app under the Families policy (child-safe SDKs, stricter review), which this answer avoids.
- **18 and over.** The simplest review, and the one with the fewest follow-up questions. Shuts out nobody in practice: Play does not block downloads by this answer.

Either way, answer **No** to "Could your app unintentionally appeal to children?": no characters, games or child-directed content. **Pierre to decide.**

## Other declarations

All **No**: news app, health, financial features, government, COVID-19, restricted permissions (no SMS, call log, all-files, accessibility, location). The Data safety and the privacy policy above cover RECORD_AUDIO; the onboarding's microphone step is the prominent disclosure (#220).
