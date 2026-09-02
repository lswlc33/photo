#!/bin/sh
# Commit messages must be written in Chinese. This script is the machine check
# behind that rule, and it is called from two places:
#
#     .githooks/commit-msg        rejects a non-Chinese message at commit time
#     .github/workflows/ci.yml    re-checks the pushed range on a clean machine
#
# Why a script instead of two copies of the same test: a clone that never ran
# `git config core.hooksPath .githooks` has no local hook, so the rule also has
# to hold on the remote - and both sides must agree on what "Chinese" means.
#
# Usage:
#     tools/check-commit-language.sh --file <msgfile>      # one draft message
#     tools/check-commit-language.sh --range <base>..<head>
#     tools/check-commit-language.sh                       # HEAD only
#
# What is checked: the subject line contains at least one Han character. That is
# the part a machine can judge. AGENTS.md carries the intent - write the whole
# message in Chinese and leave identifiers, paths and commands verbatim.
set -e

# CJK ideographs: Extension A (3400-4DBF), the main block (4E00-9FFF) and the
# compatibility block (F900-FAFF). CJK punctuation is deliberately excluded: a
# subject made of nothing but a full-width comma is not a Chinese subject.
han_pcre='[\x{3400}-\x{4DBF}\x{4E00}-\x{9FFF}\x{F900}-\x{FAFF}]'

# GNU grep on the runners has -P, and Git for Windows' grep normally does too.
# Where it does not, fall back to "contains a byte outside printable ASCII":
# every Han character is multi-byte UTF-8, so an all-ASCII subject still fails.
if printf 'a' | grep -qP 'a' 2>/dev/null; then
    has_han() { printf '%s' "$1" | grep -qP "$han_pcre"; }
else
    has_han() { printf '%s' "$1" | LC_ALL=C grep -q '[^[:print:][:space:]]'; }
fi

explain() {
    echo "  提交信息必须用中文撰写（见 AGENTS.md 的 Commit Guidelines）。"
    echo "  标题用一句动词开头的中文短句，例如「修复仪表盘的空状态」或"
    echo "  「给媒体扫描加上权限状态」；标识符、路径、命令保留原文。"
}

failed=0

check_subject() {
    subject=$1
    label=$2
    case "$subject" in
        '')
            echo "提交信息是空的。" >&2
            explain >&2
            failed=1
            return
            ;;
        'Merge '*)
            # A merge message git wrote itself is out of scope for this rule.
            return
            ;;
    esac
    if has_han "$subject"; then
        return
    fi
    echo "提交信息的标题里没有中文（$label）：" >&2
    echo "  $subject" >&2
    explain >&2
    failed=1
}

check_file() {
    file=$1
    if [ ! -f "$file" ]; then
        echo "找不到提交信息文件：$file" >&2
        exit 1
    fi
    # git stripspace drops comment lines and blank padding; what is left of the
    # first line is the subject.
    subject=$(git stripspace --strip-comments < "$file" | sed -n '1{p;q;}')
    check_subject "$subject" "$file"
}

check_range() {
    commits=$(git rev-list --no-merges "$@")
    if [ -z "$commits" ]; then
        echo "范围内没有需要检查的提交"
        return
    fi
    for commit in $commits; do
        check_subject "$(git log -1 --format=%s "$commit")" "$(git log -1 --format=%h "$commit")"
    done
}

if [ "$1" = --file ]; then
    check_file "$2"
else
    [ "$1" = --range ] && shift
    [ $# -eq 0 ] && set -- -1 HEAD
    check_range "$@"
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "提交信息是中文，通过"
