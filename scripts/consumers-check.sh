#!/usr/bin/env bash
# Regression-test the engine against the libraries that consume it.
#
#   scripts/consumers-check.sh [jvm|full] [consumer ...]
#
# Publishes the committed engine to the local Ivy repository, then, for each consumer checkout found
# beside this repository, makes a scratch clone of the consumer's HEAD, points its engine pin
# at the freshly published version, regenerates the port and runs the consumer's own test aliases.
# Nothing in the consumer's checkout is modified. One result row per consumer is printed at the end;
# the exit code is the number of consumers that failed.
#
#   jvm   (default)  ci-jvm-3
#   full             verifyLocal — the consumer's own pre-push gate: its tests on every platform
#
# The consumers are checked in dependency order (lls is a library of sge and ssg), each against
# the one published just before it. BP_<NAME>_DIR (e.g. BP_SGE_DIR) checks another checkout of a
# consumer, such as a worktree on its master, when the sibling checkout is on another branch.
set -u

level="${1:-jvm}"
[ $# -gt 0 ] && shift
case "$level" in
  jvm) tasks="ci-jvm-3" ;;
  full) tasks="verifyLocal" ;;
  *) echo "usage: $0 [jvm|full] [consumer ...]" >&2; exit 64 ;;
esac
if [ $# -gt 0 ]; then consumers=("$@"); else consumers=(lls sge ssg); fi

root="$(cd "$(dirname "$0")/.." && pwd -P)"
work="$root/.balticporter/consumers-check"
engine_jdk="${BP_ENGINE_JAVA_HOME:-$HOME/.sdkman/candidates/java/22.0.2-graalce}"
consumer_jdk="${BP_CONSUMER_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-zulu}"
mkdir -p "$work"

if [ -n "$(git -C "$root" status --porcelain --untracked-files=no)" ]; then
  echo "consumers-check: commit first - the published version is the commit hash" >&2
  exit 65
fi
hash="$(git -C "$root" rev-parse HEAD)"
version="$hash-SNAPSHOT"

echo "== publishing the engine as $version"
(cd "$root" && JAVA_HOME="$engine_jdk" PATH="$engine_jdk/bin:$PATH" sbt --client "reload; publishLocal") \
  > "$work/engine-publish.log" 2>&1
if [ $? -ne 0 ] || ! ls "$HOME/.ivy2/local/com.kubuszok/balticporter-engine_3/$version" > /dev/null 2>&1; then
  echo "consumers-check: publishLocal failed or did not produce $version - see $work/engine-publish.log" >&2
  exit 66
fi

failed=0
summary=""
published=""
for name in "${consumers[@]}"; do
  # BP_<NAME>_DIR checks another checkout of that consumer (a worktree on its master) instead of the sibling
  override="BP_$(printf '%s' "$name" | tr '[:lower:]-' '[:upper:]_')_DIR"
  src="${!override:-$root/../$name}"
  if [ ! -d "$src/.git" ] && [ ! -f "$src/.git" ]; then
    summary+="$name\tskipped\tno checkout beside this repository\n"
    continue
  fi
  src="$(cd "$src" && pwd -P)"
  tree="$work/$name"
  log="$work/$name.log"

  # A local clone, not a linked worktree: the consumers derive their version through JGit, which
  # cannot read a linked worktree's object database.
  rm -rf "$tree"
  if ! git clone --quiet --local "$src" "$tree" > "$log" 2>&1; then
    summary+="$name\tFAILED\tcould not clone the consumer's checkout ($log)\n"
    failed=$((failed + 1))
    continue
  fi

  # The upstream Java is a submodule: reuse the consumer's checkout of it instead of cloning again.
  while read -r sub; do
    [ -z "$sub" ] && continue
    if [ -e "$src/$sub/.git" ]; then
      rm -rf "${tree:?}/$sub"
      ln -s "$src/$sub" "$tree/$sub"
    fi
  done < <(git -C "$src" config --file .gitmodules --get-regexp 'submodule\..*\.path' 2> /dev/null | awk '{print $2}')

  pins="$tree/project/plugins.sbt"
  if ! /usr/bin/grep -q -E '[0-9a-f]{40}-SNAPSHOT' "$pins"; then
    summary+="$name\tFAILED\tno engine pin found in project/plugins.sbt\n"
    failed=$((failed + 1))
    continue
  fi
  perl -pi -e "s/[0-9a-f]{40}-SNAPSHOT/$version/g" "$pins"
  printf '\nresolvers += Resolver.defaultLocal\n' >> "$pins"

  # A consumer checked earlier in this run was published locally: build against THAT, as its next
  # release would be consumed. (An inline member's body is compiled into the caller, so a consumer
  # can break on a library it only depends on — a published jar of the old engine hides that.)
  versions="$tree/project/Versions.scala"
  if [ -f "$versions" ]; then
    for dep in $published; do
      dep_name="${dep%%=*}"; dep_version="${dep#*=}"
      DEP="$dep_name" VER="$dep_version" perl -pi -e 's/(val\s+\Q$ENV{DEP}\E\s*=\s*)"[^"]*"/$1"$ENV{VER}"/' "$versions"
    done
  fi

  echo "== $name: generatePort ; $tasks   (log: $log)"
  (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client "generatePort ; $tasks") \
    >> "$log" 2>&1
  code=$?
  if [ "$code" -eq 0 ] && [ "$name" != "${consumers[${#consumers[@]} - 1]}" ]; then
    # publish what was just built, for the consumers after this one (JVM artifacts at level jvm).
    # A consumer's port policy is published as `<name>-port`, pinned at the engine of THIS run: a
    # dependent's build resolves it at the library's version, and the released one would drag the
    # library's own, older engine pin into that build (an eviction error on two engine hashes).
    if [ "$level" = "full" ]; then publish="publishLocal"; else
      publish="$name/publishLocal"
      /usr/bin/grep -q "lazy val \`$name-port\`" "$tree/build.sbt" && publish="$publish ; $name-port/publishLocal"
    fi
    (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client "$publish ; show $name/version") \
      > "$work/$name-publish.log" 2>&1
    built="$(sed 's/\x1b\[[0-9;]*m//g' "$work/$name-publish.log" | /usr/bin/grep -E '^\[info\] [0-9][^ ]*$' | tail -1 | awk '{print $2}')"
    [ -n "$built" ] && published="$published $name=$built"
  fi
  (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client shutdown) > /dev/null 2>&1

  errors="$(/usr/bin/grep -c '^\[error\]' "$log")"
  tests="$(/usr/bin/grep -o -E 'Total [0-9]+, Failed [0-9]+, Errors [0-9]+' "$log" | tr -d ',' |
    awk '{t += $2; f += $4; e += $6; n++} END {if (n) printf "%d tests in %d modules, %d failed, %d errors", t, n, f, e}')"
  if [ "$code" -eq 0 ]; then
    summary+="$name\tok\t${tests:-no test summary}\n"
  else
    summary+="$name\tFAILED\texit $code, $errors error lines; ${tests:-no test summary} ($log)\n"
    failed=$((failed + 1))
  fi
done

# The local publishes served THIS run only. Left in ivy-local they shadow Central for every other
# build on the machine (a `<name>-port` at the library's released version but linked against this
# run's engine broke a consumer's build with two engine hashes), and they hide a published-artifact
# break that CI would see; the run's evidence is in $work, not in ~/.ivy2.
for entry in $published; do
  n="${entry%%=*}"; v="${entry#*=}"
  rm -rf "$HOME/.ivy2/local/com.kubuszok/${n}_3/$v" "$HOME/.ivy2/local/com.kubuszok/${n}-port_3/$v"
done
rm -rf "$HOME/.ivy2/local/com.kubuszok/balticporter-"*"_3/$version"

echo
echo "engine $version, level $level"
printf "%b" "$summary" | column -t -s "$(printf '\t')"
exit "$failed"
