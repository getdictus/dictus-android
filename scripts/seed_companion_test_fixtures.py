"""Copy local synthetic/offline test fixtures into a debuggable emulator app, preserving bytes.

No network or Android privilege grants. Requires adb run-as (a debug APK) and explicit local files.
"""
import argparse
import pathlib
import subprocess
import hashlib
import base64


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="dev.pivisolutions.dictus", choices=[
        "dev.pivisolutions.dictus", "dev.pivisolutions.dictus.companiontest"])
    parser.add_argument("--model", type=pathlib.Path, required=True)
    parser.add_argument("--audio", type=pathlib.Path, required=True)
    args = parser.parse_args()
    for source in (args.model, args.audio):
        if not source.is_file():
            parser.error(f"Fixture not found: {source}")
    # -T prevents terminal line discipline from interpreting binary control bytes (notably EOT).
    prefix = [args.adb, "-s", args.serial, "shell", "-T"]
    package = args.package
    subprocess.run(prefix + [f"run-as {package} mkdir -p files/models files/companion-tests"], check=True)
    for source, destination in (
        (args.model, "files/models/ggml-tiny.bin"),
        (args.audio, "files/companion-tests/jfk.wav"),
    ):
        # Some Windows adb builds open stdin in text mode, treating 0x1a as EOF even with -T.
        # Stream ASCII base64 so the fixture survives both host and remote terminal handling.
        process = subprocess.Popen(prefix + [f"run-as {package} sh -c 'base64 -d > {destination}'"], stdin=subprocess.PIPE)
        try:
            with source.open("rb") as stream:
                while chunk := stream.read(3 * 32768):
                    process.stdin.write(base64.b64encode(chunk))
            process.stdin.close()
            if process.wait() != 0:
                raise RuntimeError(f"Fixture transfer failed: {source.name}")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
        remote_hash = subprocess.check_output(prefix + [f"run-as {package} sha256sum {destination}"], text=True).split()[0]
        with source.open("rb") as stream:
            expected_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        if remote_hash != expected_hash:
            raise RuntimeError(f"Fixture transfer checksum mismatch: {source.name}")
        print(f"Copied {source.name} ({source.stat().st_size} bytes) to {destination}")


if __name__ == "__main__":
    main()
