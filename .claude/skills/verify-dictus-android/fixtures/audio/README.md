# French test clips for the emulator microphone

| Clip | Expected text | Length |
| --- | --- | --- |
| `fr-bonjour-test.wav` | `fr-bonjour-test.txt`: « Bonjour, ceci est un test de dictée. » | 3.6 s |
| `fr-beau-paris.wav` | `fr-beau-paris.txt`: « Il fait beau aujourd'hui à Paris. » | 2.9 s |

16 kHz, mono, 16-bit PCM, about 0.5 s of silence before the voice and 0.7 s after. Play one into the emulator with `helpers/emulator-mic.sh play <clip.wav> [N]`.

How they were made: `./make-clips.sh` synthesizes each `.txt` with libespeak-ng 1.51 (voice `fr`, 140 words per minute) through `espeak_wav.py` (Python ctypes, standard library only), then pads and resamples with ffmpeg 6.1. Running it again on the same versions gives the same bytes.

Licence: generated locally by speech synthesis from the sentences above. No recording or third-party audio is involved.
