#!/usr/bin/env bash
# Regression-test the engine against the libraries that consume it.
#
#   scripts/consumers-check.sh [jvm|full|binary] [consumer ...]
#
# Publishes the committed engine to the local Ivy repository, then, for each consumer checkout found
# beside this repository, makes a scratch clone of the consumer's HEAD, points its engine pin
# at the freshly published version, regenerates the port and runs the consumer's own test aliases.
# Nothing in the consumer's checkout is modified. Result rows are printed at the end; the exit code
# is the number of failed rows.
#
#   jvm   (default)  ci-jvm-3
#   full             verifyLocal — the consumer's own pre-push gate: its tests on every platform
#   binary           only the base-binary rows below, no source runs (seconds after the publish)
#
# The consumers are checked in dependency order (lls is a library of sge and ssg), each against
# the one published just before it. BP_<NAME>_DIR (e.g. BP_SGE_DIR) checks another checkout of a
# consumer, such as a worktree on its master, when the sibling checkout is on another branch.
#
# Before any of that, the `base-binary` row: a consumer whose meta-build loads another consumer's
# PUBLISHED port artifact (sge's project/plugins.sbt: `"lls-port" % Versions.lls`) runs that exact
# artifact, compiled against an older engine, on this engine. The source runs cannot see a break
# there: they rebuild the base and publish it fresh. The step resolves the pinned artifact (Central
# snapshots first, then ivy-local), forces the engine to this run's version as the consumer's
# `dependencyOverrides` does, and links every class of the artifact against that classpath with
# scripts/LinkCheck.java. A linkage check, not a meta-build load: it takes seconds instead of a full
# generation, names every missing member instead of the first one the JVM trips on, and covers code
# paths a single load would not execute; MiMa would compare the whole engine against its previous
# version and report every internal change, while this reads only what the base actually calls.
#
# ivy-local is left as it was found: a version directory this run publishes over is set aside first
# and put back at exit, and whatever the run published itself is removed.
set -u

level="${1:-jvm}"
[ $# -gt 0 ] && shift
case "$level" in
  jvm) tasks="ci-jvm-3" ;;
  full) tasks="verifyLocal" ;;
  binary) tasks="" ;;
  *) echo "usage: $0 [jvm|full|binary] [consumer ...]" >&2; exit 64 ;;
esac
if [ $# -gt 0 ]; then consumers=("$@"); else consumers=(lls sge ssg); fi

root="$(cd "$(dirname "$0")/.." && pwd -P)"
work="$root/.balticporter/consumers-check"
engine_jdk="${BP_ENGINE_JAVA_HOME:-$HOME/.sdkman/candidates/java/22.0.2-graalce}"
consumer_jdk="${BP_CONSUMER_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-zulu}"
snapshots="https://central.sonatype.com/repository/maven-snapshots"
ivy="$HOME/.ivy2/local"
mkdir -p "$work"

if [ -n "$(git -C "$root" status --porcelain --untracked-files=no)" ]; then
  echo "consumers-check: commit first - the published version is the commit hash" >&2
  exit 65
fi
hash="$(git -C "$root" rev-parse HEAD)"
version="$hash-SNAPSHOT"

# ivy-local bookkeeping: `claimed` lists "<org dir> <artifact glob> <version>" entries this run
# publishes; a directory already there is moved to $saved first and restored by the EXIT trap.
saved="$work/ivy-saved"
claimed=""
rm -rf "$saved"
claim() { # org artifact-glob version
  local d rel
  claimed+="$1 $2 $3"$'\n'
  for d in "$ivy/$1"/$2/"$3"; do
    [ -d "$d" ] || continue
    rel="${d#"$ivy"/}"
    mkdir -p "$saved/$(dirname "$rel")"
    mv "$d" "$saved/$rel"
  done
}
release_claims() {
  local org glob v d
  while read -r org glob v; do
    [ -z "$org" ] && continue
    for d in "$ivy/$org"/$glob/"$v"; do [ -d "$d" ] && rm -rf "$d"; done
  done <<< "$claimed"
  if [ -d "$saved" ]; then
    (cd "$saved" && find . -mindepth 3 -maxdepth 3 -type d) | while read -r rel; do
      mkdir -p "$(dirname "$ivy/$rel")"
      mv "$saved/$rel" "$ivy/$rel"
    done
    rm -rf "$saved"
  fi
}
trap release_claims EXIT

consumer_src() { # name -> the checkout to read, or empty
  local override="BP_$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')_DIR"
  local src="${!override:-$root/../$1}"
  if [ -d "$src/.git" ] || [ -f "$src/.git" ]; then (cd "$src" && pwd -P); fi
}

echo "== publishing the engine as $version"
claim com.kubuszok 'balticporter-*' "$version"
(cd "$root" && JAVA_HOME="$engine_jdk" PATH="$engine_jdk/bin:$PATH" sbt --client "reload; publishLocal") \
  > "$work/engine-publish.log" 2>&1
if [ $? -ne 0 ] || ! ls "$ivy/com.kubuszok/balticporter-engine_3/$version" > /dev/null 2>&1; then
  echo "consumers-check: publishLocal failed or did not produce $version - see $work/engine-publish.log" >&2
  exit 66
fi

failed=0
summary=""

# --- base-binary: each published base port artifact a consumer's meta-build loads, on this engine
forces=()
for d in "$ivy/com.kubuszok"/balticporter-*_3/"$version"; do
  a="${d%/"$version"}"; forces+=(--force-version "com.kubuszok:${a##*/}:$version")
done
for name in "${consumers[@]}"; do
  src="$(consumer_src "$name")"
  [ -z "$src" ] && continue
  meta=""
  for f in $(git -C "$src" ls-tree --name-only HEAD project/ | /usr/bin/grep -E '\.sbt$'); do
    meta+="$(git -C "$src" show "HEAD:$f")"$'\n'
  done
  versions_scala="$(git -C "$src" show HEAD:project/Versions.scala 2> /dev/null)"
  bases="$(printf '%s' "$meta" | perl -ne 'print "$1 $2\n" while /"([^"]+)"\s*%%\s*"([A-Za-z0-9._-]+)-port"/g' | sort -u)"
  if [ -z "$bases" ]; then
    summary+="$name\tbase-binary\tnone\tthe meta-build loads no published port artifact\n"
    continue
  fi
  while read -r org base; do
    log="$work/$name-base-binary-$base.log"
    pinned="$(printf '%s' "$meta" | perl -ne 'print "$1\n" if /"\Q'"$base"'\E-port"\s*%\s*"([^"]+)"/' | head -1)"
    [ -z "$pinned" ] && pinned="$(printf '%s' "$versions_scala" | perl -ne 'print "$1\n" if /val\s+\Q'"$base"'\E\s*=\s*"([^"]+)"/' | head -1)"
    if [ -z "$pinned" ]; then
      summary+="$name\tbase-binary\tFAIL\t$base-port: no pinned version found in project/*.sbt or project/Versions.scala\n"
      failed=$((failed + 1)); continue
    fi
    echo "== $name: $base-port $pinned against engine $version   (log: $log)"
    if ! command -v cs > /dev/null 2>&1; then
      summary+="$name\tbase-binary\tFAIL\t$base-port $pinned: coursier (cs) is not on PATH\n"
      failed=$((failed + 1)); continue
    fi
    cp="$(JAVA_HOME="$consumer_jdk" cs fetch --classpath --no-default -r "$snapshots" -r central -r ivy2Local \
      "$org:$base-port_3:$pinned" "${forces[@]}" 2> "$log")"
    jar="$(printf '%s' "$cp" | tr ':' '\n' | /usr/bin/grep -E "/$base-port_3([-/][^/]*)?\.jar$" | head -1)"
    if [ -z "$jar" ]; then
      summary+="$name\tbase-binary\tFAIL\t$base-port $pinned is not published on Central or ivy-local ($log)\n"
      failed=$((failed + 1)); continue
    fi
    { echo "artifact: $jar"; printf '%s' "$cp" | tr ':' '\n' | /usr/bin/grep '/balticporter-' | sed 's/^/engine:   /'; } >> "$log"
    JAVA_HOME="$consumer_jdk" "$consumer_jdk/bin/java" "$root/scripts/LinkCheck.java" "$jar" "$cp" >> "$log" 2>&1
    code=$?
    last="$(tail -1 "$log")"
    if [ "$code" -eq 0 ]; then
      summary+="$name\tbase-binary\tok\t$base-port $pinned: $last\n"
    else
      first="$(/usr/bin/grep -m1 -E '^[A-Za-z]+(Error|Exception):' "$log" | sed 's/   (referenced from.*//')"
      summary+="$name\tbase-binary\tFAIL\t$base-port $pinned is binary-incompatible with this engine: republish $base against it before pinning $name; ${first:-$last} ($last, $log)\n"
      failed=$((failed + 1))
    fi
  done <<< "$bases"
done

# --- source: each consumer regenerated, built and tested against this engine
published=""
for name in "${consumers[@]}"; do
  [ "$level" = "binary" ] && break
  src="$(consumer_src "$name")"
  if [ -z "$src" ]; then
    summary+="$name\tsource\tskipped\tno checkout beside this repository\n"
    continue
  fi
  tree="$work/$name"
  log="$work/$name.log"

  # A local clone, not a linked worktree: the consumers derive their version through JGit, which
  # cannot read a linked worktree's object database.
  rm -rf "$tree"
  if ! git clone --quiet --local "$src" "$tree" > "$log" 2>&1; then
    summary+="$name\tsource\tFAIL\tcould not clone the consumer's checkout ($log)\n"
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
    summary+="$name\tsource\tFAIL\tno engine pin found in project/plugins.sbt\n"
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
    (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client "show $name/version") \
      > "$work/$name-publish.log" 2>&1
    built="$(sed 's/\x1b\[[0-9;]*m//g' "$work/$name-publish.log" | /usr/bin/grep -E '^\[info\] [0-9][^ ]*$' | tail -1 | awk '{print $2}')"
    if [ -n "$built" ]; then
      claim com.kubuszok "${name}_*" "$built"
      claim com.kubuszok "${name}-*" "$built"
      (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client "$publish") \
        >> "$work/$name-publish.log" 2>&1 && published="$published $name=$built"
    fi
  fi
  (cd "$tree" && JAVA_HOME="$consumer_jdk" PATH="$consumer_jdk/bin:$PATH" sbt --client shutdown) > /dev/null 2>&1

  errors="$(/usr/bin/grep -c '^\[error\]' "$log")"
  tests="$(/usr/bin/grep -o -E 'Total [0-9]+, Failed [0-9]+, Errors [0-9]+' "$log" | tr -d ',' |
    awk '{t += $2; f += $4; e += $6; n++} END {if (n) printf "%d tests in %d modules, %d failed, %d errors", t, n, f, e}')"
  if [ "$code" -eq 0 ]; then
    summary+="$name\tsource\tok\t${tests:-no test summary}\n"
  else
    summary+="$name\tsource\tFAIL\texit $code, $errors error lines; ${tests:-no test summary} ($log)\n"
    failed=$((failed + 1))
  fi
done

# The local publishes served THIS run only (the EXIT trap removes them and restores what they
# replaced). Left in ivy-local they shadow Central for every other build on the machine (a
# `<name>-port` at the library's released version but linked against this run's engine broke a
# consumer's build with two engine hashes), and they hide a published-artifact break that CI would
# see; the run's evidence is in $work, not in ~/.ivy2.
echo
echo "engine $version, level $level"
printf "%b" "$summary" | column -t -s "$(printf '\t')"
exit "$failed"
