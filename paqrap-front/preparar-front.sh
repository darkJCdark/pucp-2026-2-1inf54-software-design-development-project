#!/usr/bin/env sh
set -eu

rm -f package-lock.json
npm install
npm run build

echo "Frontend preparado. Versiona package.json y el nuevo package-lock.json."
