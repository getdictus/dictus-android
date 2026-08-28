#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly derive="$script_dir/derive-android-version.sh"

assert_output() {
    local tag="$1"
    local expected_name="$2"
    local expected_code="$3"
    local actual
    actual="$($derive "$tag")"
    [[ "$actual" == $'version_name='"$expected_name"$'\nversion_code='"$expected_code" ]] || {
        printf 'Unexpected output for %s:\n%s\n' "$tag" "$actual" >&2
        exit 1
    }
}

assert_rejected() {
    local tag="$1"
    if "$derive" "$tag" >/dev/null 2>&1; then
        printf 'Expected rejection for %s\n' "$tag" >&2
        exit 1
    fi
}

assert_output v1.1.0 1.1.0 10100999
assert_output v1.2.0-beta.1 1.2.0-beta.1 10200001
assert_output v1.2.0-beta.2 1.2.0-beta.2 10200002
assert_output v1.2.0 1.2.0 10200999
assert_output v209.99.99 209.99.99 2099999999

beta_one="$($derive v1.2.0-beta.1 | sed -n 's/version_code=//p')"
beta_two="$($derive v1.2.0-beta.2 | sed -n 's/version_code=//p')"
final="$($derive v1.2.0 | sed -n 's/version_code=//p')"
(( beta_one > 10100 && beta_one < beta_two && beta_two < final ))

patch_boundary="$($derive v1.2.99 | sed -n 's/version_code=//p')"
next_minor="$($derive v1.3.0-beta.1 | sed -n 's/version_code=//p')"
major_boundary="$($derive v1.99.99 | sed -n 's/version_code=//p')"
next_major="$($derive v2.0.0-beta.1 | sed -n 's/version_code=//p')"
(( patch_boundary < next_minor && major_boundary < next_major ))

assert_rejected 1.2.0
assert_rejected v1.2
assert_rejected v1.2.0-rc.1
assert_rejected v1.2.0-beta.0
assert_rejected v1.2.0-beta.999
assert_rejected v1.100.0
assert_rejected v211.0.0
assert_rejected v210.0.0
assert_rejected v18446744073709551616.0.0
assert_rejected v1.2.0-beta.18446744073709551616

printf 'All release-version tests passed.\n'
