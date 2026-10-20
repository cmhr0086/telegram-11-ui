#!/usr/bin/env python3
"""Drop imports of org.telegram / androidx.recyclerview classes that are not part of tgui."""
import os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "tgui/src/main/java")
present = set()
for d, _, fs in os.walk(LIB):
    for f in fs:
        if f.endswith(".java"):
            present.add(os.path.relpath(os.path.join(d, f), LIB)[:-5].replace("/", "."))
keep = {"org.telegram.messenger.R", "org.telegram.messenger.BuildConfig"}
pat = re.compile(r"^import\s+(static\s+)?((?:org\.telegram|androidx\.recyclerview)[\w.]*?)(\.\*)?;\s*$", re.M)
for d, _, fs in os.walk(LIB):
    for f in fs:
        if not f.endswith(".java"):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding="utf-8").read()
        def ok(m):
            name = m.group(2)
            parts = name.split(".")
            for i in range(len(parts), 1, -1):
                c = ".".join(parts[:i])
                if c in present or c in keep:
                    return True
                if m.group(3) and i == len(parts):
                    # wildcard package import: keep if package dir exists
                    if os.path.isdir(os.path.join(LIB, *parts)):
                        return True
            return False
        s2 = pat.sub(lambda m: m.group(0) if ok(m) else "", s)
        if s2 != s:
            open(p, "w", encoding="utf-8").write(s2)
            if "-v" in sys.argv:
                print("fixed", p)
