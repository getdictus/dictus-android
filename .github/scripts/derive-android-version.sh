#!/usr/bin/env bash
# Derives the Android versionName and versionCode from git tags. The tag is the only
# source of truth: nothing in Gradle is bumped by hand.
#
#   derive-android-version.sh vX.Y.Z[-beta.N]   release mode (release.yml): the tag itself
#   derive-android-version.sh --dev             every other build (app/build.gradle.kts, #165)
#
# Release versionCode = major * 10000000 + minor * 100000 + patch * 1000 + channel,
# where channel is the beta number (1-998) or 999 for a stable release.
#
# Dev mode picks the highest release tag whose changes are all in HEAD, then prints:
#   version_name = <tag>-<N>-g<sha>[-dirty], N = commits in HEAD that are not in the tag
#   version_code = release code + 1 after a stable tag, release code after a beta tag
# Release code + 1 lands on channel 000 of the next patch, a slot no release uses (betas
# start at 1), so a dev APK installs over the last release and every later release
# installs over it. Right after a beta there is no free slot (the next beta is +1), so the
# dev APK reuses the beta's code, which Android still accepts as an update.
# "Changes all in HEAD" means no non-merge commit of the tag is missing from HEAD: release
# tags sit on the main merge commit, which develop never reaches, so plain reachability
# (git describe) would report the release before.
# Without git, or without any release tag (a shallow CI checkout), dev mode prints
# 0.0.0-unknown or 0.0.0-dev+g<sha> with version_code 1, warns on stderr and exits 0.
set -euo pipefail

readonly pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-beta\.([1-9][0-9]*))?$'

# Prints the release versionCode of a tag, or explains on stderr and fails.
release_code() {
    local tag="$1"

    if [[ ! "$tag" =~ $pattern ]]; then
        printf 'Unsupported release tag: %s\n' "$tag" >&2
        return 1
    fi

    local major="${BASH_REMATCH[1]}"
    local minor="${BASH_REMATCH[2]}"
    local patch="${BASH_REMATCH[3]}"
    local beta_number="${BASH_REMATCH[5]:-}"

    if (( ${#major} > 3 || ${#minor} > 2 || ${#patch} > 2 || ${#beta_number} > 3 )); then
        printf 'Release tag contains an out-of-range numeric component: %s\n' "$tag" >&2
        return 1
    fi

    if (( major > 209 || minor > 99 || patch > 99 )); then
        printf 'Major must be <= 209 and minor/patch must be <= 99: %s\n' "$tag" >&2
        return 1
    fi

    if [[ -n "$beta_number" ]] && (( beta_number > 998 )); then
        printf 'Beta number must be between 1 and 998: %s\n' "$tag" >&2
        return 1
    fi

    local channel_code=999
    if [[ -n "$beta_number" ]]; then
        channel_code="$beta_number"
    fi

    local version_code=$((major * 10000000 + minor * 100000 + patch * 1000 + channel_code))

    if (( version_code <= 0 || version_code > 2100000000 )); then
        printf 'Derived Android versionCode is out of range: %s\n' "$version_code" >&2
        return 1
    fi

    printf '%s\n' "$version_code"
}

dev_version() {
    local sha
    if ! sha="$(git rev-parse --short HEAD 2>/dev/null)"; then
        printf 'warning: no git revision available, using version 0.0.0-unknown\n' >&2
        printf 'version_name=0.0.0-unknown\nversion_code=1\n'
        return
    fi

    local dirty=""
    if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
        dirty="-dirty"
    fi

    local tag code best_tag="" best_code=0
    while IFS= read -r tag; do
        code="$(release_code "$tag" 2>/dev/null)" || continue
        (( code > best_code )) || continue
        [[ -z "$(git rev-list --no-merges -n 1 "HEAD..$tag" --)" ]] || continue
        best_tag="$tag"
        best_code="$code"
    done < <(git tag --list 'v*')

    if [[ -z "$best_tag" ]]; then
        printf 'warning: no release tag (vX.Y.Z) in this checkout, using version 0.0.0-dev+g%s\n' "$sha" >&2
        printf 'version_name=0.0.0-dev+g%s%s\nversion_code=1\n' "$sha" "$dirty"
        return
    fi

    local distance
    distance="$(git rev-list --count "$best_tag..HEAD" --)" || distance=0
    if (( best_code % 1000 == 999 )); then
        best_code=$((best_code + 1))
    fi

    printf 'version_name=%s-%s-g%s%s\n' "${best_tag#v}" "$distance" "$sha" "$dirty"
    printf 'version_code=%s\n' "$best_code"
}

if [[ "${1:-}" == "--dev" ]]; then
    dev_version
    exit 0
fi

readonly release_tag="${1:-}"
version_code="$(release_code "$release_tag")" || exit 1
readonly version_code
readonly version_name="${release_tag#v}"

printf 'version_name=%s\n' "$version_name"
printf 'version_code=%s\n' "$version_code"
