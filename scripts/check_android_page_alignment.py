"""Verify ELF load-segment and uncompressed ZIP alignment for 64-bit Android libraries."""
import argparse
import struct
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    args = parser.parse_args()
    failed = False
    checked = 0
    with open(args.apk, "rb") as raw, zipfile.ZipFile(args.apk) as archive:
        for entry in archive.infolist():
            if not entry.filename.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not entry.filename.endswith(".so"):
                continue
            elf = archive.read(entry)
            checked += 1
            if elf[:6] != b"\x7fELF\x02\x01":
                raise ValueError(f"Expected little-endian ELF64: {entry.filename}")
            phoff = struct.unpack_from("<Q", elf, 32)[0]
            stride, count = struct.unpack_from("<HH", elf, 54)
            loads = []
            for index in range(count):
                start = phoff + index * stride
                if struct.unpack_from("<I", elf, start)[0] == 1:  # PT_LOAD
                    offset, address = struct.unpack_from("<QQ", elf, start + 8)
                    alignment = struct.unpack_from("<Q", elf, start + 48)[0]
                    loads.append(alignment >= 16384 and offset % 16384 == address % 16384)
            raw.seek(entry.header_offset)
            header = raw.read(30)
            name_size, extra_size = struct.unpack_from("<HH", header, 26)
            data_offset = entry.header_offset + 30 + name_size + extra_size
            zip_ok = entry.compress_type != zipfile.ZIP_STORED or data_offset % 16384 == 0
            elf_ok = bool(loads) and all(loads)
            passed = elf_ok and zip_ok
            failed |= not passed
            print(f"{'PASS' if passed else 'FAIL'} {entry.filename}: ELF={elf_ok}, ZIP={zip_ok}")
    if not checked:
        raise ValueError("APK contains no 64-bit native libraries")
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
