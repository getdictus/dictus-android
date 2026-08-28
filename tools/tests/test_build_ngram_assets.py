import importlib.util
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


if __name__ == "__main__":
    unittest.main()
