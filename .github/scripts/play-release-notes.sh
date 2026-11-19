#!/usr/bin/env bash
# Prints the Play release notes of a tag as the JSON array gplay reads, in French and English.
#
#   play-release-notes.sh vX.Y.Z[-beta.N] > release-notes.json
#
# The notes are the message of the annotated tag, one section per Play language:
#
#   [fr-FR]
#   Texte, 500 caractères au plus.
#   [en-US]
#   Text, 500 characters at most.
#
# Not "### Français" as in the GitHub release: `git tag -a` drops lines starting with #.
#
# A lightweight tag has no such message and Play refuses a note over 500 characters. Either
# way the note becomes a one-liner pointing at the GitHub release, with a CI warning: short
# notes on a testing track are not worth failing a release.
set -euo pipefail

readonly tag="$1"
readonly version="${tag#v}"
readonly limit=500
readonly release_url="https://github.com/getdictus/dictus-android/releases/tag/$tag"

# Prints what stands under a "[<language>]" line of the tag message, without surrounding blank lines.
section() {
    git tag --list --format='%(contents)' "$tag" \
        | awk -v heading="[$1]" '$0 == heading { on = 1; next } /^\[[a-z][a-z]-[A-Z][A-Z]\]$/ { on = 0 } on' \
        | sed -e '/./,$!d'
}

# Prints a section, or the fallback when it is missing or too long for Play.
note() {
    local language="$1" fallback="$2" text
    text="$(section "$language")"
    if [[ -z "$text" ]] || (( $(jq -Rs 'length' <<< "$text") > limit + 1 )); then
        printf '::warning::Using the generic %s Play note for %s\n' "$language" "$tag" >&2
        text="$fallback"
    fi
    printf '%s' "$text"
}

fr="$(note fr-FR "Dictus $version : améliorations et corrections. Détails : $release_url")"
en="$(note en-US "Dictus $version: improvements and fixes. Details: $release_url")"

jq -n --arg fr "$fr" --arg en "$en" \
    '[{language: "fr-FR", text: $fr}, {language: "en-US", text: $en}]'
