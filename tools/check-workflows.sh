#!/bin/sh
# Workflow files have to parse before anything in them runs. This script is the
# machine check behind that, and it is called from three places:
#
#     .githooks/pre-commit        rejects a broken workflow file at commit time
#     tools/verify.sh             part of the full local gate
#     .github/workflows/ci.yml    re-checks on a clean machine
#
# Why it exists at all: when GitHub cannot parse a workflow file it fails the
# run in 0 s with "This run likely failed because of a workflow file issue" and
# runs no job - so the commit-message check, the tests, the lint and the nightly
# publish are all skipped silently. CI cannot be the gate for its own file:
# a broken ci.yml is exactly the file that never gets to run. That leaves the
# local hook as the only place this can be caught before the push.
#
# What is checked: the two mistakes that make the file be rejected outright and
# that no build can otherwise reveal - a mapping key defined twice among its
# siblings (`env:` given once before `outputs:` and once after), and a tab used
# for indentation. This is deliberately not a YAML parser and not actionlint:
# it is an indentation walk over the shapes these three files actually use.
#
# Usage:
#     tools/check-workflows.sh                 # every .github/workflows/*.yml
#     tools/check-workflows.sh <file>...       # only these files
set -e

if [ $# -eq 0 ]; then
    cd "$(git rev-parse --show-toplevel)"
    set -- .github/workflows/*.yml .github/workflows/*.yaml
fi

failed=0
checked=0

for file in "$@"; do
    # An unmatched glob stays literal, so *.yaml is skipped rather than reported.
    [ -f "$file" ] || continue
    checked=$((checked + 1))
    awk -v file="$file" '
        function closeDeeper(ind,    i) {
            # Dedenting past a mapping closes it; so does the next "-" item at
            # the same indent, which starts a sibling mapping of its own.
            for (i = ind + 1; i <= deepest; i++)
                if (i in scope) delete scope[i]
        }
        BEGIN { block = -1; deepest = 0; scopes = 0; bad = 0 }
        {
            line = $0
            sub(/\r$/, "", line)
            if (line ~ /^[ ]*$/) next

            match(line, /^ */)
            indent = RLENGTH
            rest = substr(line, indent + 1)

            # Inside a block scalar every deeper-indented line is content, not
            # structure: a shell script under `run: |` is full of colons.
            if (block >= 0) {
                if (indent > block) next
                block = -1
            }

            if (substr(rest, 1, 1) == "#") next
            if (rest ~ /^\t/) {
                printf "%s:%d: 缩进里有制表符，YAML 不接受\n", file, FNR > "/dev/stderr"
                bad = 1
                next
            }
            if (rest ~ /^(---|\.\.\.)/) { closeDeeper(-1); next }

            # "- key: value" opens an item mapping whose keys sit where the key
            # itself starts, so walk past the dash and keep the column.
            while (match(rest, /^-([ ]+|$)/)) {
                closeDeeper(indent)
                indent += RLENGTH
                rest = substr(rest, RLENGTH + 1)
            }
            if (rest == "") next

            colon = index(rest, ":")
            if (colon == 0) next
            after = substr(rest, colon + 1)
            if (after != "" && after !~ /^[ \t]/) next   # "a:b" is a value

            key = substr(rest, 1, colon - 1)
            sub(/[ \t]+$/, "", key)
            if (key == "") next

            if (indent > deepest) deepest = indent
            closeDeeper(indent)
            if (!(indent in scope)) scope[indent] = ++scopes
            where = scope[indent] SUBSEP key
            if (where in seen) {
                printf "%s:%d: %s 已经在第 %d 行定义过了\n", file, FNR, key, seen[where] > "/dev/stderr"
                bad = 1
            } else {
                seen[where] = FNR
            }

            # `|`, `>`, `|-`, `>2` ... open a block scalar; anything else is a
            # value or a nested mapping, both of which stay structure.
            value = after
            sub(/^[ \t]+/, "", value)
            if (value ~ /^[|>][0-9]*[+-]?[ \t]*(#.*)?$/) block = indent
        }
        END { exit bad }
    ' "$file" || failed=1
done

if [ "$failed" -ne 0 ]; then
    echo "workflow 文件有问题，GitHub 会直接拒绝它 —— 那样整次运行 0 秒失败，" >&2
    echo "提交信息检查、测试、lint 和 nightly 发布都不会跑。修好再提交。" >&2
    exit 1
fi
echo "workflow 文件检查通过（$checked 个）"
