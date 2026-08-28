#!/usr/bin/env python3
"""Build Dictus NGRM v1 assets from pinned, integrity-checked Google Books CSVs."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import struct
import urllib.request
from collections import defaultdict
from pathlib import Path

REVISION = "8e16017414d88aa75cba348416ba877a9135889a"
BASE_URL = f"https://raw.githubusercontent.com/orgtre/google-books-ngram-frequency/{REVISION}/ngrams"
SOURCE_SHA256 = {
    "es": {
        2: "fa2b6368b08744d03d604cdacdaab1c15834dfe2f1d81c9a3ad2f102a6643774",
        3: "85b49f0521c57dd8bc2718c367c326397a06e45a0b36cdbf86b0f06cdf80258d",
    },
    "fr": {
        2: "63f48cc4f9511306cdd15dacd93d2550447ba789c6a58b1ee302bbf3f562d48d",
        3: "0e1829c66c2244b567c2a18ca088daf1797770c90047855f8b04ee162aa84b90",
    },
    "en": {
        2: "9e2ae9e6149785a078e62325d468e462974ce6eb94eefe8f27b16e6d011e8ee5",
        3: "6cfdd4cc65cbd0df606d174df00274843c81eb430cfbeddce4711fb07f4e12df",
    },
}
LANG_NAME = {"fr": "french", "en": "english", "es": "spanish"}
SEEDS = {
    # ADR 0001 deliberately defines no authored seed pairs for Spanish.
    "es": [],
    "fr": [
        ("ça", "va"), ("ça", "marche"), ("en", "fait"), ("bien", "sûr"),
        ("au", "revoir"), ("merci", "beaucoup"), ("je", "vais"),
        ("je", "peux"), ("je", "veux"), ("je", "sais"), ("je", "pense"),
        ("je", "suis"), ("tu", "peux"), ("tu", "veux"), ("il", "faut"),
        ("on", "peut"), ("s'il", "vous"), ("vous", "plaît"),
    ],
    "en": [
        ("of", "course"), ("a", "lot"), ("in", "fact"), ("i", "think"),
        ("i", "know"), ("i", "can"), ("i", "will"), ("i", "have"),
        ("i", "want"), ("you", "know"), ("you", "can"), ("you", "are"),
        ("we", "are"), ("we", "have"), ("kind", "of"), ("sort", "of"),
    ],
}
TOKEN = re.compile(r"^[a-zA-ZÀ-ÿœŒ]+(?:[-'][a-zA-ZÀ-ÿœŒ]+)*$")
HEADER_SIZE = 32
MAX_RESULTS = 16


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_name(language: str, width: int) -> str:
    return f"{width}grams_{LANG_NAME[language]}.csv"


def acquire_source(language: str, width: int, cache_dir: Path) -> tuple[bytes, str]:
    name = source_name(language, width)
    destination = cache_dir / name
    url = f"{BASE_URL}/{name}"
    if destination.exists():
        data = destination.read_bytes()
    else:
        request = urllib.request.Request(url, headers={"User-Agent": "Dictus-asset-builder/1"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        cache_dir.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    expected = SOURCE_SHA256[language][width]
    actual = sha256(data)
    if actual != expected:
        raise ValueError(f"source integrity mismatch for {name}: expected {expected}, got {actual}")
    return data, url


def normalize(token: str) -> str:
    return token.strip().lower().replace("’", "'").replace("‘", "'")


def parse_csv(data: bytes, width: int) -> dict[str, dict[str, int]]:
    parsed: dict[str, dict[str, int]] = defaultdict(dict)
    rows = csv.reader(data.decode("utf-8").splitlines())
    for row in rows:
        if len(row) != 2 or row[0].lower() == "ngram":
            continue
        words = [normalize(word) for word in row[0].split()]
        if len(words) != width or not all(TOKEN.fullmatch(word) for word in words):
            continue
        try:
            frequency = int(row[1])
        except ValueError:
            continue
        if frequency < 2:
            continue
        key = words[0] if width == 2 else words[0] + "\0" + words[1]
        predicted = words[-1]
        parsed[key][predicted] = max(parsed[key].get(predicted, 0), frequency)
    return parsed


def inject_seeds(data: dict[str, dict[str, int]], language: str) -> None:
    for left, right in SEEDS[language]:
        data.setdefault(left, {})[right] = max(data.get(left, {}).get(right, 0), 100_000)


def cap(data: dict[str, dict[str, int]]) -> dict[str, list[tuple[str, int]]]:
    return {
        key: sorted(results.items(), key=lambda item: (-item[1], item[0]))[:MAX_RESULTS]
        for key, results in sorted(data.items())
        if results
    }


def fnv1a(data: bytes) -> int:
    value = 0x811C9DC5
    for byte in data:
        value = ((value ^ byte) * 0x01000193) & 0xFFFFFFFF
    return value


def normalized(data: dict[str, list[tuple[str, int]]]) -> dict[str, list[tuple[str, int]]]:
    maximum = max((frequency for results in data.values() for _, frequency in results), default=1)
    denominator = math.log(maximum + 1)
    return {
        key: [(word, max(1, min(65535, int(math.log(frequency + 1) / denominator * 65535))))
              for word, frequency in results]
        for key, results in data.items()
    }


def section(data: dict[str, list[tuple[str, int]]], offsets: dict[str, int]) -> bytes:
    hashed: list[tuple[int, str, list[tuple[str, int]]]] = []
    seen: dict[int, str] = {}
    for key, results in data.items():
        key_hash = fnv1a(key.encode("utf-8"))
        collision = seen.get(key_hash)
        if collision is not None and collision != key:
            raise ValueError(f"FNV-1a collision between {collision!r} and {key!r}")
        seen[key_hash] = key
        hashed.append((key_hash, key, results))
    output = bytearray()
    for key_hash, _, results in sorted(hashed):
        output.extend(struct.pack("<IB", key_hash, len(results)))
        for word, score in results:
            output.extend(struct.pack("<IH", offsets[word], score))
    return bytes(output)


def serialize(bigrams: dict[str, list[tuple[str, int]]], trigrams: dict[str, list[tuple[str, int]]]) -> bytes:
    bigrams = normalized(bigrams)
    trigrams = normalized(trigrams)
    words = sorted({word for dataset in (bigrams, trigrams) for results in dataset.values() for word, _ in results})
    strings = bytearray()
    offsets: dict[str, int] = {}
    for word in words:
        offsets[word] = len(strings)
        strings.extend(word.encode("utf-8") + b"\0")
    bigram_bytes = section(bigrams, offsets)
    trigram_bytes = section(trigrams, offsets)
    trigram_offset = HEADER_SIZE + len(bigram_bytes)
    string_offset = trigram_offset + len(trigram_bytes)
    header = b"NGRM" + struct.pack(
        "<HHIIIIII", 1, 0, len(bigrams), len(trigrams), HEADER_SIZE,
        trigram_offset, string_offset, len(strings),
    )
    assert len(header) == HEADER_SIZE
    return header + bigram_bytes + trigram_bytes + strings


def inspect(data: bytes) -> dict[str, int]:
    if len(data) < HEADER_SIZE or data[:4] != b"NGRM":
        raise ValueError("invalid generated NGRM header")
    version, flags, bigrams, trigrams, bigram_offset, trigram_offset, string_offset, string_size = struct.unpack(
        "<HHIIIIII", data[4:HEADER_SIZE],
    )
    if version != 1 or flags != 0 or bigram_offset != HEADER_SIZE or string_offset + string_size != len(data):
        raise ValueError("invalid generated NGRM layout")
    if not (bigram_offset <= trigram_offset <= string_offset):
        raise ValueError("overlapping generated NGRM sections")
    return {"bigramKeys": bigrams, "trigramKeys": trigrams, "bytes": len(data)}


def build(language: str, output: Path, cache_dir: Path) -> dict[str, object]:
    bigram_source, bigram_url = acquire_source(language, 2, cache_dir)
    trigram_source, trigram_url = acquire_source(language, 3, cache_dir)
    bigrams = parse_csv(bigram_source, 2)
    trigrams = parse_csv(trigram_source, 3)
    inject_seeds(bigrams, language)
    binary = serialize(cap(bigrams), cap(trigrams))
    stats = inspect(binary)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(binary)
    return {
        "language": language,
        "output": str(output),
        "sha256": sha256(binary),
        **stats,
        "sources": [
            {"url": bigram_url, "sha256": SOURCE_SHA256[language][2]},
            {"url": trigram_url, "sha256": SOURCE_SHA256[language][3]},
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language", choices=sorted(LANG_NAME), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path, default=Path(".cache/ngrams"))
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    result = build(args.language, args.output, args.cache_dir)
    if args.manifest:
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
