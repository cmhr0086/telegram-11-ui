#!/usr/bin/env python3
"""Remove if / else-if branches whose condition uses `instanceof X` for the given class names.

  tools/dropbranch.py File.java Name1 Name2 ...

* `} else if (... instanceof X ...) { ... }`  -> branch removed
* standalone `if (... instanceof X ...) { ... }` with no else -> statement removed
* `if (instanceof X) {...} else if (...)`     -> first branch removed, `else if` becomes `if`
Anything else is reported and left alone.
"""
import re, sys

def match_paren(s, i, o, c):
    d = 0
    while i < len(s):
        if s[i] == o:
            d += 1
        elif s[i] == c:
            d -= 1
            if d == 0:
                return i
        elif s[i] == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == "\\" else 1
        i += 1
    raise ValueError("unbalanced")

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
for name in sys.argv[2:]:
    while True:
        m = re.search(r"instanceof\s+(?:[\w.]+\.)?" + re.escape(name) + r"\b", s)
        if not m:
            break
        # find start of the enclosing `if (`
        k = s.rfind("if (", 0, m.start())
        cond_end = match_paren(s, k + 3, "(", ")")
        if cond_end < m.start():
            print("!! not inside an if-condition at", s.count("\n", 0, m.start()) + 1); break
        brace = s.find("{", cond_end)
        if s[cond_end + 1:brace].strip():
            print("!! if without braces at", s.count("\n", 0, k) + 1); break
        end = match_paren(s, brace, "{", "}")
        before = s[:k].rstrip()
        after = s[end + 1:]
        if before.endswith("else"):
            # else-if branch: drop "else if (...) {...}" + following whitespace
            start = s.rfind("else", 0, k)
            trail = re.match(r"[ \t]*", after).end()
            s = s[:start] + after[trail:].lstrip(" ") if after[trail:].startswith("else") else s[:start].rstrip(" ") + after
        else:
            nxt = after.lstrip()
            if nxt.startswith("else if"):
                s = s[:k] + nxt[len("else "):]
            elif nxt.startswith("else"):
                print("!! plain else follows at", s.count("\n", 0, k) + 1); break
            else:
                line_start = s.rfind("\n", 0, k) + 1
                line_end = s.find("\n", end)
                s = s[:line_start] + s[line_end + 1:]
open(path, "w", encoding="utf-8").write(s)
