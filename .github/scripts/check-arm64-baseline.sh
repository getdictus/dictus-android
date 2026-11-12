#!/usr/bin/env bash
# Fails when the default arm64 Whisper library, or a library it loads, holds an
# instruction newer than ARMv8.0 (#322).
#
# WHY: the app loads libwhisper.so on arm64 CPUs without FP16 (#321). An ARMv8.1+
# instruction in it or in its ggml libraries is a SIGILL waiting on an ARMv8.0 phone.
#
# How: an instruction that llvm-objdump decodes with every extension (+all) but not
# with the base ISA (+v8a) needs ARMv8.1 or later. Two cases are skipped, both
# guarded at run time and statically linked by the NDK into every library:
# - the __aarch64_* outline-atomic helpers, which test the CPU before using LSE;
# - the MTE stg of libunwind, only run for frames built with stack tagging.
#
# Usage: check-arm64-baseline.sh <dir of unstripped arm64-v8a libs> [root lib]
# NDK llvm tools are found through ANDROID_NDK_HOME, or the newest NDK in ANDROID_HOME.
set -euo pipefail

readonly lib_dir="${1:?usage: $0 <arm64-v8a lib dir> [root lib]}"
readonly root_lib="${2:-libwhisper.so}"

find_llvm_bin() {
    local ndk="${ANDROID_NDK_HOME:-}"
    if [[ -z "$ndk" ]]; then
        local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
        [[ -n "$sdk" ]] || { echo "Set ANDROID_NDK_HOME or ANDROID_HOME" >&2; exit 2; }
        ndk="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1)"
    fi
    local bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    [[ -x "$bin/llvm-objdump" ]] || { echo "llvm-objdump not found under $ndk" >&2; exit 2; }
    echo "$bin"
}

readonly llvm_bin="$(find_llvm_bin)"

# A tool failing inside a process substitution or a $(...) would go unnoticed and read
# as "no instruction found", so every tool's status is checked explicitly.
work="$(mktemp -d)"
trap 'rm -rf "${work:?}"' EXIT

# Libraries the root library loads, itself included, that ship next to it. System
# libraries (libc, liblog, libandroid...) are not in lib_dir and are not checked.
loaded_libs() {
    local queue=("$root_lib") seen=" " lib needed dynamic
    while ((${#queue[@]})); do
        lib="${queue[0]}"
        queue=("${queue[@]:1}")
        [[ "$seen" == *" $lib "* ]] && continue
        seen+="$lib "
        echo "$lib"
        dynamic="$("$llvm_bin/llvm-readelf" --dynamic "$lib_dir/$lib")" || return 2
        for needed in $(sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' <<< "$dynamic"); do
            if [[ -f "$lib_dir/$needed" ]]; then queue+=("$needed"); fi
        done
    done
}

# One line per instruction: "<function>\t<address>\t<instruction>".
disassemble() {
    local lib="$1" features="$2"
    "$llvm_bin/llvm-objdump" -d --no-show-raw-insn --mattr="$features" "$lib" |
        awk '
            /^[0-9a-f]+ <.*>:$/ { fn = $0; sub(/^[0-9a-f]+ </, "", fn); sub(/>:$/, "", fn); next }
            /^ *[0-9a-f]+:/ { addr = $1; $1 = ""; sub(/^ +/, ""); print fn "\t" addr "\t" $0 }'
}

# Prints "<function> <address> <instruction>" for every instruction beyond ARMv8.0.
newer_than_v80() {
    local lib="$1"
    disassemble "$lib" +v8a > "$work/v8a" || return 2
    disassemble "$lib" +all > "$work/all" || return 2
    [[ -s "$work/all" ]] || { echo "no instruction disassembled in $lib" >&2; return 2; }
    paste "$work/v8a" "$work/all" |
        awk -F '\t' '
            $2 != $5 { print "disassemblies out of step at " $2 " / " $5 > "/dev/stderr"; exit 2 }
            $1 ~ /^__aarch64_/ { next }
            $1 ~ /^_ZN9libunwind/ && $6 ~ /^stg / { next }
            $3 == "<unknown>" && $6 != "<unknown>" { print $1, $2, $6 }'
}

[[ -f "$lib_dir/$root_lib" ]] || { echo "$lib_dir/$root_lib not found" >&2; exit 2; }

libs="$(loaded_libs)" || { echo "could not read the dependencies of $root_lib" >&2; exit 2; }
status=0
for lib in $libs; do
    found="$(newer_than_v80 "$lib_dir/$lib")" || { echo "could not disassemble $lib" >&2; exit 2; }
    if [[ -n "$found" ]]; then
        count="$(wc -l <<< "$found")"
        echo "FAIL $lib: $count instruction(s) newer than ARMv8.0"
        awk '{ print $1 }' <<< "$found" | sort | uniq -c | sort -rn | sed 's/^/    /'
        status=1
    else
        echo "ok   $lib: ARMv8.0 only"
    fi
done
exit "$status"
