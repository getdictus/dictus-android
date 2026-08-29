# Keyboard activation — onboarding mockup

Three states for the onboarding step that activates the Dictus keyboard, designed
2026-06-11. The frames live in `design/dictus-android-design.pen`; the PNGs here are
exports kept for quick reference.

| File | State |
|---|---|
| `activation-1-initial.png` | Dictus toggle off — "Ouvrir les Réglages" |
| `activation-2-ime-active.png` | Keyboard enabled (green check) — test field + "Changer de clavier" |
| `activation-3-default-set.png` | Enabled **and** set as default (two green checks) — "Continuer" |

The design separates **enabled** from **set as default**. Those are two distinct Android
actions, and conflating them is what shipped in v1.1.0: onboarding checked only
`enabledInputMethodList`, declared "All set!", and left users on their previous keyboard —
see #111. The fix that shipped in #41 drives the system input-method picker, which is the
"Changer de clavier" step above.

The rest of the v1.2 planning material this came from was dropped deliberately; only the
design is kept.
