#!/usr/bin/env python3
"""PreToolUse guard for Baltic Porter and the repositories that consume it.

Reads the hook payload on stdin. Exit 0 lets the tool call through; exit 2 blocks it and the text
on stderr is shown to the model, so every refusal names the command to use instead.

Each rule exists because the written rule alone did not hold (measured over this project's
sessions): stating it in a memory file, a skill or a brief reduced violations but never ended them.
"""
import json
import os
import re
import subprocess
import sys

SCRATCH = (".balticporter/", "/tmp/", "/private/tmp/", "/var/folders/", "scratchpad", "/.claude/plans/", "/memory/")
SOURCE_EXT = r"\.(scala|sbt|java|md|conf|ya?ml|sh|json|tsv|properties|py)\b|Justfile"
# a command starts at the beginning, or after a shell separator, optionally behind env assignments or a wrapper
START = r"(?:^|[;&|(\n]|\bthen\b|\bdo\b)\s*(?:[A-Za-z_][A-Za-z0-9_]*=\S*\s+|timeout\s+\S+\s+|nohup\s+|time\s+|exec\s+)*"


def strip(command: str) -> str:
    """Drop what is not shell syntax: comment lines, heredoc bodies, then quoted strings."""
    lines, out, end = command.split("\n"), [], None
    for line in lines:
        if end is not None:
            if line.strip() == end:
                end = None
            continue
        if line.lstrip().startswith("#"):
            continue
        m = re.search(r"<<-?\s*['\"]?([A-Za-z_][A-Za-z0-9_]*)['\"]?", line)
        if m:
            end = m.group(1)
        out.append(line)
    text = "\n".join(out)
    text = re.sub(r"'[^']*'", "''", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    return text


def segments(text: str):
    return [s for s in re.split(r"[;&|\n]+", text) if s.strip()]


def in_scratch(path: str) -> bool:
    return any(marker in path for marker in SCRATCH)


def git(cwd: str, *args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        return ""


def check_bash(raw: str, cwd: str):
    text = strip(raw)

    for seg in segments(text):
        if re.search(START + r"sbt\b", seg) and not re.search(r"\s(--client|-client)\b", seg):
            if not re.search(r"\bsbt\s+(--help|--version|-h|-V)\b", seg):
                return ("Use the warm sbt server: `sbt --client '<project>/<task>; <next task>'` — ONE quoted string, tasks joined "
                        "with `;`. Never bare `sbt`, `sbt -batch` or `--server=false` (each cold-starts a JVM). "
                        "Project ids: run `sbt --client projects` first. See the sbt2-client skill.")

    if re.search(START + r"(pkill|killall)\b", text) or re.search(r"xargs\s+(-\S+\s+)*kill\b", text) or re.search(r"\bkill\s+(-\w+\s+)*\$\((pgrep|ps)\b", text):
        return ("Pattern kills are machine-global: they take down other agents' sbt servers. Stop ONE server with "
                "`sbt --client shutdown` in its directory, or find one PID (`lsof -p <pid>` shows its cwd) and `kill <pid>`.")

    for seg in segments(text):
        if re.search(START + r"(/usr/bin/)?(sed\s+(-\w+\s+)*-\w*i|perl\s+(-\w+\s+)*-\w*i)", seg):
            files = [t for t in seg.split() if "/" in t or re.search(SOURCE_EXT, t)]
            if not files or not all(in_scratch(f) for f in files):
                return "Edit repository files with the Edit tool (or Write for a new file). In-place sed/perl is allowed on scratch files only."

    if re.search(r"\bpython3?\b", text) and re.search(r"open\([^)]*['\"][wa]b?['\"]|write_text|\.write\(", raw):
        targets = re.findall(r"['\"]([^'\"\n]*(?:" + SOURCE_EXT + r"))['\"]", raw)
        targets = [t[0] if isinstance(t, tuple) else t for t in targets]
        if any(not in_scratch(t) for t in targets):
            return "A Python rewrite of a repository file is a disguised sed. Use the Edit tool (or Write for a new file)."

    if re.search(r"\bgit\s+(-[cC]\s+\S+\s+)*merge(?![-\w])", text) and not re.search(r"--(ff-only|abort|continue)\b", text):
        return "Linear history only: `git rebase <base>`, then `git merge --ff-only`. Merge commits are rejected by the organisation's rules."

    if re.search(r"\bgit\b[^;&|\n]*(--no-verify|core\.hooksPath)", raw):
        return "Hooks are never bypassed. Fix what the hook reports."

    if re.search(r"\bgit\s+(-[cC]\s+\S+\s+)*(reset\s+[^;&|\n]*--hard|stash(?!\s+(list|show)))", text):
        return ("The stash is shared by every worktree and `reset --hard` destroys work. Make a WIP commit on your branch, "
                "or restore single files with `git restore <file>`.")

    if re.search(r"\brm\s+-\w*r\w*\s+[^;&|]*Library/Caches/sbt", raw):
        return "Do not wipe the shared sbt cache: it cold-starts every agent on this machine. Prune only on a full disk, in the order the disk-hygiene notes give."

    if re.search(r"\bgit\b[^;&|\n]*\bcommit\b[^;&|\n]*--allow-empty", text):
        return "No empty commits to trigger CI. Re-run a workflow with `gh run rerun <id>` (or `--failed`)."

    if re.search(r"\bgit\b[^;&|\n]*\bpush\b[^;&|\n]*\s(--force|-f)(\s|$)", text):
        return "No plain force pushes. If a rewritten branch really has to be pushed, use `--force-with-lease` and say why."

    if re.search(r"\bgit\b[^;&|\n]*\bpush\b", text):
        reason = unverified_push(raw, cwd)
        if reason:
            return reason
    return None


def unverified_push(raw: str, cwd: str):
    """A consumer repository (one that generates code with Baltic Porter) is pushed only after the local gate ran on this commit."""
    m = re.search(r"\bgit\s+-C\s+(\S+)", raw)
    repo = os.path.join(cwd, m.group(1)) if m else cwd
    top = git(repo, "rev-parse", "--show-toplevel")
    if not top or not os.path.exists(os.path.join(top, "project", "BalticPorterGen.scala")):
        return None
    head = git(top, "rev-parse", "HEAD")
    marker = os.path.join(top, "target", "local-verification")
    verified = open(marker).read().strip() if os.path.exists(marker) else ""
    if verified == head:
        return None
    if verified:
        changed = [f for f in git(top, "diff", "--name-only", verified, head).split("\n") if f]
        if changed and all(f.endswith(".md") or f.startswith("docs/") or f.startswith(".github/") or f.startswith(".claude/") or f.startswith(".rescale/data/") for f in changed):
            return None
    return ("This commit has not passed the local gate. Run `sbt --client verifyLocal` (JVM, Scala.js and Scala Native tests; it records "
            "the verified commit in target/local-verification), fix EVERY failure, then push once. Compiling is not linking, and CI is "
            "not the place to find out.")


def check_edit(path: str):
    if "/src_managed/" in path or re.search(r"/target/balticporter-[^/]+/", path):
        return ("Generated code is never edited: it is rewritten on the next generation. Fix the translation — the port's policy or "
                "the engine — and regenerate. See the root-cause-port skill.")
    return None


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    tool = payload.get("tool_name", "")
    data = payload.get("tool_input", {}) or {}
    cwd = payload.get("cwd") or os.getcwd()
    reason = None
    if tool == "Bash":
        reason = check_bash(data.get("command", ""), cwd)
    elif tool in ("Edit", "Write", "NotebookEdit"):
        reason = check_edit(data.get("file_path", "") or "")
    if reason:
        print(reason, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
