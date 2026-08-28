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


class NgramAssetBuilderTest(unittest.TestCase):
    def test_parse_normalize_seed_and_round_trip_are_deterministic(self):
        source = (
            b"ngram,frequency\n"
            b"Je SUIS,100\n"
            b"je vais,50\n"
            b"je suis,80\n"
            b"a besoin de,75\n"
        )
        bigrams = BUILDER.parse_csv(source, 2)
        trigrams = BUILDER.parse_csv(source, 3)
        BUILDER.inject_seeds(bigrams, "fr")

        first = BUILDER.serialize(BUILDER.cap(bigrams), BUILDER.cap(trigrams))
        second = BUILDER.serialize(BUILDER.cap(bigrams), BUILDER.cap(trigrams))

        self.assertEqual(first, second)
        self.assertEqual(
            {"bigramKeys": len(bigrams), "trigramKeys": 1, "bytes": len(first)},
            BUILDER.inspect(first),
        )
        self.assertEqual(100_000, bigrams["je"]["suis"])
        self.assertEqual(100_000, bigrams["ça"]["va"])

    def test_source_hash_mismatch_is_rejected_before_build(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            (cache / "2grams_french.csv").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "source integrity mismatch"):
                BUILDER.acquire_source("fr", 2, cache)

    def test_hash_collision_is_rejected_instead_of_publishing_ambiguous_keys(self):
        with patch.object(BUILDER, "fnv1a", return_value=7):
            with self.assertRaisesRegex(ValueError, "collision"):
                BUILDER.serialize(
                    {"first": [("one", 10)], "second": [("two", 9)]},
                    {},
                )

    def test_spanish_has_no_authored_seeds_and_checked_in_output_is_pinned(self):
        parsed = BUILDER.parse_csv("también se,20\nespañol también,10\n".encode(), 2)
        before = {key: dict(value) for key, value in parsed.items()}
        BUILDER.inject_seeds(parsed, "es")
        self.assertEqual(before, parsed)

        binary = (ROOT / "trie/src/main/assets/es_ngrams.dict").read_bytes()
        self.assertEqual(
            "d48691aa2c8c95fff9f831f32e3ae565917f17352ea696f1fd6d655fb320750e",
            hashlib.sha256(binary).hexdigest(),
        )
        self.assertEqual(
            {"bigramKeys": 1029, "trigramKeys": 1443, "bytes": 45054},
            BUILDER.inspect(binary),
        )

    def test_spanish_source_hash_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            (cache / "2grams_spanish.csv").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "source integrity mismatch"):
                BUILDER.acquire_source("es", 2, cache)


if __name__ == "__main__":
    unittest.main()
