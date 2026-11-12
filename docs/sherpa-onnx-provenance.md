# sherpa-onnx provenance

Parakeet runs on [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache 2.0). The `:asr` module vendors its prebuilt Android libraries and its Kotlin API unchanged, from one upstream release. This file records which one, so the next upgrade can check the files against their source.

## Version

**sherpa-onnx v2.0.1** (ONNX Runtime 1.28.2), from 2026-10-26. It replaced v1.12.34 (ONNX Runtime 1.23.2) in #338, to get the CPU memory arena setting of k2-fsa/sherpa-onnx#3503 (1.12.39 and later) that #326 needs.

## Native libraries

Archive: [`sherpa-onnx-v2.0.1-android.tar.bz2`](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android.tar.bz2), 45,300,379 bytes, sha256 `39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330` (the digest GitHub lists for the release asset). The files below are copied from its `jniLibs/` byte for byte; the C and C++ API libraries it also holds are not used.

| File in the repo | Bytes | sha256 |
|---|---:|---|
| `asr/src/main/jniLibs/arm64-v8a/libonnxruntime.so` | 22,249,560 | `33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1` |
| `asr/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` | 4,609,320 | `5db8f89f12b2c900a98c9e01e00c475c59db35e9437a62e36e27839ec48b5575` |
| `asr/src/main/jniLibs/armeabi-v7a/libonnxruntime.so` | 15,359,592 | `7a8f17a707e52e0ac3a026be319272876d9eae61f4bb19585292772d785f7744` |
| `asr/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so` | 3,381,796 | `d45f377249d5c3191b91de6ba6edffcd83e4cf7e109bc523b212db341581c2a8` |
| `asr/src/emulator/jniLibs/x86_64/libonnxruntime.so` | 25,581,120 | `087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e` |
| `asr/src/emulator/jniLibs/x86_64/libsherpa-onnx-jni.so` | 5,053,592 | `878d61116f244131263eec24297ef05bf508d02bccdede2a4e3ad3202bbdaa48` |

The 64-bit libraries have 16 KB LOAD segments (`checkDebugApkNativeAlignment`). The x86_64 pair is packaged only in debug builds made with `-Pdictus.emulatorAbi=true` (see `asr/build.gradle.kts`).

## Kotlin API

`asr/src/main/java/com/k2fsa/sherpa/onnx/` holds `sherpa-onnx/kotlin-api/{OfflineRecognizer,OfflineStream,FeatureConfig,HomophoneReplacerConfig,QnnConfig}.kt` from tag `v2.0.1` (commit `af2d8d81dbfa61c4cea4c568da78f3982edca005`), unchanged. The JNI library reads the config classes' fields by name, so these files and the libraries must come from the same release.

## Checking

Run from an empty directory, with `REPO` set to the repository root:

```bash
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android.tar.bz2
echo "39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330  sherpa-onnx-v2.0.1-android.tar.bz2" | sha256sum -c -
tar xjf sherpa-onnx-v2.0.1-android.tar.bz2
for abi in arm64-v8a armeabi-v7a; do
  for lib in libonnxruntime libsherpa-onnx-jni; do cmp jniLibs/$abi/$lib.so "$REPO/asr/src/main/jniLibs/$abi/$lib.so"; done
done
for lib in libonnxruntime libsherpa-onnx-jni; do cmp jniLibs/x86_64/$lib.so "$REPO/asr/src/emulator/jniLibs/x86_64/$lib.so"; done
```

## Upgrading

Take the libraries and the Kotlin files from the same release, update the tables above, and compare Parakeet's output on the shared voice corpus (`getdictus/dictus-test-voices`) before and after, as #338 did.
