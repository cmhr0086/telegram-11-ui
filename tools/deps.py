#!/usr/bin/env python3
"""List upstream classes referenced by tgui sources but not yet present.

Output: <fqcn> <upstream-lines> <referencing files...>
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UP_JAVA = os.path.join(ROOT, "upstream/TMessagesProj/src/main/java")
LIB_JAVA = os.path.join(ROOT, "tgui/src/main/java")


def classes_in(base):
    out = {}
    for d, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(".java"):
                p = os.path.join(d, f)
                out[os.path.relpath(p, base)[:-5].replace("/", ".")] = p
    return out


up = classes_in(UP_JAVA)
lib = classes_in(LIB_JAVA)
by_pkg = defaultdict(set)
for c in up:
    pkg, _, simple = c.rpartition(".")
    by_pkg[pkg].add(simple)

missing = defaultdict(set)
imp = re.compile(r"^import\s+(?:static\s+)?([\w.]+)(?:\.\*)?;", re.M)
for c, p in lib.items():
    src = open(p, encoding="utf-8").read()
    for m in imp.findall(src):
        if not (m.startswith("org.telegram") or m.startswith("androidx.recyclerview")):
            continue
        # strip nested / static member parts down to a top-level class
        parts = m.split(".")
        for i in range(len(parts), 1, -1):
            cand = ".".join(parts[:i])
            if cand in up:
                if cand not in lib:
                    missing[cand].add(c.rpartition(".")[2])
                break
    pkg = c.rpartition(".")[0]
    body = re.sub(r"//.*|/\*.*?\*/|\"(?:\\.|[^\"\\])*\"", "", src, flags=re.S)
    words = set(re.findall(r"\b[A-Z]\w*\b", body))
    for simple in by_pkg[pkg] & words:
        cand = pkg + "." + simple
        if cand not in lib:
            missing[cand].add(c.rpartition(".")[2])

for c in sorted(missing, key=lambda k: -len(missing[k])):
    n = sum(1 for _ in open(up[c], encoding="utf-8"))
    refs = sorted(missing[c])
    print(f"{c} {n} [{len(refs)}] {' '.join(refs[:8])}{' ...' if len(refs) > 8 else ''}")
