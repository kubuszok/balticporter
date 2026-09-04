#!/bin/bash
# comment-lint.sh [git-range] — fail on comment blocks longer than 5 lines (whole tree, or the range's changed files)
# and on narrative words in the range's ADDED comment lines (CLAUDE.md §7).
set -u
cd "$(dirname "$0")/.." || exit 1
range="${1:-}"; bad=0
if [ -n "$range" ]; then
  # range mode: only comment blocks made of ADDED lines, and narrative words in added comment lines
  narrative=$(git diff "$range" -- '*.scala' | grep -E '^\+\s*(//|\*|/\*)' | grep -viE 'ENGINE-LIMITS|CLAUDE\.md|DESIGN\.md|subplan' \
    | grep -iE '\b(wave [0-9]|previously|used to (be|match|cost|fire|refuse|drop|emit|read)|measured at|the earlier|the first time|initially|the prior one)\b' || true)
  [ -n "$narrative" ] && { echo "!! narrative in added comments:"; echo "$narrative"; bad=1; }
  blocks=$(git diff -U0 "$range" -- '*.scala' | awk '
    /^\+\+\+ / { f=substr($0,7); n=0; next }
    /^@@/ { match($0, /\+[0-9]+/); ln=substr($0, RSTART+1, RLENGTH-1)+0; n=0; next }
    /^\+/ { s=substr($0,2); sub(/^[ \t]+/,"",s); if (s ~ /^(\/\/|\/\*|\*)/) { if (!n) start=ln; n++ } else { if (n>5) printf "%s:%d: added comment block of %d lines\n", f, start, n; n=0 }; ln++; next }
    { if (n>5) printf "%s:%d: added comment block of %d lines\n", f, start, n; n=0 }
    END { if (n>5) printf "%s:%d: added comment block of %d lines\n", f, start, n }')
  [ -n "$blocks" ] && { echo "!! added comment blocks over 5 lines:"; echo "$blocks"; bad=1; }
else
  files=$(find balticporter -name '*.scala' -not -path '*/target/*')
  blocks=""
  for f in $files; do
    [ -f "$f" ] || continue
    b=$(awk -v f="$f" '{ s=$0; sub(/^[ \t]+/,"",s); c = (s ~ /^(\/\/|\/\*|\*)/) }
         c { if (!n) start=NR; n++ } !c { if (n>5) printf "%s:%d: comment block of %d lines\n", f, start, n; n=0 }
         END { if (n>5) printf "%s:%d: comment block of %d lines\n", f, start, n }' "$f")
    [ -n "$b" ] && blocks="$blocks$b"$'\n'
  done
  [ -n "$blocks" ] && { echo "!! comment blocks over 5 lines:"; printf '%s' "$blocks"; bad=1; }
fi
exit $bad
