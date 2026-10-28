#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v node >/dev/null 2>&1; then
  exec node "$script_dir/trefle.mjs"
fi
runtime_node="$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
if [ -x "$runtime_node" ]; then exec "$runtime_node" "$script_dir/trefle.mjs"; fi
printf '%s\n' 'Install Node.js 20 or newer, then run this script again.' >&2
exit 1
