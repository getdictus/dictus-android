#!/usr/bin/env python3
"""Build deterministic Dictus DTRI v1 spell assets from pinned Google Books CSVs."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import struct
import unicodedata
import urllib.request
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

REVISION = "8e16017414d88aa75cba348416ba877a9135889a"
BASE_URL = f"https://raw.githubusercontent.com/orgtre/google-books-ngram-frequency/{REVISION}/ngrams"
SOURCE_SHA256 = {
    "es": "9b94729d538b4335b14f5776c8451d48a0b3fa23237bc5bc0da7224efa421fae",
}
LANG_NAME = {"es": "spanish"}
MAGIC = b"DTRI"
VERSION = 1
HEADER_SIZE = 32
MAX_WORD_CODE_UNITS = 32


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_name(language: str) -> str:
    return f"1grams_{LANG_NAME[language]}.csv"


def acquire_source(language: str, cache_dir: Path) -> tuple[bytes, str]:
    name = source_name(language)
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
    actual = sha256(data)
    expected = SOURCE_SHA256[language]
    if actual != expected:
        raise ValueError(f"source integrity mismatch for {name}: expected {expected}, got {actual}")
    return data, url


def utf16_units(word: str) -> list[int]:
    encoded = word.encode("utf-16-le")
    return list(struct.unpack(f"<{len(encoded) // 2}H", encoded))


def parse_csv(data: bytes) -> dict[str, int]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError("source is not valid UTF-8") from error
    words: dict[str, int] = {}
    for row in csv.reader(text.splitlines()):
        if len(row) != 2 or row[0].lower() == "ngram":
            continue
        word = unicodedata.normalize(
            "NFC", row[0].strip().lower().replace("’", "'").replace("‘", "'"),
        )
        try:
            frequency = int(row[1])
        except ValueError:
            continue
        units = utf16_units(word)
        if (not word or any(character.isspace() for character in word) or
                "-" in word or frequency <= 0 or len(units) > MAX_WORD_CODE_UNITS or
                any(unit == 0 for unit in units)):
            continue
        words[word] = max(words.get(word, 0), frequency)
    return words


@dataclass
class TrieNode:
    chars: list[int] = field(default_factory=list)
    children: dict[int, "TrieNode"] = field(default_factory=dict)
    terminal: bool = False
    frequency: int = 0

    def child_list(self) -> list["TrieNode"]:
        return [self.children[key] for key in sorted(self.children)]


def insert(node: TrieNode, remaining: list[int], frequency: int) -> None:
    if not remaining:
        node.terminal = True
        node.frequency = max(node.frequency, frequency)
        return
    child = node.children.get(remaining[0])
    if child is None:
        node.children[remaining[0]] = TrieNode(remaining, terminal=True, frequency=frequency)
        return
    common = 0
    while common < len(child.chars) and common < len(remaining) and child.chars[common] == remaining[common]:
        common += 1
    if common == len(child.chars):
        insert(child, remaining[common:], frequency)
        return
    split = TrieNode(child.chars[:common])
    child.chars = child.chars[common:]
    split.children[child.chars[0]] = child
    node.children[remaining[0]] = split
    if common == len(remaining):
        split.terminal = True
        split.frequency = frequency
    else:
        suffix = remaining[common:]
        split.children[suffix[0]] = TrieNode(suffix, terminal=True, frequency=frequency)


def serialize(words: dict[str, int]) -> bytes:
    if not words:
        raise ValueError("cannot serialize an empty dictionary")
    root = TrieNode()
    for word, frequency in sorted(words.items()):
        insert(root, utf16_units(word), frequency)
    nodes: list[TrieNode] = []
    queue = deque(root.child_list())
    while queue:
        node = queue.popleft()
        nodes.append(node)
        queue.extend(node.child_list())
    if len(root.children) > 255 or any(len(node.children) > 255 or len(node.chars) > 255 for node in nodes):
        raise ValueError("DTRI node limit exceeded")

    def node_size(node: TrieNode, pointer_size: int) -> int:
        return (1 + (1 if len(node.chars) > 1 else 0) + 2 * len(node.chars) +
                (2 if node.terminal else 0) + (1 + pointer_size if node.children else 0))

    offsets: dict[int, int] = {}
    for attempt in range(10):
        previous = offsets
        offsets = {}
        offset = HEADER_SIZE
        for node in nodes:
            offsets[id(node)] = offset
            if not node.children:
                pointer_size = 0
            elif attempt == 0:
                pointer_size = 4
            else:
                child_offset = previous[id(node.child_list()[0])]
                pointer_size = 2 if child_offset <= 0xFFFF else 3 if child_offset <= 0xFFFFFF else 4
            offset += node_size(node, pointer_size)
        if offsets == previous:
            break
    else:
        raise ValueError("DTRI pointer layout did not converge")

    maximum = max(words.values())
    denominator = math.log(maximum + 1)
    output = bytearray(MAGIC + struct.pack(
        "<HHIIIB11x", VERSION, 0, len(nodes), len(words), min(maximum, 0xFFFFFFFF), len(root.children),
    ))
    for node in nodes:
        child_offset = offsets[id(node.child_list()[0])] if node.children else 0
        pointer_size = 0 if not node.children else 2 if child_offset <= 0xFFFF else 3 if child_offset <= 0xFFFFFF else 4
        flags = ({0: 0, 2: 1, 3: 2, 4: 3}[pointer_size] << 6)
        if len(node.chars) > 1:
            flags |= 0x20
        if node.terminal:
            flags |= 0x10
        output.append(flags)
        if len(node.chars) > 1:
            output.append(len(node.chars))
        for unit in node.chars:
            output.extend(struct.pack("<H", unit))
        if node.terminal:
            score = int(65535 * math.log(node.frequency + 1) / denominator)
            output.extend(struct.pack("<H", min(65535, max(0, score))))
        if node.children:
            output.append(len(node.children))
            output.extend(struct.pack("<I", child_offset)[:pointer_size])
    return bytes(output)


def inspect(data: bytes) -> dict[str, int]:
    if len(data) < HEADER_SIZE:
        raise ValueError("truncated DTRI header")
    magic, version, flags, node_count, word_count, maximum, root_count = struct.unpack(
        "<4sHHIIIB", data[:21],
    )
    if magic != MAGIC or version != VERSION or flags != 0 or root_count == 0:
        raise ValueError("invalid DTRI header")
    if any(data[21:HEADER_SIZE]):
        raise ValueError("non-zero DTRI reserved header bytes")
    seen: set[int] = set()
    node_ranges: list[tuple[int, int]] = []
    terminals = 0

    def read_list(offset: int, count: int, word_depth: int) -> None:
        nonlocal terminals
        if count == 0:
            raise ValueError("empty DTRI child list")
        previous_first_unit = -1
        for _ in range(count):
            start = offset
            if start in seen or start >= len(data):
                raise ValueError("invalid DTRI node offset")
            seen.add(start)
            flags_byte = data[offset]
            offset += 1
            if flags_byte & 0x0F:
                raise ValueError("non-zero DTRI reserved node flags")
            if flags_byte & 0x20:
                if offset >= len(data):
                    raise ValueError("truncated DTRI character count")
                char_count = data[offset]
                offset += 1
            else:
                char_count = 1
            if char_count == 0 or word_depth + char_count > MAX_WORD_CODE_UNITS:
                raise ValueError("invalid DTRI character count")
            if offset + char_count * 2 > len(data):
                raise ValueError("truncated DTRI characters")
            first_unit = int.from_bytes(data[offset:offset + 2], "little")
            if first_unit <= previous_first_unit:
                raise ValueError("DTRI siblings are not strictly sorted")
            previous_first_unit = first_unit
            offset += char_count * 2
            if flags_byte & 0x10:
                terminals += 1
                offset += 2
            pointer_size = (0, 2, 3, 4)[flags_byte >> 6]
            if pointer_size:
                if offset >= len(data):
                    raise ValueError("truncated DTRI child count")
                child_count = data[offset]
                offset += 1
                if offset + pointer_size > len(data):
                    raise ValueError("truncated DTRI child pointer")
                child_offset = int.from_bytes(data[offset:offset + pointer_size], "little")
                offset += pointer_size
                read_list(child_offset, child_count, word_depth + char_count)
            if offset > len(data):
                raise ValueError("truncated DTRI node")
            node_ranges.append((start, offset))
    read_list(HEADER_SIZE, root_count, 0)
    ordered_ranges = sorted(node_ranges)
    expected_start = HEADER_SIZE
    for start, end in ordered_ranges:
        if start != expected_start or end <= start:
            raise ValueError("DTRI nodes do not exactly cover the file")
        expected_start = end
    if (len(seen) != node_count or terminals != word_count or
            expected_start != len(data)):
        raise ValueError("DTRI header counts do not match structure")
    return {"words": word_count, "nodes": node_count, "maxFrequency": maximum, "bytes": len(data)}


def build(language: str, output: Path, cache_dir: Path) -> dict[str, object]:
    source, url = acquire_source(language, cache_dir)
    words = parse_csv(source)
    binary = serialize(words)
    stats = inspect(binary)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(binary)
    return {
        "language": language,
        "output": str(output),
        "sha256": sha256(binary),
        **stats,
        "source": {"url": url, "sha256": SOURCE_SHA256[language]},
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
