---
status: resolved
trigger: "accent popup for 'a' key overflows off the left edge of the screen"
created: 2026-03-25T00:00:00Z
updated: 2026-03-25T00:00:00Z
---

## Current Focus

hypothesis: The Popup uses Alignment.TopCenter with no horizontal offset, so it centers the accent strip over the key. For leftmost keys like "a", half the popup extends beyond the left screen edge. No edge-clamping logic exists.
test: Read popup positioning code in KeyButton.kt
expecting: No x-offset or screen-bounds check
next_action: Confirm root cause and propose fix

## Symptoms

expected: All accent characters visible when long-pressing "a" key
actual: Accent popup extends past left screen edge, leftmost characters cut off
errors: None (visual overflow, not a crash)
reproduction: Long-press "a" key on AZERTY keyboard
started: Since accent popup was implemented

## Eliminated

(none)

## Evidence

- timestamp: 2026-03-25T00:00:00Z
  checked: KeyButton.kt lines 278-295 — accent popup positioning
  found: Popup uses `alignment = Alignment.TopCenter` and `offset = IntOffset(0, -100)` with no horizontal offset. The popup is centered over the key with zero awareness of screen bounds.
  implication: For the leftmost key "a", the popup center aligns with the key center. Since the popup is wider than the key (e.g. 6 accents * 44dp = 264dp vs ~36dp key width), roughly half the popup (132dp) extends to the left of the key center, overflowing the screen.

- timestamp: 2026-03-25T00:00:00Z
  checked: resolveAccentIndex() at lines 102-110
  found: The function calculates popup-relative coordinates using `popupLeftPx = (keyWidthPx - popupWidthPx) / 2f` — same centered assumption. This means finger tracking for accent selection also uses the unclamped position.
  implication: Both the visual popup AND the touch-tracking logic share the same centered-over-key assumption. Both need the same offset correction.

- timestamp: 2026-03-25T00:00:00Z
  checked: AccentPopup.kt
  found: AccentPopup is purely visual (Row of 44dp boxes). It has no positioning logic — positioning is 100% controlled by the Popup wrapper in KeyButton.kt.
  implication: Fix belongs entirely in KeyButton.kt.

## Resolution

root_cause: KeyButton.kt line 279-281 — the accent Popup uses `Alignment.TopCenter` with `IntOffset(0, -100)` and no horizontal offset. The popup is always centered over the key regardless of screen position. For edge keys like "a" (leftmost), this causes the popup to overflow the screen boundary because no edge-clamping is applied.
fix: (pending)
verification: (pending)
files_changed: []
