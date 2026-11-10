#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly notes="$script_dir/play-release-notes.sh"

repo="$(mktemp -d)"
trap 'rm -rf "${repo:?}"' EXIT
cd "$repo"
git init -q
git -c user.name=t -c user.email=t@t commit -q --allow-empty -m "Merge pull request"

tag() { git -c user.name=t -c user.email=t@t tag -a "$1" -m "$2"; }

assert_note() {
    local tag="$1" language="$2" expected="$3" actual
    actual="$("$notes" "$tag" 2>/dev/null | jq -r --arg language "$language" '.[] | select(.language == $language) | .text')"
    [[ "$actual" == "$expected" ]] || {
        printf 'Unexpected %s note for %s:\n%s\n' "$language" "$tag" "$actual" >&2
        exit 1
    }
}

generic_fr="Dictus 1.0.0 : améliorations et corrections. Détails : https://github.com/getdictus/dictus-android/releases/tag/v1.0.0"
generic_en="Dictus 1.0.0: improvements and fixes. Details: https://github.com/getdictus/dictus-android/releases/tag/v1.0.0"

# A lightweight tag has no notes.
git tag v1.0.0
assert_note v1.0.0 fr-FR "$generic_fr"
assert_note v1.0.0 en-US "$generic_en"

tag v2.0.0 $'Release\n\n[fr-FR]\n\nDes corrections.\nEt « un test ».\n\n[en-US]\nSome fixes.\n'
assert_note v2.0.0 fr-FR $'Des corrections.\nEt « un test ».'
assert_note v2.0.0 en-US 'Some fixes.'

# Accented characters count once, so exactly 500 of them fit; 501 fall back.
fits="$(printf 'é%.0s' {1..500})"
tag v3.0.0 "[fr-FR]
$fits
[en-US]
ok"
assert_note v3.0.0 fr-FR "$fits"

tag v1.0.1 "[fr-FR]
é$fits
[en-US]
ok"
assert_note v1.0.1 fr-FR "Dictus 1.0.1 : améliorations et corrections. Détails : https://github.com/getdictus/dictus-android/releases/tag/v1.0.1"
assert_note v1.0.1 en-US ok

printf 'play-release-notes tests passed\n'
