# Google Play listing images

Generates the phone screenshots, the feature graphic and the icon under `fastlane/metadata/android/<locale>/images/` (#221). The texts next to them are written by hand.

```bash
npm ci && npx playwright install chromium && ./fetch-icons.sh   # once
node render.mjs --proofs /tmp/planche                           # every locale, plus one review board per locale
node render.mjs --ribbon tiles --proofs /tmp/planche            # neutral tiles instead of the app logos
```

The ribbon of slides 1 and 2 shows real app logos, like iOS. They are third-party trademarks: `.icons/` stays out of git, and Google may refuse them on a listing. `--ribbon tiles` is the way back.

`template.html` is the Google Play counterpart of the iOS slides v16-B (`assets/appstore/screenshots.html` in `getdictus/dictus-ios`, read at `3dca1cf`). Its header lists what differs from iOS and why. `art/` holds that repo's `art/v16-B/hero.png` (`hero-en-US.png`) and `hero-fr-FR.png`, unchanged. The Android camera bar is drawn over the art by the template.

## Captures

`captures/` comes from the AVD `dictus_api35` (1080x2280) in the app's default dark theme, on a debug build of `verify-dictus-android`'s emulator tier. To retake them:

- Status bar: Android's demo mode (`com.android.systemui.demo`), clock 09:41, full battery and Wi-Fi.
- Language: system locale, plus `cmd locale set-app-locales`. The keyboard follows the system locale, not the app's.
- `01-recording.png`: keyboard dictation in Messages, about 1 s into `fr-beau-paris.wav` on the virtual microphone. The screen above the keyboard is covered by the template's unbranded email.
- `keyboards/`: « Dem », « Sta », « Mor » typed on the Dictus keys, in the French, English and German keyboards.
- `05-models.png`: Modèles after onboarding, with Parakeet 0.6B v3 downloaded, active and « Recommandé », then Tiny available, scrolled 18 px so the band ends under « ~77 Mo ». The badge needs 6 GB of RAM or more, so this one comes from a temporary copy of `dictus_api35` with `hw.ramSize = 6144M`, booted with `DICTUS_AVD=<copy> DICTUS_EMU_MEMORY=6144`.
- `06-microphone.png`: the onboarding's microphone step (#220).
