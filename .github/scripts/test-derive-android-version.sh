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

release_code_of() {
    "$derive" "$1" | sed -n 's/version_code=//p'
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

# Dev mode (#165), against throwaway repositories laid out like develop and main.
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

commit() {
    git commit -q --allow-empty -m "$1"
}

assert_dev() {
    local expected_name="$1"
    local expected_code="$2"
    local actual
    actual="$("$derive" --dev 2>/dev/null)"
    [[ "$actual" == $'version_name='"$expected_name"$'\nversion_code='"$expected_code" ]] || {
        printf 'Unexpected dev output in %s (expected %s / %s):\n%s\n' "$PWD" "$expected_name" "$expected_code" "$actual" >&2
        exit 1
    }
}

cd "$work_dir"
assert_dev 0.0.0-unknown 1

git init -q -b develop repo
cd repo
commit first
sha="$(git rev-parse --short HEAD)"
assert_dev "0.0.0-dev+g$sha" 1
"$derive" --dev 2>&1 >/dev/null | grep -q 'no release tag'

git tag v1.1.0
sha="$(git rev-parse --short HEAD)"
assert_dev "1.1.0-0-g$sha" 10101000

commit second
commit third
sha="$(git rev-parse --short HEAD)"
assert_dev "1.1.0-2-g$sha" 10101000

touch untracked
assert_dev "1.1.0-2-g$sha-dirty" 10101000
rm untracked

# Release flow: develop is merged into main with a merge commit, and the tag goes on the
# merge, which develop never reaches. The release still counts as the base.
git checkout -q -b main v1.1.0
git merge -q --no-ff -m 'release 1.2.0' develop
git tag v1.2.0
git checkout -q develop
commit fourth
sha="$(git rev-parse --short HEAD)"
assert_dev "1.2.0-1-g$sha" 10201000

dev_code=10201000
(( dev_code > $(release_code_of v1.2.0) && dev_code < $(release_code_of v1.2.1-beta.1) && dev_code < $(release_code_of v1.3.0) ))

# A branch cut before the release does not contain its changes: it stays on 1.1.0.
git checkout -q -b old-branch v1.1.0
sha="$(git rev-parse --short HEAD)"
assert_dev "1.1.0-0-g$sha" 10101000

# A beta tag leaves no free code before the next beta: the dev build reuses its code.
git checkout -q develop
git tag v1.3.0-beta.2
commit fifth
sha="$(git rev-parse --short HEAD)"
assert_dev "1.3.0-beta.2-1-g$sha" 10300002

# Tags that are not release tags are ignored.
git tag v2.0
git tag v3.0.0-rc.1
assert_dev "1.3.0-beta.2-1-g$sha" 10300002

cd "$script_dir"
printf 'All dev-version tests passed.\n'
