# Device profiles for layout audits

Ten emulator profiles that together cover the Android devices in use: phone sizes from 5.5" to 6.7", a foldable closed and open, 8" and 11" tablets, densities from mdpi to xxxhdpi, aspect ratios from 16:9 to 21:9, gesture and 3-button navigation, Android 10 to 16, and a large-font profile. First used by the pre-release audit of #339 (2026-11-12); its report, captures and defects live in the shared proofs folder (`proofs/339/report.md`), not in the repo.

Use them when a change touches the layout of the keyboard or of an app screen, and before a Play release. One emulator at a time, under the emulator lock and the Gradle lock (see the `verify-dictus-android` skill, Emulator tier).

## Profiles

| Profile | Screen (px) | Density | Size (dp) | Ratio | Android | Navigation | Stands for |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `petit55` | 540x960 | 240 (hdpi) | 360x640 | 16:9 | 10 (API 29) | 3-button | old entry-level qHD phones |
| `budget65` | 720x1600 | 320 (xhdpi) | 360x800 | 20:9 | 12 (API 31) | 3-button | Galaxy A / Redmi entry level; 360x800 is the most common Android viewport |
| `std61` | 1080x2400 | 420 (xxhdpi) | 411x914 | 20:9 | 14 (API 34) | gestures | mainstream FHD+ phone |
| `grand67` | 1440x3120 | 560 (xxxhdpi) | 411x891 | 19.5:9 | 15 (API 35) | gestures | QHD+ flagship |
| `ratio21` | 1080x2520 | 440 (xxhdpi) | 392x916 | 21:9 | 16 (API 36) | 3-button | tallest phones (Xperia, some Motorola) |
| `police` | 1080x2340 | 440, display size 506 | 341x740 | 19.5:9 | 11 (API 30) | gestures | font scale 1.3 and enlarged display size |
| `fold-ferme` | 884x2208 | 420 | 336x841 | ~22.5:9 | 14 (API 34) | gestures | foldable, outer screen |
| `fold-ouvert` | 1768x2208 | 420 | 673x841 | ~4:5 | 14 (API 34) | gestures | foldable, open |
| `tab8` | 800x1280 | 160 (mdpi) | 800x1280 | 16:10 | 13 (API 33) | 3-button | 8" entry-level tablet |
| `tab11` | 2560x1600 | 320 (xhdpi) | 1280x800 | 16:10 | 14 (API 34) | gestures, taskbar | 11" tablet |

Why these: StatCounter (October 2026) puts Android 11 to 16 at 89.6 % of mobile and tablet devices, and the Google Play distribution of 2026-10-01 (via composables.com) puts Android 10 or later at 93.9 %. The most common Android viewports are 360x800 and 384-414 dp wide; the two most common tablet resolutions are 1280x800 and 800x1280 (StatCounter, October 2026). About a quarter of Android users enlarge the font (Appt.org, quoted by ProAndroidDev). Re-check the shares before reusing the matrix a year later.

## Creating the AVDs

All images are `system-images;android-<api>;google_apis;x86_64`, installed with `sdkmanager`. Create each AVD from a close device definition, then override the screen in its `config.ini`:

```sh
avdmanager create avd -n audit_std61 -k "system-images;android-34;google_apis;x86_64" -d medium_phone
# then in ~/.android/avd/audit_std61.avd/config.ini:
#   hw.lcd.width = 1080   hw.lcd.height = 2400   hw.lcd.density = 420
#   hw.keyboard = no      hw.ramSize = 2048
```

Device definitions used: `Nexus 5` (`petit55`), `medium_phone` (`budget65`, `std61`, `ratio21`), `pixel_7_pro` (`grand67`), `pixel_4` (`police`), `7.6in Foldable` (both fold profiles, no screen override), `10.1in WXGA (Tablet)` (`tab8`), `pixel_tablet` (`tab11`).

After the first boot:

- French UI: `adb root`, `setprop persist.sys.locale fr-FR`, `stop; start`, then `adb unroot`.
- Navigation: `cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural` (or `.threebutton`).
- `settings put secure show_ime_with_hard_keyboard 1`, otherwise no soft keyboard shows.
- Rotation: `settings put system accelerometer_rotation 0` then `settings put system user_rotation 1` (landscape) or `0`.
- `police`: `settings put system font_scale 1.3` and `wm density 506`.

## Emulator limits on this machine

- API 35 and 36 images force the guest to 4 GB of RAM ("Increasing RAM size to 4096MB" in the emulator log), whatever `-memory` says. A QHD+ API 36 guest made QEMU die on its hanging-thread detector when the 12 GB host was busy. `grand67` therefore runs on a 1080x2340 / 420 AVD with `wm size 1440x3120` and `wm density 560`: same dp geometry, same density bucket.
- Without a visible Qt window, `adb emu fold` changes the device state but not the screen. `fold-ferme` is simulated with `wm size 884x2208` on the foldable AVD (`wm size reset` to open it); the tablet taskbar stays visible there, which a real outer screen does not show.
- A text field low on the screen shows whether the keyboard hides it: the Google Messages composer (`am start -a android.intent.action.SENDTO -d sms:5554`).
- Delete the audit AVDs afterwards (`avdmanager delete avd -n <name>`); keep `dictus_api35` as it is.
