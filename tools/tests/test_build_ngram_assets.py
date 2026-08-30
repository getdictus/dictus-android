import bz2
import importlib.util
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).parents[1] / "build_ngram_assets.py"
SPEC = importlib.util.spec_from_file_location("build_ngram_assets", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT}")
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)
ROOT = Path(__file__).parents[2]

PINNED = {
    "fr": ("cd8a505818a333d6d27076a7c47d1a86b838a0054b2003df408f8f4d4b191720", 28855, 134304, 3509057),
    "en": ("9d1a0a1383c0cc02f2697652d58e1fcb8cd12819b1c11aa15edb807ac8ab557d", 31520, 252388, 6460007),
    "es": ("bdaec24949ddeae8e7f4840d5e59de58fed227ecc62f4ffc78218e4c8981e4ed", 23431, 89053, 2353496),
    "de": ("889df643d4079fe8408996af53f840b58609a86aa2a205618b5b51d047cf6e6d", 27602, 131695, 3497225),
}


def export(*sentences: str) -> bytes:
    rows = "\n".join(f"{index}\tfra\t{text}" for index, text in enumerate(sentences, start=1))
    return bz2.compress(rows.encode("utf-8"))


class NgramAssetBuilderTest(unittest.TestCase):
    def test_repeated_pairs_are_counted_and_single_occurrences_pruned(self):
        bigrams, trigrams = BUILDER.parse_sentences(
            export("Je suis là", "Je suis parti", "Je pense donc"),
        )

        self.assertEqual({"suis": 2}, bigrams["je"])
        # "suis là", "suis parti" and every trigram occur once each, so nothing survives pruning.
        self.assertNotIn("suis", bigrams)
        self.assertEqual({}, trigrams)

    def test_case_and_curly_apostrophes_are_normalized_before_counting(self):
        bigrams, _ = BUILDER.parse_sentences(export("J’AI FAIM", "j'ai faim"))

        self.assertEqual({"faim": 2}, bigrams["j'ai"])

    def test_punctuation_ends_a_context_instead_of_bridging_over_it(self):
        # The runtime extractor refuses to read a context back across punctuation, so a key
        # learned across one could never be queried. Both sentences must contribute nothing.
        bigrams, _ = BUILDER.parse_sentences(export("Bonjour, madame", "Bonjour, madame"))

        self.assertNotIn("bonjour", bigrams)
        self.assertEqual({}, dict(bigrams))

    def test_digits_end_a_context_the_token_shape_could_not_represent(self):
        bigrams, _ = BUILDER.parse_sentences(export("article 42 modifié", "article 42 modifié"))

        self.assertNotIn("article", bigrams)
        self.assertNotIn("modifié", bigrams)

    def test_sentences_never_bridge_into_one_another(self):
        bigrams, _ = BUILDER.parse_sentences(export("il part", "il part", "demain", "demain"))

        self.assertEqual({"part": 2}, bigrams["il"])
        self.assertNotIn("part", bigrams)

    def test_authored_french_seeds_outrank_corpus_counts(self):
        bigrams, _ = BUILDER.parse_sentences(export("je vais bien", "je vais bien"))
        BUILDER.inject_seeds(bigrams, "fr")

        self.assertEqual(100_000, bigrams["je"]["vais"])
        self.assertEqual(100_000, bigrams["ça"]["va"])

    def test_serialization_is_deterministic_and_self_describing(self):
        bigrams, trigrams = BUILDER.parse_sentences(
            export("je suis venu hier", "je suis venu hier"),
        )

        first, collisions = BUILDER.serialize(BUILDER.cap(bigrams), BUILDER.cap(trigrams))
        second, _ = BUILDER.serialize(BUILDER.cap(bigrams), BUILDER.cap(trigrams))

        self.assertEqual(first, second)
        self.assertEqual(
            {"bigramKeys": len(bigrams), "trigramKeys": len(trigrams), "bytes": len(first)},
            BUILDER.inspect(first),
        )
        self.assertEqual(
            {"bigramKeysDroppedToCollision": 0, "trigramKeysDroppedToCollision": 0},
            collisions,
        )

    def test_colliding_keys_are_both_dropped_rather_than_one_answering_for_the_other(self):
        with patch.object(BUILDER, "fnv1a", side_effect=lambda key: 7 if key != b"third" else 9):
            binary, collisions = BUILDER.serialize(
                {"first": [("one", 10)], "second": [("two", 9)], "third": [("three", 8)]},
                {},
            )

        self.assertEqual(2, collisions["bigramKeysDroppedToCollision"])
        self.assertEqual(1, BUILDER.inspect(binary)["bigramKeys"])

    def test_source_hash_mismatch_is_rejected_before_build(self):
        for language in sorted(BUILDER.SOURCE_SHA256):
            with self.subTest(language=language), tempfile.TemporaryDirectory() as directory:
                cache = Path(directory)
                (cache / BUILDER.source_name(language)).write_bytes(b"tampered")
                with self.assertRaisesRegex(ValueError, "source integrity mismatch"):
                    BUILDER.acquire_source(language, cache)

    def test_spanish_and_german_have_no_authored_seeds(self):
        for language, sentence in (("es", "también se fue"), ("de", "ich bin hier")):
            with self.subTest(language=language):
                parsed, _ = BUILDER.parse_sentences(export(sentence, sentence))
                before = {key: dict(value) for key, value in parsed.items()}
                BUILDER.inject_seeds(parsed, language)
                self.assertEqual(before, parsed)

    def test_checked_in_assets_match_their_pinned_identity(self):
        for language, (digest, bigrams, trigrams, size) in PINNED.items():
            with self.subTest(language=language):
                binary = (ROOT / f"trie/src/main/assets/{language}_ngrams.dict").read_bytes()
                self.assertEqual(digest, hashlib.sha256(binary).hexdigest())
                self.assertEqual(
                    {"bigramKeys": bigrams, "trigramKeys": trigrams, "bytes": size},
                    BUILDER.inspect(binary),
                )


if __name__ == "__main__":
    unittest.main()
