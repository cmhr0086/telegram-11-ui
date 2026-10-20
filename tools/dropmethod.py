#!/usr/bin/env python3
"""Remove methods (any nesting level, e.g. inside anonymous classes) by name, searching only
after a marker string.

  tools/dropmethod.py File.java "marker text" name1 name2 ...
Each name removes the first method declaration `... name(` found after the marker, including
a preceding @Override line.
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

path, marker = sys.argv[1], sys.argv[2]
s = open(path, encoding="utf-8").read()
base = s.index(marker)
for name in sys.argv[3:]:
    pat = re.compile(r"\n([ \t]*@Override[ \t]*\n)?[ \t]*(?:public|private|protected)[^\n;{=]*?\b" + re.escape(name) + r"\s*\(")
    m = pat.search(s, base)
    if not m:
        print("!! not found", name)
        continue
    brace = s.index("{", m.end())
    end = match_brace(s, brace)
    s = s[:m.start()] + s[end + 1:]
    print("- " + name)
open(path, "w", encoding="utf-8").write(s)
