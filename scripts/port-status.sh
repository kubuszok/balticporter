#!/usr/bin/env bash
# port-status.sh — one row per port, read from the COMMITTED baselines (never from a run in
# progress): module, errors per platform, tests, failing/skipped names, declared exceptions,
# last baseline date, live/frozen. Mechanism only; the wording is the `port-status` skill's.
cd "$(dirname "$0")/.." || exit 1
# POLICY: the reports the loop still measures (PROGRESS.md §13.29 standing order 5). Everything
# else is the frozen family on the OLD full-policy core; update this list when a port moves.
LIVE="LlsMigrate LlsDifferential LibgdxL0Migrate LibgdxL0TestMigrate DemoCheck DemoRun"
printf "report\tmodule\tstatus\tjvm\tjs\tnative\tref-suite\ttests\tpass\tfail\tskipped\tfailing\tskipped-names\tdeclared-failures\tdeclared-lost\truns\tlast-baseline\n"
for b in port-report/*/baseline; do
  r=$(basename "$(dirname "$b")")
  mod=$(grep -m1 -oE "module=[^	 ]+" "$b/port-map.tsv" 2>/dev/null | cut -d= -f2); [ -n "$mod" ] || mod=-
  st=frozen; for l in $LIVE; do [ "$l" = "$r" ] && st=live; done
  n() { [ -f "$1" ] && tr -d ' \n' < "$1" || echo -; }
  jvm=$(n "$b/expected-errors"); js=$(n "$b/expected-errors.js"); nat=$(n "$b/expected-errors.native"); ref=$(n "$b/expected-errors.ref")
  [ -f "$b/expected-errors.suite" ] && ref="suite:$(tr -d ' \n' < "$b/expected-errors.suite")"
  if [ -f "$b/tests.tsv" ]; then
    t=$(($(wc -l < "$b/tests.tsv")-1))
    p=$(awk -F'\t' 'NR>1 && $3=="pass"' "$b/tests.tsv" | wc -l | tr -d ' ')
    f=$(awk -F'\t' 'NR>1 && $3=="fail"' "$b/tests.tsv" | wc -l | tr -d ' ')
    s=$(awk -F'\t' 'NR>1 && $3!="pass" && $3!="fail"' "$b/tests.tsv" | wc -l | tr -d ' ')
    fn=$(awk -F'\t' 'NR>1 && $3=="fail"{sub(/.*\./,"",$1); print $1"."$2}' "$b/tests.tsv" | paste -sd';' -)
    sn=$(awk -F'\t' 'NR>1 && $3!="pass" && $3!="fail"{sub(/.*\./,"",$1); print $1"."$2" ("$3")"}' "$b/tests.tsv" | paste -sd';' -)
  else t=-; p=-; f=-; s=-; fn=-; sn=-; fi
  df=$( [ -f "$b/expected-failures.tsv" ] && grep -vE '^\s*#|^\s*$' "$b/expected-failures.tsv" | wc -l | tr -d ' ' || echo -)
  dl=$( [ -f "$b/expected-lost" ] && grep -vE '^\s*#|^\s*$' "$b/expected-lost" | head -1 || echo -)
  # runs: does a demo RUN on this module's stack? Read from DemoRun's baseline (frames rendered, or fail);
  # only the port the demo-run lane launches against carries a value.
  runs=-
  if [ -f port-report/DemoRun/baseline/counts.tsv ] && { [ "$r" = "DemoRun" ] || [ "$mod" = "sge-l0" ]; }; then
    runs=$(awk -F'\t' 'NR>1{print ($3=="0" && $2!="0") ? "frames="$2 : "fail(exit="$3")"}' port-report/DemoRun/baseline/counts.tsv | head -1)
  fi
  last=$(git log -1 --format=%cs -- "$b" 2>/dev/null)
  for v in fn sn dl; do eval "$v=\$(printf '%s' \"\${$v}\" | tr -d '\n\r')"; done
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$r" "$mod" "$st" "$jvm" "$js" "$nat" "$ref" "$t" "$p" "$f" "$s" "${fn:--}" "${sn:--}" "$df" "$dl" "$runs" "$last"
done
