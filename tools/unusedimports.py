#!/usr/bin/env python3
"""Remove unused single-type imports from the given Java files."""
import re, sys
for p in sys.argv[1:]:
    s = open(p, encoding="utf-8").read()
    body = re.sub(r"^import .*;$", "", s, flags=re.M)
    body = re.sub(r"//[^\n]*|/\*.*?\*/", "", body, flags=re.S)
    def keep(m):
        name = m.group(2).split(".")[-1]
        if name == "*":
            return m.group(0)
        return m.group(0) if re.search(r"\b" + re.escape(name) + r"\b", body) else ""
    s2 = re.sub(r"^import (static )?([\w.]+(?:\.\*)?);\n", keep, s, flags=re.M)
    if s2 != s:
        open(p, "w", encoding="utf-8").write(s2)
        print("cleaned", p)
