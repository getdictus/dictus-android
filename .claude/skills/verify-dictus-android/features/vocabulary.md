# Custom vocabulary

The user teaches Dictus how a term is spelled and how the engine mishears it; every dictation then has the misheard forms rewritten into the user's spelling before it is inserted. Pro only, as on iOS.

- **ID:** `vocabulary`
- **Tier:** Pro (`ProStatus.isPro`; nothing is sold on Android yet, so in practice the Debug build's « Forcer Dictus Pro »)
- **Android status:** partial — no « Analyser mes transcriptions » suggestions, no onboarding scene
- **Screens:** `app.pro.vocabulary`, `app.pro.vocabulary.term-editor` — see [screens.md](screens.md)

## Sub-features

- `vocabulary.add`: « Ajouter un terme » opens « Nouveau terme »: « L’orthographe correcte » and « Ce que Dictus écrit à la place » (variants separated by commas). A term saved alone lists as « En attente d’erreurs à corriger » and replaces nothing. A term already stored is refused (« Ce terme est déjà dans votre vocabulaire. »); 200 terms at most. — present
- `vocabulary.edit`: tap a term, « Modifier ». — present
- `vocabulary.delete`: swipe a term to the left. — present (iOS also has an « Edit » mode; Android has the swipe only)
- `vocabulary.reset`: « Réinitialiser le vocabulaire », then « Oublier N termes ». — present
- `vocabulary.suggestions`: « Analyser mes transcriptions ». — absent (needs history, #202, and the iOS miner)
- `vocabulary.term-variants`: the suggestions sheet's first section. — absent
- `vocabulary.apply`: every dictation (app and keyboard) has its variants replaced, after the non-speech tags are dropped and before the full stop is added. — present
- `vocabulary.toggle`: « Vocabulaire » switch at the top of the screen; off keeps the terms (dimmed, inert) and replaces nothing. Each term also has its own switch. — present

## How to get to it (user POV)

1. With Pro: Dictus → Réglages → « Dictus Pro » → « Vocabulaire », as on iOS. Réglages has no Vocabulaire row of its own.
2. « Ajouter un terme », type the spelling and how Dictus writes it, « Enregistrer ».
3. Dictate the misheard form: the inserted text carries the spelling.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a Debug build. Note whether « Forcer Dictus Pro » is on: it must be the same at the end.

- **Pro.** Réglages → scroll → « Développeur » → `Forcer Dictus Pro` on. → Réglages shows the `Dictus Pro` row at the top.
- **Open.** Tap `Dictus Pro`, then the `Vocabulaire` card (`pro_hub_feature_vocabulary`). → the switch `Vocabulaire`, « Vos termes », `Ajouter un terme` (`screen.sh app.pro.vocabulary`).
- **Add.** Tap `Ajouter un terme`, type the term and a variant, tap `Enregistrer`. → the row with « Remplace : … » (`screen.sh app.pro.vocabulary.term-editor` before saving).
- **Apply.** Dictate the variant from Accueil → `Nouvelle dictée`: the result carries the term. The log line `vocabularyApplied: enabled=yes entries=N replacements=1` (counts only) proves the pass ran.
- **Clean up.** Delete the term by swiping it left, put « Forcer Dictus Pro » back.
- **Proof.** The tree with the row, the dictation's result text and the `vocabularyApplied` log line.

## Gotchas

- Pierre's Pixel holds no vocabulary: add only a test term and delete it before leaving.
- Without Pro the hub and its card are out of reach; with Pro and the switch off, the screen shows the terms dimmed and `vocabularyApplied: enabled=no`.
- The emulator proves `vocabulary.apply` with the French clip `fixtures/audio/fr-beau-paris.wav` (« Il fait beau aujourd'hui à Paris. »): the term `Lutèce` with the variant `Paris` turns the result into « … à Lutèce. ».
