#!/bin/sh
# Checks that every commit in a range builds and passes its tests on its own.
#
# master is pushed as-is, so a commit that does not build is only discovered by
# bisecting into it later - CI checks the tip, not the steps that led to it. Run
# this after rewriting or splitting history, and before cutting a release.
#
# Usage:
#     tools/verify-history.sh            # whole history
#     tools/verify-history.sh <base>     # <base>..HEAD only
#
# Non-destructive by design: every build happens in a scratch worktree, so
# neither the working tree nor the commits themselves are touched. `git rebase
# --exec` would do the same job but rewrites the commits it checks, which is the
# last thing you want in a repository whose only copy is this one.
#
# The scratch worktree is reused across commits so its build/ directory keeps
# builds incremental; expect the first commit to be slow and the rest to be fast.
set -e

cd "$(git rev-parse --show-toplevel)"

base="$1"
if [ -n "$base" ]; then
    range="$base..HEAD"
else
    range="HEAD"
fi

commits=$(git rev-list --reverse "$range")
if [ -z "$commits" ]; then
    echo "no commits to verify in $range"
    exit 0
fi

worktree="../photo-verify-worktree"
cleanup() {
    git worktree remove --force "$worktree" 2>/dev/null || true
}
trap cleanup EXIT

cleanup
git worktree add --detach "$worktree" HEAD >/dev/null

# local.properties holds the machine-local SDK path and is gitignored, so the
# scratch worktree needs a copy or Gradle cannot locate the Android SDK.
if [ -f local.properties ]; then
    cp local.properties "$worktree/local.properties"
fi

failed=""
for commit in $commits; do
    printf '\n=== %s\n' "$(git log -1 --format='%h %s' "$commit")"
    git -C "$worktree" checkout --quiet --detach "$commit"
    if (cd "$worktree" && ./gradlew :app:test --console=plain --quiet); then
        echo "    ok"
    else
        echo "    FAILED"
        failed="$failed $commit"
    fi
done

if [ -n "$failed" ]; then
    printf '\ncommits that do not build on their own:\n'
    for commit in $failed; do
        git log -1 --format='  %h %s' "$commit"
    done
    exit 1
fi

printf '\nevery commit in %s builds and passes tests\n' "$range"
