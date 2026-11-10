#!/usr/bin/env bash
# Fetches the app icons of the ribbon on slides 1 and 2, as the iOS set does
# (getdictus/dictus-ios, assets/appstore/tools/fetch-icons.sh), from Apple's public
# iTunes lookup API. They are third-party trademarks: they land in .icons/, git-ignored,
# and each machine that renders the images fetches its own copy.
set -euo pipefail

OUT="$(cd "$(dirname "$0")" && pwd)/.icons"
mkdir -p "$OUT"

# slug:App Store id. Only apps that exist on Android: no Apple Messages, Mail or Notes.
APPS=(
  "gmail:422689480"
  "whatsapp:310633997"
  "chatgpt:6448311069"
  "messenger:454638411"
  "gemini:6477489729"
  "notion:1232780281"
  "slack:618783545"
  "instagram:389801252"
  "telegram:686449807"
)

for entry in "${APPS[@]}"; do
  slug="${entry%%:*}"; id="${entry##*:}"
  [ -s "$OUT/$slug.png" ] && continue
  url="$(curl -fsS "https://itunes.apple.com/lookup?id=$id&country=us" \
    | python3 -I -c 'import json,sys; r=json.load(sys.stdin)["results"]; print(r[0]["artworkUrl512"] if r else "")')"
  [ -n "$url" ] || { echo "No artwork for $slug ($id)" >&2; exit 1; }
  curl -fsS -o "$OUT/$slug.png" "${url/512x512bb/1024x1024bb}"
  echo "$slug"
done
