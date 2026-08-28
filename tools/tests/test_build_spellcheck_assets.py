import hashlib
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "build_spellcheck_assets.py"
SPEC = importlib.util.spec_from_file_location("build_spellcheck_assets", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT}")
BUILDER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = BUILDER
SPEC.loader.exec_module(BUILDER)
ROOT = Path(__file__).parents[2]


class SpellcheckAssetBuilderTest(unittest.TestCase):
    def test_fixture_is_normalized_filtered_and_has_stable_output(self):
        source = (
            "ngram,freq\n"
            "También,20\n"
            "español,10\n"
            "tambie\u0301n,15\n"
            "ESPANOL,7\n"
            "invalid phrase,8\n"
            "bad,nope\n"
        ).encode()
        words = BUILDER.parse_csv(source)

        first = BUILDER.serialize(words)
        second = BUILDER.serialize(dict(reversed(list(words.items()))))

        self.assertEqual({"también": 20, "español": 10, "espanol": 7}, words)
        self.assertEqual(first, second)
        self.assertEqual(
            "e7d54079ae634abba5554b4ce660360a5f1bb4eb9649ca3a5dc3736f257d4df7",
            hashlib.sha256(first).hexdigest(),
        )
        self.assertEqual(
            {"words": 3, "nodes": 4, "maxFrequency": 20, "bytes": 83},
            BUILDER.inspect(first),
        )

    def test_malformed_source_and_binary_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "UTF-8"):
            BUILDER.parse_csv(b"\xff")
        with self.assertRaisesRegex(ValueError, "empty"):
            BUILDER.serialize({})

        valid = BUILDER.serialize({"español": 10, "también": 20})
        malformed = [
            valid[:31],
            b"FAIL" + valid[4:],
            valid + b"trailing",
            valid[:8] + (999).to_bytes(4, "little") + valid[12:],
            valid[:21] + b"\x01" + valid[22:],
            valid[:32] + bytes([valid[32] | 0x01]) + valid[33:],
        ]
        for binary in malformed:
            with self.assertRaises(ValueError):
                BUILDER.inspect(binary)

    def test_source_hash_mismatch_is_rejected_before_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            (cache / "1grams_spanish.csv").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "source integrity mismatch"):
                BUILDER.acquire_source("es", cache)

    def test_checked_in_spanish_asset_has_pinned_hash_and_counts(self):
        binary = (ROOT / "trie/src/main/assets/es_spellcheck.dict").read_bytes()
        self.assertEqual(
            "39015e063ea69282ff6ac3099e852fe06b94118f41a69a680f75582899bb9ab4",
            hashlib.sha256(binary).hexdigest(),
        )
        self.assertEqual(10_000, BUILDER.inspect(binary)["words"])


if __name__ == "__main__":
    unittest.main()
