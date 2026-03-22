---
status: resolved
trigger: "No visual difference between single-shift and caps lock states on the keyboard"
created: 2026-03-22T00:00:00Z
updated: 2026-03-22T00:00:00Z
---

## Current Focus

hypothesis: isCapsLock state is never passed to KeyButton — the rendering logic only knows about isShifted
test: traced the prop chain from KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton
expecting: isCapsLock should influence shift key appearance
next_action: report diagnosis

## Symptoms

expected: Shift key should look different in single-shift vs caps-lock mode (e.g., filled vs outline icon, underline, color change)
actual: Shift key looks identical in both states — same accent-colored background
errors: none (visual-only bug)
reproduction: tap shift once (single uppercase) vs double-tap shift (caps lock) — shift key appearance is the same
started: always broken — never implemented

## Eliminated

(none needed — root cause found on first hypothesis)

## Evidence

- timestamp: 2026-03-22
  checked: KeyboardScreen.kt state management (lines 38-48, 167-176)
  found: isCapsLock state IS correctly managed — double-tap sets isCapsLock=true, single tap sets isCapsLock=false
  implication: The state logic is correct; the problem is in rendering

- timestamp: 2026-03-22
  checked: KeyboardScreen.kt -> KeyboardView call (lines 55-58)
  found: KeyboardView receives isShifted but NOT isCapsLock
  implication: isCapsLock is never propagated to the rendering layer

- timestamp: 2026-03-22
  checked: KeyboardView.kt signature (line 27)
  found: KeyboardView only accepts isShifted:Boolean, no isCapsLock parameter
  implication: KeyboardView cannot pass caps state downstream even if it wanted to

- timestamp: 2026-03-22
  checked: KeyButton.kt background logic (lines 48-52)
  found: Shift key background uses only isShifted — `key.type == KeyType.SHIFT && isShifted -> DictusColors.Accent`. Both single-shift and caps-lock produce isShifted=true, so they render identically.
  implication: This is the root rendering code — it has no awareness of caps lock state

## Resolution

root_cause: |
  isCapsLock state is never propagated from KeyboardScreen to KeyButton.

  The chain is: KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton.

  - KeyboardScreen holds isCapsLock (line 40) but only passes isShifted to KeyboardView (line 57).
  - KeyboardView.kt (line 27) has no isCapsLock parameter.
  - KeyButton.kt (line 43) has no isCapsLock parameter.
  - KeyButton.kt line 49 renders shift key background as: `key.type == KeyType.SHIFT && isShifted -> DictusColors.Accent`

  Since both single-shift and caps-lock set isShifted=true, the shift key gets the same DictusColors.Accent background in both states. There is no code path that differentiates caps lock visually.

fix: (not applied — diagnosis only)
verification: (not applied — diagnosis only)
files_changed: []
