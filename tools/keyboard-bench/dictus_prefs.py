#!/usr/bin/env python3
"""
dictus_prefs.py -- Read or set one preference of the Dictus debug build (#382).

The replay types French on the French keyboard and English on the English one, and the
Dictus keyboard reads its language from the app's DataStore (`keyboard_language` in
`files/datastore/dictus_prefs.preferences_pb`). This rewrites that one entry and keeps
every other entry byte for byte, so a shared emulator's settings stay as they were.
It needs a debuggable build (`run-as`) and stops the app first, so no process holds
the old file in memory.

A value is written as a string, or as a boolean with --bool: the spatial autocorrect switch
(#385), `debug_spatial_autocorrect`, is one.

Usage:
    python3 -I tools/keyboard-bench/dictus_prefs.py --serial emulator-5554 get keyboard_language
    python3 -I tools/keyboard-bench/dictus_prefs.py --serial emulator-5554 set keyboard_language en
    python3 -I tools/keyboard-bench/dictus_prefs.py --serial emulator-5554 set --bool debug_spatial_autocorrect true
"""

import argparse
import subprocess

PACKAGE = "dev.pivisolutions.dictus"
PATH = "files/datastore/dictus_prefs.preferences_pb"


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def read_varint(data, i):
    shift = value = 0
    while True:
        b = data[i]
        i += 1
        value |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            return value, i


def fields(data):
    """(field number, value, raw field bytes) for one message level."""
    i = 0
    while i < len(data):
        start = i
        tag, i = read_varint(data, i)
        number, wire = tag >> 3, tag & 7
        if wire == 2:
            length, i = read_varint(data, i)
            value = data[i:i + length]
            i += length
        elif wire == 0:
            value, i = read_varint(data, i)
        elif wire == 5:
            value = data[i:i + 4]
            i += 4
        elif wire == 1:
            value = data[i:i + 8]
            i += 8
        else:
            raise SystemExit(f"unexpected wire type {wire}")
        yield number, value, data[start:i]


def entries(data):
    """PreferenceMap: field 1 is a map entry {1: name, 2: Value}. Yields (name, entry, raw)."""
    for number, value, raw in fields(data):
        if number == 1:
            name = next(v for n, v, _ in fields(value) if n == 1).decode()
            yield name, value, raw


def value_of(entry):
    value = next(v for n, v, _ in fields(entry) if n == 2)
    for n, v, _ in fields(value):
        if n == 5:                              # Value.string = 5
            return v.decode()
        if n == 1:                              # Value.boolean = 1
            return "true" if v else "false"
    return None


def preference_entry(name, value):
    """value is the encoded Value message."""
    k = name.encode()
    entry = b"\x0a" + varint(len(k)) + k + b"\x12" + varint(len(value)) + value
    return b"\x0a" + varint(len(entry)) + entry


def string_value(text):
    t = text.encode()
    return b"\x2a" + varint(len(t)) + t


def bool_value(text):
    if text not in ("true", "false"):
        raise SystemExit("a boolean is true or false")
    return b"\x08" + (b"\x01" if text == "true" else b"\x00")


def adb(serial, *args, data=None):
    return subprocess.run(["adb", "-s", serial, *args], input=data, capture_output=True, check=True).stdout


def read_prefs(serial):
    """The DataStore file, or nothing when the app has never written one (a fresh install)."""
    done = subprocess.run(["adb", "-s", serial, "exec-out", "run-as", PACKAGE, "cat", PATH], capture_output=True)
    if done.returncode == 0:
        return done.stdout
    if b"No such file" in done.stderr + done.stdout:
        return b""
    raise SystemExit(f"cannot read {PATH}: {(done.stderr or done.stdout).decode(errors='replace').strip()}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("action", choices=["get", "set"])
    parser.add_argument("--bool", action="store_true", help="write the value as a boolean")
    parser.add_argument("name")
    parser.add_argument("value", nargs="?")
    args = parser.parse_args()

    data = read_prefs(args.serial)
    current = {name: (entry, raw) for name, entry, raw in entries(data)}
    if args.action == "get":
        print(value_of(current[args.name][0]) if args.name in current else "(unset)")
        return
    if args.value is None:
        raise SystemExit("set needs a value")
    kept = b"".join(raw for name, (_, raw) in current.items() if name != args.name)
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    adb(args.serial, "exec-in", "run-as", PACKAGE, "sh", "-c", f"mkdir -p {PATH.rsplit('/', 1)[0]} && cat > {PATH}",
        data=kept + preference_entry(args.name, (bool_value if args.bool else string_value)(args.value)))
    print(f"{args.name} = {args.value}")


if __name__ == "__main__":
    main()
