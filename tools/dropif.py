#!/usr/bin/env python3
"""Remove every `if (<cond>) { ... }` block whose header contains the given text exactly.

  tools/dropif.py File.java "if (botCommandsMenuButton != null) {" [...]
Only blocks without an else branch are removed; others are reported.
"""
import re, sys

def match_brace(s, i):
    d = 0
    while i < len(s):
        c = s[i]
        if c == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == "\\" else 1
        elif c == "'":
            i += 1
            while s[i] != "'":
                i += 2 if s[i] == "\\" else 1
        elif s.startswith("//", i):
            i = s.index("\n", i)
            continue
        elif c == '{':
            d += 1
        elif c == '}':
            d -= 1
            if d == 0:
                return i
        i += 1
    raise ValueError("unbalanced")

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
for header in sys.argv[2:]:
    n = 0
    pos = 0
    while True:
        k = s.find(header, pos)
        if k < 0:
            break
        line_start = s.rfind("\n", 0, k) + 1
        if s[line_start:k].strip():
            pos = k + 1
            continue
        brace = k + len(header) - 1
        assert s[brace] == "{", header
        end = match_brace(s, brace)
        rest = s[end + 1:]
        if re.match(r"\s*else\b", rest):
            print("!! has else, skipped at line", s.count("\n", 0, k) + 1)
            pos = end
            continue
        nl = s.find("\n", end)
        s = s[:line_start] + s[nl + 1:]
        n += 1
        pos = line_start
    print(f"- {n}x {header}")
open(path, "w", encoding="utf-8").write(s)
