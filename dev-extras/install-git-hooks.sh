#!/bin/sh

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_DIR="${REPO_ROOT}/.git/hooks"
PRE_PUSH_HOOK="${HOOKS_DIR}/pre-push"

mkdir -p "${HOOKS_DIR}"

cat >"${PRE_PUSH_HOOK}" <<'EOF'
#!/bin/sh

set -e

repo_root="$(git rev-parse --show-toplevel)"

cd "$repo_root"

printf "Running Spotless check before push..."
mvn spotless:check
EOF

chmod +x "${PRE_PUSH_HOOK}"

printf '%s\n' "Installed pre-push hook at ${PRE_PUSH_HOOK}"
