# Google Play listing images

Generates the phone screenshots, the feature graphic and the icon under `fastlane/metadata/android/<locale>/images/` (#221). The texts next to them are written by hand.

```bash
npm ci && npx playwright install chromium   # once
node render.mjs --proofs /tmp/planche       # every locale, plus one review board per locale
```

`template.html` is the Google Play counterpart of the iOS slides v16-B (`assets/appstore/screenshots.html` in `getdictus/dictus-ios`, read at `3dca1cf`). Its header lists what differs from iOS and why. `art/` holds that repo's `art/v16-B/hero.png` (`hero-en-US.png`) and `hero-fr-FR.png`, unchanged. The Android camera bar is drawn over the art by the template.

## Captures

`captures/` comes from the AVD `dictus_api35` (1080x2280) in the app's default dark theme, on a debug build of `verify-dictus-android`'s emulator tier. To retake them:

- Status bar: Android's demo mode (`com.android.systemui.demo`), clock 09:41, full battery and Wi-Fi.
- Language: system locale, plus `cmd locale set-app-locales`. The keyboard follows the system locale, not the app's.
- `01-recording.png`: keyboard dictation in Messages, about 1 s into `fr-beau-paris.wav` on the virtual microphone. The screen above the keyboard is covered by the template's unbranded email.
- `keyboards/`: « Dem », « Sta », « Mor » typed on the Dictus keys, in the French, English and German keyboards.
- `05-models.png`: Modèles, with Tiny (active) and Small Q5 downloaded.
- `06-microphone.png`: the onboarding's microphone step (#220).
