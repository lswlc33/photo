#!/bin/sh
# Full local gate: what the pre-commit hook skips for speed.
#
# Run before cutting a release, or after a change wide enough that the
# per-commit test run is not reassuring on its own.
set -e

cd "$(git rev-parse --show-toplevel)"

echo "==> tools/check-workflows.sh"
tools/check-workflows.sh

echo
echo "==> :app:test"
./gradlew :app:test --console=plain

echo
echo "==> :app:lint"
./gradlew :app:lint --console=plain

echo
# The variant CI publishes, so an R8 or resource-shrinking failure surfaces here
# instead of after the push. It is also what the pre-commit hook cannot afford.
echo "==> :app:assembleNightly"
./gradlew :app:assembleNightly --console=plain

echo
echo "all checks passed"
