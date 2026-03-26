---
status: investigating
trigger: "Waveform on onboarding welcome screen (step 1) is static, should be animated transcription waveform style"
created: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:00:00Z
---

## Current Focus

hypothesis: Welcome screen uses a private static WaveformDecoration with fixed Random(42) bars instead of reusing the animated TranscribingScreen sine-wave pattern via core WaveformBars
test: Compare implementations — confirmed by reading source
expecting: Static bars with no animation API
next_action: Provide diagnosis and recommended fix approach

## Symptoms

expected: Animated transcription-style waveform (traveling sine wave) on onboarding welcome screen
actual: Static 15-bar waveform drawn with fixed Random(42) seed — bars never move
errors: None (visual issue only)
reproduction: Launch app -> onboarding step 1 -> waveform is frozen
started: Always — implemented static by design (see comment in source)

## Eliminated

(none)

## Evidence

- timestamp: 2026-03-26
  checked: OnboardingWelcomeScreen.kt implementation
  found: Uses private WaveformDecoration composable — 15 bars, fixed Random(42) seed, no animation. Comment explicitly says "WHY static waveform (not animated)" — this was a deliberate design choice that now needs reversal.
  implication: The waveform was intentionally made static. Needs to be replaced with animated version.

- timestamp: 2026-03-26
  checked: TranscribingScreen.kt in ime module
  found: Already has exact pattern needed — uses rememberInfiniteTransition + animateFloat to generate a sine-wave phase, then creates 30 energy values via `0.2f + 0.25f * (sin(...) + 1f)` and feeds them to core WaveformBars composable. 2-second loop, LinearEasing, RepeatMode.Restart.
  implication: The animation pattern exists and is production-ready. Can be extracted or copied.

- timestamp: 2026-03-26
  checked: core/ui/WaveformBars.kt
  found: Shared 30-bar WaveformBars composable in core module. Takes energyLevels: List<Float>, handles padding/trimming to 30 bars, uses same accent gradient color scheme. Already available to the app module.
  implication: No need to build new animation component. Core WaveformBars + sine wave driver is the solution.

## Resolution

root_cause: OnboardingWelcomeScreen uses a private static WaveformDecoration (15 bars, Random(42) seed, no animation) instead of reusing the animated core WaveformBars with a sine-wave energy driver like TranscribingScreen does.
fix: (pending)
verification: (pending)
files_changed: []
