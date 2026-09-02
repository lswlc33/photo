#!/bin/sh
# Full local gate: what the pre-commit hook skips for speed.
#
# Run before cutting a release, or after a change wide enough that the
# per-commit test run is not reassuring on its own.
set -e

cd "$(git rev-parse --show-toplevel)"

echo "==> :app:test"
./gradlew :app:test --console=plain

echo
echo "==> :app:lint"
./gradlew :app:lint --console=plain

echo
echo "==> :app:assembleDebug"
./gradlew :app:assembleDebug --console=plain

echo
echo "all checks passed"
