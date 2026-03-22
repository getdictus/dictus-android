---
status: resolved
trigger: "Mic button row is positioned below the keyboard instead of above it"
created: 2026-03-22T00:00:00Z
updated: 2026-03-22T00:00:00Z
---

## Current Focus

hypothesis: MicButtonRow is placed after KeyboardView in the Column, rendering it below
test: Read KeyboardScreen.kt composable hierarchy
expecting: MicButtonRow call appears after KeyboardView
next_action: report diagnosis

## Symptoms

expected: Mic button row above the keyboard rows (matching design mockups)
actual: Mic button row renders below the keyboard
errors: none (visual layout issue)
reproduction: Launch IME keyboard — mic row is at the bottom
started: Since initial implementation

## Eliminated

(none needed — root cause found on first inspection)

## Evidence

- timestamp: 2026-03-22
  checked: KeyboardScreen.kt lines 51-127 — Column composable order
  found: Column children are ordered as (1) KeyboardView at line 55, (2) AccentPopup conditional at line 105, (3) MicButtonRow at line 126. In Compose Column, children render top-to-bottom, so MicButtonRow renders below KeyboardView.
  implication: The ordering is simply reversed from what the design requires.

- timestamp: 2026-03-22
  checked: MicButtonRow.kt KDoc comment at line 23
  found: KDoc says "Placeholder row below the keyboard" — confirms the developer intentionally placed it below, but this contradicts the design mockups.
  implication: The placement was a conscious choice but doesn't match the design spec.

## Resolution

root_cause: In KeyboardScreen.kt, the Column composable (lines 51-127) places KeyboardView first (line 55) and MicButtonRow last (line 126). Compose Column renders children top-to-bottom, so MicButtonRow appears below the keyboard. The design requires it above.
fix: (not applied — diagnosis only)
verification: (not applied)
files_changed: []
