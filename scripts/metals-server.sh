#!/usr/bin/env bash
# Per-worktree Metals MCP server (Metals v2 standalone), owned by launchd so it survives the
# harness's process-tree reaping. One server per checkout: the port and the launchd label are
# derived from the checkout path exactly as scripts/_lib.sh derives SBT_GLOBAL_SERVER_DIR, so two
# worktrees never share a Metals (or a BSP sbt) instance.
#   scripts/metals-server.sh start   # idempotent; writes <root>/.mcp.json for Claude Code
#   scripts/metals-server.sh stop    # launchctl remove + kill the tree
#   scripts/metals-server.sh status  # label, port, pid, ready?
#   scripts/metals-server.sh port    # print the port
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
METALS_VERSION="${METALS_MCP_VERSION:-2.0.0-M18}"
HASH="$(printf '%s' "$ROOT" | shasum | cut -c1-8)"
# the checkout's LANE sbt server (scripts/_lib.sh): Metals' `sbt bsp` must attach to it, not start
# a second sbt for the same build — a cold sbt on this build took Metals past its 2-minute limit twice.
_LIB_HASH="$(cd "$ROOT" && printf '%s' "$(pwd)" | shasum | cut -c1-8)"
SBT_DIR="/tmp/sbt-bp-${_LIB_HASH}"
PORT=$((41000 + 0x${HASH:0:3} % 1000))
LABEL="bp-metals-$HASH"
LOG="$ROOT/.balticporter/metals-$HASH.log"
JAVA_HOME_PIN=/Users/dev/.sdkman/candidates/java/current
ready() { curl -s -m 3 -o /dev/null -w '%{http_code}' -X POST "http://localhost:$PORT/mcp" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"0"}}}' 2>/dev/null | grep -qE '^20[02]$'; }
write_mcp_json() {
  cat > "$ROOT/.mcp.json" <<JSON
{ "mcpServers": { "metals": { "type": "http", "url": "http://localhost:$PORT/mcp" } } }
JSON
}
case "${1:-status}" in
  port) echo "$PORT" ;;
  status)
    echo "root=$ROOT label=$LABEL port=$PORT"
    launchctl list 2>/dev/null | grep -q "$LABEL" && echo "launchd: running" || echo "launchd: not loaded"
    ready && echo "mcp: ready" || echo "mcp: not answering"
    [ -f "$LOG" ] && grep -q 'Fatal error in MCP server' "$LOG" && echo "last log has a FATAL (see $LOG; the 2-minute build-connect Await in StandaloneMcpService)" ;;
  start)
    mkdir -p "$ROOT/.balticporter"
    if launchctl list 2>/dev/null | grep -q "$LABEL"; then echo "$LABEL already loaded (port $PORT)"; write_mcp_json; exit 0; fi
    # The wrapper rate-limits launchd's relaunch-on-exit: metals-mcp exits 1 when its build
    # connection is not up within StandaloneMcpService's hard-coded 2-minute Await (sbt 2's BSP
    # server on this build takes ~60-120 s cold, more under load), and launchd would otherwise
    # restart it in a tight loop. Three consecutive failures remove the job and leave the log.
    cat > "$ROOT/.balticporter/metals-$HASH.sh" <<SH
#!/bin/bash
cd "$ROOT" || exit 1
export JAVA_HOME=$JAVA_HOME_PIN; export PATH="\$JAVA_HOME/bin:\$PATH"
export SBT_GLOBAL_SERVER_DIR=$SBT_DIR
F="$ROOT/.balticporter/metals-$HASH.failures"
cs launch org.scalameta:metals-mcp_2.13:$METALS_VERSION --java-opt -Xmx3g --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --java-opt --add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED -- --workspace "$ROOT" --port $PORT --client claude-code --java-home \$JAVA_HOME --default-bsp-to-build-tool
st=\$?
if [ \$st -eq 0 ]; then rm -f "\$F"; exit 0; fi
n=\$(( \$(cat "\$F" 2>/dev/null || echo 0) + 1 )); echo \$n > "\$F"
echo "metals-mcp exited \$st (failure \$n/3) — \$(date)"
if [ \$n -ge 3 ]; then echo "giving up; run scripts/metals-server.sh start when the machine is quieter"; rm -f "\$F"; launchctl remove "$LABEL"; exit \$st; fi
sleep 60
exit \$st
SH
    chmod +x "$ROOT/.balticporter/metals-$HASH.sh"
    rm -f "$ROOT/.balticporter/metals-$HASH.failures"
    # warm the lane server first (sbt 2 build load ~20 s cold) so Metals' BSP handshake is instant
    ( cd "$ROOT" && JAVA_HOME=$JAVA_HOME_PIN PATH="$JAVA_HOME_PIN/bin:$PATH" SBT_GLOBAL_SERVER_DIR="$SBT_DIR" sbt --client "projects" >/dev/null 2>&1 ) || true
    launchctl submit -l "$LABEL" -o "$LOG" -e "$LOG" -- /usr/bin/env "PATH=$PATH" "HOME=$HOME" /bin/bash -lc "$ROOT/.balticporter/metals-$HASH.sh"
    write_mcp_json
    echo "started $LABEL on port $PORT (log: $LOG); .mcp.json written" ;;
  stop)
    launchctl remove "$LABEL" 2>/dev/null && echo "removed $LABEL" || echo "$LABEL was not loaded"
    for i in 1 2 3 4 5 6; do launchctl list 2>/dev/null | grep -q "$LABEL" || break; sleep 1; done
    for p in $(pgrep -f "metals-mcp_2.13:$METALS_VERSION" 2>/dev/null); do lsof -a -p "$p" -d cwd -Fn 2>/dev/null | grep -q "^n$ROOT\$" && kill "$p" 2>/dev/null; done
    sleep 2
    for p in $(pgrep -f "metals-mcp_2.13:$METALS_VERSION" 2>/dev/null); do lsof -a -p "$p" -d cwd -Fn 2>/dev/null | grep -q "^n$ROOT\$" && kill -9 "$p" 2>/dev/null; done
    rm -f "$ROOT/.mcp.json" "$ROOT/.balticporter/metals-$HASH.failures"; echo "stopped" ;;
  *) echo "usage: $0 start|stop|status|port"; exit 2 ;;
esac
