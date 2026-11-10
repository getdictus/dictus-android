# Companion insertion

Use `TextInsertionManager(context)` on the main thread. Accessibility enablement and the
Companion automatic-insertion preference belong to the coordinator. A connected service
does nothing until explicit `capture()`; idle accessibility events inspect no nodes.

`capture(): InsertionTarget?` replaces any previous capture. The opaque target contains no
text; the manager keeps a String/metadata snapshot in memory only. No nodes are retained.
Each acquisition refreshes the root and input-focused node before metadata or text reads;
failed refresh falls back. Identity includes a node hash correlation as well as the bounded
tree path, optional resource/unique IDs, class, package, window, and connection generation.
`insert(target, transcript): InsertionResult` consumes the target on every attempt.
`clear()` cancels a capture; `release()` clears and permanently disposes this manager.
The coordinator must also deduplicate results by recording session ID.

For the visible microphone activation activity:

1. Capture while the original editor still owns focus.
2. Call `beginActivation()` immediately before launching
   `dev.pivisolutions.dictus.companion.CompanionActivationActivity`.
3. Call `endActivation()` **before** finishing that activity.
4. Insert after the original editor regains focus. End alone does not authorize insertion.

The metadata-only exception requires an exactly named activation window without an input
editor, followed by the exact original package/window/editor identity. Window-list events
may arrive before the named activity event; such a provisional window cannot authorize
insertion without confirmation. Other app/field focus permanently revokes the target.
Keyboard/overlay window-list notifications are harmless when a fresh lookup still finds the
exact original editor. Ambiguous ordering, Quick Settings focus, or a tile capture while
System UI owns focus falls back to the coordinator's manual result UI. An activation activity
containing an editable field is intentionally unsupported.

`Inserted` means the merged text and final UTF-16 cursor position were re-read and verified.
`Rejected(reason)` means no text action was attempted. `Partial(reason)` means a text action
was attempted and may have written text, including when the action returned false or threw.
Never automatically retry `Partial`; show the transcript for user-controlled recovery.
This component never writes the clipboard or retries with paste actions.

Snapshots and merged values are capped at 16,384 UTF-16 units to limit Binder payload risk.
Before mutation, the final merged length must also fit the editor's reported `maxTextLength`
when provided. Emoji count as two units. Oversized insertion rejects without changing the
field, preserving the suffix instead of letting a native length filter truncate it. An
unreported/custom filter can still transform a write; verification reports that as partial.

Whole-value accessibility replacement supports plain native `android.widget.EditText` only,
including safe Dictus onboarding fields. All numeric (`TYPE_CLASS_NUMBER`) editors are
rejected even without a password flag: ordinary numeric fields can be PIN/OTP inputs, and
plain-text speech insertion cannot establish their safety. Editors with a WebView ancestor
are rejected even when the virtual HTML editor reports the native EditText class name.
Ancestry checks are bounded and read metadata only; uncertain ancestry falls back.
Password/input-type and platform sensitive metadata
are rejected before reading text, including verification reads. Rich spans, unknown/virtual
editors, missing actions, invalid selections, split surrogates, changed text/selection or
changed focus identity all fall back. Android accessibility actions are not atomic; a target
app can race a check or transform an action. Post-action verification therefore reports
uncertain outcomes as partial rather than attempting a destructive rollback.
