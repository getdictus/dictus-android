#!/usr/bin/env bash
# check-arm64-baseline.sh must pass ARMv8.0 libraries, outline atomics included, and
# fail on FP16, LSE and dot product instructions in a library the root one loads.
set -euo pipefail

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly check="$script_dir/check-arm64-baseline.sh"

ndk="${ANDROID_NDK_HOME:-$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME}}"/ndk/* | sort -V | tail -1)}"
readonly cc="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --target=aarch64-linux-android29 -O2 -shared -fPIC"

work="$(mktemp -d)"
trap 'rm -rf "${work:?}"' EXIT
mkdir "$work/good" "$work/bad"

cat > "$work/base.c" <<'EOF'
#include <stdatomic.h>
int base(_Atomic int *p) { return atomic_fetch_add(p, 1); }
EOF
cat > "$work/newer.c" <<'EOF'
#include <arm_neon.h>
#include <stdatomic.h>
float16x8_t fp16(float16x8_t a, float16x8_t b) { return vmulq_f16(a, b); }
int lse(_Atomic int *p) { return atomic_fetch_add(p, 1); }
int32x4_t dot(int32x4_t a, int8x16_t b, int8x16_t c) { return vdotq_s32(a, b, c); }
EOF
echo 'int base(void *); int root(void *p) { return base(p); }' > "$work/root.c"

# Default flags use outline atomics: LSE only behind a run-time check, so this one passes.
$cc "$work/base.c" -o "$work/good/libbase.so"
$cc "$work/root.c" -L"$work/good" -lbase -o "$work/good/libroot.so"
"$check" "$work/good" libroot.so

cp "$work/good/libbase.so" "$work/bad/"
$cc -march=armv8.2-a+fp16+dotprod -mno-outline-atomics "$work/newer.c" -o "$work/bad/libnewer.so"
$cc "$work/root.c" -L"$work/bad" -lbase -lnewer -o "$work/bad/libroot.so"
if output="$("$check" "$work/bad" libroot.so)"; then
    printf 'Expected a failure on libnewer.so:\n%s\n' "$output" >&2
    exit 1
fi
for fn in fp16 lse dot; do
    grep -Eq "^ +1 $fn$" <<< "$output" || { printf 'No instruction reported in %s:\n%s\n' "$fn" "$output" >&2; exit 1; }
done
echo "check-arm64-baseline.sh: controls ok"
