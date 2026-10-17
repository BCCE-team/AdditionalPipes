#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
if (( $# == 0 )); then
    args=(buildAndCollect)
else
    args=("$@")
fi
for generation in old 1.21.X 26.X; do
    echo "==> Additional Pipes ${generation}: ${args[*]}"
    (cd "${root}/builds/${generation}" && ./gradlew "${args[@]}")
done
