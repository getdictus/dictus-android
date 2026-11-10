#!/usr/bin/env python3
"""Build Dictus NGRM v1 assets from a pinned, integrity-checked Tatoeba sentence export."""

from __future__ import annotations

import argparse
import bz2
import hashlib
import json
import math
import re
import struct
import urllib.request
from collections import defaultdict
from pathlib import Path

# Tatoeba publishes one rolling export per language rather than versioned revisions, so the
# snapshot below is pinned by content hash. A mismatch is fatal on purpose: upstream moved, and
# the regenerated assets must be re-reviewed and re-pinned rather than silently changing.
SNAPSHOT = "2026-08-30"
BASE_URL = "https://downloads.tatoeba.org/exports/per_language"
SOURCE_SHA256 = {
    "de": "d94890e3f1ca3cf1fa928b4fa727119421458d11081a1afc1144faf73648847b",
    "en": "d1a7d45ad531a2cf5ee3b3660aaef3bdf6e1915f247893afd40dacc3a90a8175",
    "es": "6902d2d7d6170378003fd36b8b036abe2ce5776921995a9bea8fd94a38032bb6",
    "fr": "02c6a4041697aa3bd3e96f5b245dbada4523442d4755ccbc842af86ff9b5b412",
}
ISO3 = {"de": "deu", "en": "eng", "es": "spa", "fr": "fra"}
LANG_NAME = {"de": "german", "fr": "french", "en": "english", "es": "spanish"}
SEEDS = {
    # ADR 0001 deliberately defines no authored seed pairs for German.
    "de": [],
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
TOKEN = re.compile(r"[a-zA-ZÀ-ÿœŒ]+(?:[-'][a-zA-ZÀ-ÿœŒ]+)*")
# Anything that is neither a token character nor whitespace ends a context. This mirrors
# NextWordContextExtractor, which refuses to bridge a context backwards over punctuation or
# digits: a key learned across a comma could never be queried at runtime.
BOUNDARY = re.compile(r"[^a-zA-ZÀ-ÿœŒ'\-\s]+")
HEADER_SIZE = 32
MAX_RESULTS = 16
MIN_FREQUENCY = 2


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_name(language: str) -> str:
    return f"{ISO3[language]}_sentences.tsv.bz2"


def acquire_source(language: str, cache_dir: Path) -> tuple[bytes, str]:
    name = source_name(language)
    destination = cache_dir / name
    url = f"{BASE_URL}/{ISO3[language]}/{name}"
    if destination.exists():
        data = destination.read_bytes()
    else:
        request = urllib.request.Request(url, headers={"User-Agent": "Dictus-asset-builder/1"})
        with urllib.request.urlopen(request, timeout=300) as response:
            data = response.read()
        cache_dir.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    expected = SOURCE_SHA256[language]
    actual = sha256(data)
    if actual != expected:
        raise ValueError(f"source integrity mismatch for {name}: expected {expected}, got {actual}")
    return data, url


def normalize(token: str) -> str:
    return token.strip().lower().replace("’", "'").replace("‘", "'")


def parse_sentences(data: bytes) -> tuple[dict[str, dict[str, int]], dict[str, dict[str, int]]]:
    """Counts bigram and trigram continuations in one pass over the sentence export.

    Each export row is `id TAB iso3 TAB sentence`. Sentences are independent, and punctuation
    splits a sentence further, so no context ever spans a boundary the runtime would refuse.
    """
    bigrams: dict[str, dict[str, int]] = defaultdict(dict)
    trigrams: dict[str, dict[str, int]] = defaultdict(dict)
    for line in bz2.decompress(data).decode("utf-8").splitlines():
        row = line.split("\t")
        if len(row) != 3:
            continue
        for segment in BOUNDARY.split(normalize(row[2])):
            words = TOKEN.findall(segment)
            for index in range(len(words) - 1):
                key, predicted = words[index], words[index + 1]
                bigrams[key][predicted] = bigrams[key].get(predicted, 0) + 1
            for index in range(len(words) - 2):
                key = words[index] + "\0" + words[index + 1]
                predicted = words[index + 2]
                trigrams[key][predicted] = trigrams[key].get(predicted, 0) + 1
    return prune(bigrams), prune(trigrams)


def prune(data: dict[str, dict[str, int]]) -> dict[str, dict[str, int]]:
    """Drops continuations seen once. A single occurrence is as likely a typo as a phrase."""
    pruned: dict[str, dict[str, int]] = {}
    for key, results in data.items():
        kept = {word: count for word, count in results.items() if count >= MIN_FREQUENCY}
        if kept:
            pruned[key] = kept
    return pruned


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


def drop_collisions(data: dict[str, list[tuple[str, int]]]) -> tuple[dict[str, list[tuple[str, int]]], list[str]]:
    """Removes every key involved in an FNV-1a collision, keeping none of them.

    NGRM v1 addresses a context by a 32-bit hash, so at the corpus sizes a sentence-derived
    trigram section reaches, collisions are near-certain rather than exotic. Keeping the more
    frequent side would silently serve its continuations for the other context, so both go: a
    context with no prediction is honest, a context with another context's prediction is not.
    Raising this ceiling means a 64-bit key, and that is an NGRM v2 on both platforms.
    """
    by_hash: dict[int, list[str]] = defaultdict(list)
    for key in data:
        by_hash[fnv1a(key.encode("utf-8"))].append(key)
    dropped = sorted(key for keys in by_hash.values() if len(keys) > 1 for key in keys)
    if not dropped:
        return data, []
    discarded = set(dropped)
    return {key: results for key, results in data.items() if key not in discarded}, dropped


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


def serialize(
    bigrams: dict[str, list[tuple[str, int]]],
    trigrams: dict[str, list[tuple[str, int]]],
) -> tuple[bytes, dict[str, int]]:
    bigrams, bigram_collisions = drop_collisions(bigrams)
    trigrams, trigram_collisions = drop_collisions(trigrams)
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
    collisions = {
        "bigramKeysDroppedToCollision": len(bigram_collisions),
        "trigramKeysDroppedToCollision": len(trigram_collisions),
    }
    return header + bigram_bytes + trigram_bytes + strings, collisions


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
    source, url = acquire_source(language, cache_dir)
    bigrams, trigrams = parse_sentences(source)
    inject_seeds(bigrams, language)
    binary, collisions = serialize(cap(bigrams), cap(trigrams))
    stats = inspect(binary)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(binary)
    return {
        "language": language,
        "output": str(output),
        "sha256": sha256(binary),
        "snapshot": SNAPSHOT,
        **stats,
        **collisions,
        "sources": [{"url": url, "sha256": SOURCE_SHA256[language]}],
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
