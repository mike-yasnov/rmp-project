#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "error: required command not found: $cmd" >&2
    exit 1
  fi
}

require_cmd node
require_cmd chromium

cd "$ROOT_DIR"

echo "==> Rendering presentation HTML and PDF"
node docs/presentation/render-presentation.mjs

echo "==> Done"
echo "HTML: docs/presentation/presentation.html"
echo "PDF:  docs/presentation/HighLoad_Invest_presentation.pdf"
