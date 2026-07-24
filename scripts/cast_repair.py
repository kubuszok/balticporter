#!/usr/bin/env python3
"""Error-feedback cast repair for the libgdx-core port.

Java raw types / unchecked casts compile via erasure; Scala's stricter checker rejects them.
This mirrors the erasure: read scalac's E007 `Found/Required` mismatches, and wrap the
offending expression in `.asInstanceOf[Required]` (or `[scala.Nothing]` when the required type
is an unnameable path-dependent type parameter like `loader.P`). Idempotent; run in a loop.

Usage: cast_repair.py <sbt-compile-output-file> <emitted-src-root>
Prints the number of patches applied.
"""
import re, sys, os

ANSI = re.compile(r'\x1b\[[0-9;]*m')
HDR  = re.compile(r'-- \[E007\] Type Mismatch Error: (\S+\.scala):(\d+):(\d+)')
# a path-dependent type parameter: a value path segment followed by a single-capital type param
PATHDEP = re.compile(r'[A-Za-z_?][\w?]*\.[A-Z]\b')

def parse(out_path):
    lines = [ANSI.sub('', l).removeprefix('[error] ').rstrip('\n') for l in open(out_path)]
    errs = []
    for i, l in enumerate(lines):
        m = HDR.search(l)
        if not m:
            continue
        req = None
        for j in range(i, min(i + 16, len(lines))):
            r = re.search(r'Required:\s*(.+)$', lines[j])
            if r:
                req = r.group(1).strip()
                break
        if req:
            errs.append((m.group(1), int(m.group(2)), int(m.group(3)), req))
    return errs

def expr_end(line, start):
    """end (exclusive) of the primary expression at `start`, balanced over ()[]{}."""
    depth, i = 0, start
    while i < len(line):
        c = line[i]
        if c in '([{': depth += 1
        elif c in ')]}':
            if depth == 0: return i
            depth -= 1
        elif c == ',' and depth == 0: return i
        i += 1
    return len(line)

def cast_type(req):
    # unnameable (path-dependent type param, wildcard refinement) -> the bottom type conforms anywhere
    if req == '?' or '?{' in req or '?.' in req or PATHDEP.search(req):
        return 'scala.Nothing'
    return req

def main():
    out_path, root = sys.argv[1], sys.argv[2]
    errs = parse(out_path)
    by_file = {}
    for f, ln, col, req in errs:
        by_file.setdefault(f, []).append((ln, col, req))
    applied = 0
    for f, ps in by_file.items():
        if not os.path.exists(f):
            continue
        lines = open(f).read().split('\n')
        # patch latest position first so earlier offsets stay valid; dedupe identical sites
        for ln, col, req in sorted(set(ps), key=lambda p: (-p[0], -p[1])):
            idx = ln - 1
            if not (0 <= idx < len(lines)):
                continue
            line = lines[idx]
            start = min(col - 1, len(line))
            end = expr_end(line, start)
            raw = line[start:end]
            core = raw.strip()
            typ = cast_type(req)
            wrapped = f'.asInstanceOf[{typ}]'
            if not core or wrapped in raw:      # empty or already wrapped -> skip
                continue
            lead = raw[:len(raw) - len(raw.lstrip())]
            lines[idx] = line[:start] + lead + f'({core}){wrapped}' + line[end:]
            applied += 1
        open(f, 'w').write('\n'.join(lines))
    print(applied)

if __name__ == '__main__':
    main()
