#!/usr/bin/env python3
"""Cases for guard.py: `python3 plugin/hooks/guard_test.py`. The pass list is the false positives seen in real sessions."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import guard  # noqa: E402

BLOCK = [
    "sbt compile",
    "cd ../sge && sbt 'sge/test'",
    "sbt -batch 'engine/test'",
    "JAVA_HOME=/x sbt --server=false test",
    "timeout 600 sbt testFull",
    "pkill -f sbt-launch",
    "killall -9 java",
    "pgrep -f sbt | xargs kill -9",
    "kill -9 $(pgrep -f sbt-launch)",
    "sed -i '' 's/a/b/' build.sbt",
    "sed -i.bak -e 's/a/b/' engine/src/main/scala/X.scala",
    "perl -pi -e 's/a/b/' README.md",
    "python3 - <<'EOF'\np='corpus/src/main/scala/X.scala'\ns=open(p).read()\nopen(p,'w').write(s)\nEOF",
    "git merge feature",
    "git -c user.name=x merge origin/master",
    "git commit --no-verify -m x",
    "git -c core.hooksPath=/dev/null commit -m x",
    "git reset --hard HEAD~1",
    "git stash",
    "git stash pop",
    "rm -rf ~/Library/Caches/sbt/v2",
    "git commit --allow-empty -m 'trigger ci'",
    "git push --force origin branch",
    "git push -f",
]

PASS = [
    "sbt --client 'engine/test; corpus/test'",
    "cd ../sge && sbt --client ci-jvm-3",
    "sbt -client compile",
    "sbt --client shutdown",
    "sbt --version",
    "which sbt",
    "pgrep -f sbt-launch | head",
    "grep sbt project/build.properties",
    "echo 'run sbt compile later'",
    "git commit -S -m 'use sbt compile, never pkill'",
    "kill 44265 44267",
    "kill -0 $pid",
    "sed -n '1,5p' build.sbt",
    "sed -E 's/x/y/i' file.txt | head",
    "sed -i '' 's/a/b/' .balticporter/scratch/list.txt",
    "python3 - <<'EOF'\nopen('.balticporter/out.tsv','w').write('x')\nEOF",
    "python3 -c 'print(1)'",
    "git merge --ff-only feature",
    "git merge-base HEAD master",
    "git log --merges",
    "git stash list",
    "git reset --soft HEAD~1",
    "rm -rf target/out",
    "git push --force-with-lease origin branch",
    "# sbt compile is what we never run\nls",
]

EDIT_BLOCK = ["/w/sge/target/balticporter-sge/src_managed/main/scala/sge/Gdx.scala", "/w/balticporter/ported/sge/src_managed/main/scala/A.scala"]
EDIT_PASS = ["/w/sge/sge/src/main/scala/sge/Sge.scala", "/w/sge/project/BalticPorterGen.scala"]

failed = 0
for c in BLOCK:
    if guard.check_bash(c, "/nonexistent") is None:
        failed += 1
        print("NOT BLOCKED:", repr(c))
for c in PASS:
    r = guard.check_bash(c, "/nonexistent")
    if r is not None:
        failed += 1
        print("WRONGLY BLOCKED:", repr(c), "->", r[:60])
for p in EDIT_BLOCK:
    if guard.check_edit(p) is None:
        failed += 1
        print("EDIT NOT BLOCKED:", p)
for p in EDIT_PASS:
    if guard.check_edit(p) is not None:
        failed += 1
        print("EDIT WRONGLY BLOCKED:", p)
print(f"{len(BLOCK) + len(PASS) + len(EDIT_BLOCK) + len(EDIT_PASS)} cases, {failed} failed")
sys.exit(1 if failed else 0)
