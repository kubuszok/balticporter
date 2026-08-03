#!/bin/bash
# THE CLOSED-TWIN DERIVATION — the cheapest half of a catalog status re-derivation, mechanised.
#
# WHY THIS EXISTS. Every row of the difference catalog carries a `status` (OPEN / PARTIAL /
# HANDLED / …) and a `twin` naming the `ENGINE-LIMITS.md` entry that measured it. A status is a
# claim about a moving target: four headline rows went from OPEN to fixed inside one week while the
# document describing them was being written, and nothing could see that they had. Every one of the
# four would have fallen out of ONE grep — "does this row's twin read CLOSED?" — which is what this
# script is.
#
# It is deliberately a script and not prose. A hand transcription of 130 statuses is a second thing
# that goes stale at the same rate as the first, so the re-derivation has to be repeatable by
# typing one command. `balticporter.catalog`'s status-enforcement spec automates exactly this rule
# in Scala, over the same file and the same ids; this script is the shell reading of it, for an
# agent that is holding a checkout rather than a test runner.
#
# WHAT IT CANNOT DO, said out loud: it sees a row only through its `twin`, so a row with no twin is
# invisible to it, and a twin that is CLOSED for one FACE of a difference and open for another
# reads as simply CLOSED. Those are the rows step (2) of the re-derivation exists for — re-read the
# cited SYMBOL in the engine, never the cited line — and this script is step (1).
#
# USAGE
#   scripts/catalog-status.sh                 # every ENGINE-LIMITS entry: id, verdict, heading
#   scripts/catalog-status.sh rows.tsv        # cross a catalog table against it; non-zero on a
#                                             # row whose twin is CLOSED and whose status is OPEN
#
#   rows.tsv is three tab-separated columns, `#` comments and blank lines ignored:
#       <catalog id>  <status>  <twin>
#   `twin` is an ENGINE-LIMITS id (`F5`, `C12`, `K5.6`), `-` for none, or `PREDICTED`.
#
# No `set -e`, for the reason `_lib.sh` states at length: `grep -c` exits 1 when it counts zero and
# counting zero is the success case.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIMITS="${LIMITS:-$ROOT/ENGINE-LIMITS.md}"

if [ ! -f "$LIMITS" ]; then
  echo "catalog-status: no such file: $LIMITS" >&2
  exit 2
fi

# ---------------------------------------------------------------------------------------------
# THE VERDICT, derived from the file's own shape.
#
# An entry is `### <ID>. <heading>` — with the trailing dot optional, because several ids are
# written `### K5.6 A cast …`. The id is the first whitespace-delimited token with a trailing `.`
# removed, which is also the string every citation elsewhere uses.
#
# CLOSED is read from the heading OR from the entry's FIRST PARAGRAPH, because the file uses both
# conventions and neither is wrong: `C12`, `T14`, `G22` and `X2` say it in the heading; `F5` and
# `F6` open the body with `CLOSED.`. The token is matched in UPPER CASE only, on purpose — `P5`'s
# heading reads "OPEN; the counting is closed, the pipeline is not", and a case-insensitive match
# would call the one entry that says it is open, closed.
#
# Where both tokens appear the verdict is AMBIGUOUS rather than a guess: `K15` ("CLOSED where a
# class file can be READ, OPEN where it cannot") is a real half-and-half, and a script that picked a
# side would be the hand transcription this one replaces. Note that heading had to be MADE honest
# before the state existed at all: it used to read "counted where it cannot", which holds one token,
# so K15 parsed CLOSED and this branch had never once been taken while two documents cited it as the
# example. A documented state with no instance is a branch nobody has run — `ClosedTwinStatusSpec`
# pins K15's verdict for that reason.
# ---------------------------------------------------------------------------------------------
verdicts() {
  awk '
    function flush() {
      if (id == "") return
      c = (head ~ /CLOSED/ || para ~ /CLOSED/)
      o = (head ~ /OPEN/   || para ~ /OPEN/)
      v = c && o ? "AMBIGUOUS" : (c ? "CLOSED" : (o ? "OPEN" : "UNMARKED"))
      printf "%s\t%s\t%s\n", id, v, head
      id = ""; head = ""; para = ""; seen = 0
    }
    /^#/ {
      if ($0 ~ /^### /) {
        flush()
        head = substr($0, 5)
        id = head; sub(/[ \t].*$/, "", id); sub(/\.$/, "", id)
        next
      }
      flush(); next            # a `##` section heading ends the entry above it
    }
    {
      if (id == "" || seen) next
      if ($0 ~ /^[ \t]*$/) { if (para != "") seen = 1; next }
      para = para " " $0
    }
    END { flush() }
  ' "$LIMITS"
}

if [ $# -eq 0 ]; then
  verdicts
  exit 0
fi

ROWS="$1"
if [ ! -f "$ROWS" ]; then
  echo "catalog-status: no such file: $ROWS" >&2
  exit 2
fi

# ---------------------------------------------------------------------------------------------
# THE CROSS. Two findings, and they are different questions:
#   STALE     — the twin reads CLOSED and the row still claims OPEN. This is the whole point.
#   DANGLING  — the twin names an id `ENGINE-LIMITS.md` does not have, so the citation resolves to
#               nothing and the row's status is unfalsifiable. A row that cites an id nobody can
#               look up is worse than a row with no twin, which at least says so.
# `REVIEW` is reported and does not fail: an AMBIGUOUS twin is a human's call by construction.
# ---------------------------------------------------------------------------------------------
verdicts > "${TMPDIR:-/tmp}/catalog-verdicts.$$"

awk -F'\t' -v V="${TMPDIR:-/tmp}/catalog-verdicts.$$" '
  BEGIN {
    while ((getline line < V) > 0) { split(line, a, "\t"); verdict[a[1]] = a[2] }
  }
  /^#/ || /^[ \t]*$/ { next }
  {
    id = $1; status = $2; twin = $3
    if (twin == "" || twin == "-" || twin == "PREDICTED") next
    if (!(twin in verdict)) { printf "DANGLING\t%s\t%s\tno ENGINE-LIMITS entry `%s`\n", id, status, twin; bad++; next }
    if (verdict[twin] == "AMBIGUOUS") { printf "REVIEW\t%s\t%s\ttwin %s is CLOSED on one face and OPEN on another\n", id, status, twin; next }
    if (verdict[twin] == "CLOSED" && status == "OPEN") {
      printf "STALE\t%s\t%s\ttwin %s reads CLOSED\n", id, status, twin; bad++
    }
  }
  END { exit (bad > 0 ? 1 : 0) }
' "$ROWS"
rc=$?
rm -f "${TMPDIR:-/tmp}/catalog-verdicts.$$"
[ $rc -eq 0 ] && echo "catalog-status: no row claims OPEN against a CLOSED twin, and every twin resolves"
exit $rc
