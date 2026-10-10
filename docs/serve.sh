#!/usr/bin/env bash
# Serves the Zensical documentation site locally.
# Set BUILD_ONLY=true to run only the strict build check.
# Prerequisites: pip install zensical

set -euo pipefail

SCRIPT_DIR="$(realpath "$(dirname $0)")"

cd "$SCRIPT_DIR"

# Install dependencies if a requirements file is present, otherwise fall back
# to installing the known required package.
if [[ -f requirements.txt ]]; then
	pip install -q -r requirements.txt
else
	pip install -q zensical
fi

python -m zensical build --strict --clean --config-file zensical.toml

if [[ "${BUILD_ONLY:-false}" == "true" ]]; then
	echo "Zensical build completed successfully. BUILD_ONLY=true, skipping dev server."
	exit 0
fi

echo "Starting Zensical dev server at http://127.0.0.1:8000 ..."
python -m zensical serve --config-file zensical.toml
