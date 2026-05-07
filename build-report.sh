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
require_cmd npx
require_cmd chromium
require_cmd pdftotext

cd "$ROOT_DIR"

echo "==> Building report markdown"
node docs/report/build.mjs

echo "==> Rendering report HTML and PDF"
node docs/report/render-html.mjs

echo "==> Done"
echo "Markdown: docs/report/full.md"
echo "HTML:     docs/report/full.html"
echo "PDF:      docs/report/HighLoad_Invest_report.pdf"
