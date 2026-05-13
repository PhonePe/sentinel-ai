#!/usr/bin/env bash
# Serves the MkDocs documentation site locally.
# Prerequisites: pip install mkdocs-material mkdocs-glightbox mkdocs-awesome-pages-plugin

set -euo pipefail

SCRIPT_DIR="$(realpath "$(dirname $0)")"

cd "$SCRIPT_DIR"

# Install dependencies if a requirements file is present, otherwise fall back
# to installing the known required packages.
if [[ -f requirements.txt ]]; then
  pip install -q -r requirements.txt
else
  pip install -q \
    mkdocs-material \
    mkdocs-glightbox \
    mkdocs-awesome-pages-plugin \
    markdown-include \
    pymdown-extensions \
    mkdocs-material-extensions \
    mkdocs-get-deps \
    mkdocs-nav-weight \
    mkdocs-pdf-export-plugin
fi

echo "Starting MkDocs dev server at http://127.0.0.1:8000 ..."
mkdocs build --strict --theme material
mkdocs serve --strict --livereload

