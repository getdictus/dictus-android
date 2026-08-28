#!/usr/bin/env bash
set -euo pipefail

readonly tag="${1:-}"
readonly pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-beta\.([1-9][0-9]*))?$'

if [[ ! "$tag" =~ $pattern ]]; then
    printf 'Unsupported release tag: %s\n' "$tag" >&2
    exit 1
fi

readonly major="${BASH_REMATCH[1]}"
readonly minor="${BASH_REMATCH[2]}"
readonly patch="${BASH_REMATCH[3]}"
readonly beta_number="${BASH_REMATCH[5]:-}"

if (( ${#major} > 3 || ${#minor} > 2 || ${#patch} > 2 || ${#beta_number} > 3 )); then
    printf 'Release tag contains an out-of-range numeric component: %s\n' "$tag" >&2
    exit 1
fi

if (( major > 209 || minor > 99 || patch > 99 )); then
    printf 'Major must be <= 209 and minor/patch must be <= 99: %s\n' "$tag" >&2
    exit 1
fi

if [[ -n "$beta_number" ]] && (( beta_number > 998 )); then
    printf 'Beta number must be between 1 and 998: %s\n' "$tag" >&2
    exit 1
fi

channel_code=999
if [[ -n "$beta_number" ]]; then
    channel_code="$beta_number"
fi

readonly version_name="${tag#v}"
readonly version_code=$((major * 10000000 + minor * 100000 + patch * 1000 + channel_code))

if (( version_code <= 0 || version_code > 2100000000 )); then
    printf 'Derived Android versionCode is out of range: %s\n' "$version_code" >&2
    exit 1
fi

printf 'version_name=%s\n' "$version_name"
printf 'version_code=%s\n' "$version_code"
