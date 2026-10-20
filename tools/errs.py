#!/usr/bin/env python3
"""Summarize javac errors from build-out.txt: tools/errs.py [FileNameFilter]"""
import os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lines = open(os.path.join(ROOT, "build", "build-out.txt"), encoding="utf-8", errors="replace").read().split("\n")
flt = sys.argv[1] if len(sys.argv) > 1 else None
seen = set()
out = []
i = 0
while i < len(lines):
    m = re.match(r"\s*(/\S+\.java):(\d+): error: (.*)", lines[i])
    if not m:
        i += 1
        continue
    path, ln, msg = m.group(1), int(m.group(2)), m.group(3)
    src = lines[i + 1].strip() if i + 1 < len(lines) else ""
    sym = ""
    j = i + 2
    while j < len(lines) and not re.match(r"\s*/\S+\.java:\d+: error", lines[j]) and j < i + 7:
        s = lines[j].strip()
        if s.startswith("symbol:"):
            sym = s[7:].strip()
        j += 1
    i = j
    name = os.path.basename(path)[:-5]
    if flt and name != flt:
        continue
    key = (path, ln, msg, sym)
    if key in seen:
        continue
    seen.add(key)
    out.append((name, ln, msg, sym, src))
for name, ln, msg, sym, src in sorted(out, key=lambda x: (x[0], x[1])):
    print(f"{name}:{ln}: {msg}{' ['+sym+']' if sym else ''} :: {src[:150]}")
