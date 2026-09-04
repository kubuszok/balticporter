#!/bin/bash
# comment-lint.sh [git-range] — fail on comment blocks longer than 5 lines (whole tree, or the range's changed files)
# and on narrative words in the range's ADDED comment lines (CLAUDE.md §7).
set -u
cd "$(dirname "$0")/.." || exit 1
range="${1:-}"; bad=0
if [ -n "$range" ]; then
  files=$(git diff --name-only "$range" -- '*.scala')
  narrative=$(git diff "$range" -- '*.scala' | grep -E '^\+\s*(//|\*|/\*)' | grep -viE 'ENGINE-LIMITS|CLAUDE\.md|DESIGN\.md|subplan' \
    | grep -iE '\b(wave [0-9]|previously|used to|measured at|the earlier|the first time|initially|no longer)\b' || true)
  [ -n "$narrative" ] && { echo "!! narrative in added comments:"; echo "$narrative"; bad=1; }
else
  files=$(find balticporter -name '*.scala' -not -path '*/target/*')
fi
blocks=""
for f in $files; do
  [ -f "$f" ] || continue
  b=$(awk -v f="$f" '{ s=$0; sub(/^[ \t]+/,"",s); c = (s ~ /^(\/\/|\/\*|\*)/) }
       c { if (!n) start=NR; n++ } !c { if (n>5) printf "%s:%d: comment block of %d lines\n", f, start, n; n=0 }
       END { if (n>5) printf "%s:%d: comment block of %d lines\n", f, start, n }' "$f")
  [ -n "$b" ] && blocks="$blocks$b"$'\n'
done
[ -n "$blocks" ] && { echo "!! comment blocks over 5 lines:"; printf '%s' "$blocks"; bad=1; }
exit $bad
