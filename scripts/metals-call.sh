#!/usr/bin/env bash
# Call one Metals MCP tool over the streamable-HTTP transport from a shell — the client an AGENT in a
# WORKTREE uses, because a subagent inherits the session's MCP servers (the primary's Metals) and
# cannot register its own. Usage:
#   scripts/metals-call.sh list
#   scripts/metals-call.sh <tool> '<json arguments>'     e.g. compile-file '{"file":"…/Foo.scala"}'
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
PORT="$("$ROOT/scripts/metals-server.sh" port)"
URL="http://localhost:$PORT/mcp"
HDR=(-H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
init="$(curl -s -m 20 -D - -X POST "$URL" "${HDR[@]}" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"metals-call","version":"0"}}}')" || { echo "!! Metals MCP not answering on $URL — run scripts/metals-server.sh start"; exit 1; }
SID="$(printf '%s' "$init" | grep -i '^mcp-session-id:' | awk '{print $2}' | tr -d '\r')"
SH=(); [ -n "$SID" ] && SH=(-H "Mcp-Session-Id: $SID")
curl -s -m 10 -o /dev/null -X POST "$URL" "${HDR[@]}" "${SH[@]}" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
body() { printf '%s' "$1" | sed -n 's/^data: //p' | tail -1; }
if [ "${1:-list}" = "list" ]; then
  r="$(curl -s -m 60 -X POST "$URL" "${HDR[@]}" "${SH[@]}" -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}')"
  printf '%s\n' "$r" | grep -q '^data:' && r="$(body "$r")"
  printf '%s' "$r" | python3 -c 'import json,sys; d=json.load(sys.stdin); [print(t["name"], "-", t.get("description","")[:100]) for t in d["result"]["tools"]]'
else
  tool="$1"; args="${2:-{\}}"
  r="$(curl -s -m 600 -X POST "$URL" "${HDR[@]}" "${SH[@]}" -d "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"$tool\",\"arguments\":$args}}")"
  printf '%s\n' "$r" | grep -q '^data:' && r="$(body "$r")"
  printf '%s' "$r" | python3 -c 'import json,sys; d=json.load(sys.stdin); res=d.get("result") or d; [print(c.get("text","")) for c in res.get("content",[])] if isinstance(res,dict) and "content" in res else print(json.dumps(d,indent=1)[:4000])'
fi
