#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
image="${1:-$(cat .rollback-image)}"
test -n "$image"
bash scripts/deploy.sh "$image"
