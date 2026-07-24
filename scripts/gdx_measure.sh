#!/bin/bash
# re-emit libgdx-core and count errors with scala-cli (the consistent gate; sbt incremental lies)
cd "$(dirname "$0")/.."
sbt -client "corpus-tests/runMain balticporter.corpus.LibgdxCoreMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "wrote" | head -1
pkill -9 -f scala-cli 2>/dev/null; sleep 1
scala-cli compile --scala 3.8.4 --server=false libgdx-core/src/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/gdxmeasure.txt
echo "TOTAL ERRORS: $(grep -cE '\[E[0-9]+\].*Error' /tmp/gdxmeasure.txt)"
grep -oE "\[E[0-9]+\][^:]*Error" /tmp/gdxmeasure.txt | sort | uniq -c | sort -rn | head
