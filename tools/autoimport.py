#!/usr/bin/env python3
"""Add missing imports for 'cannot find symbol [class X]' / '[variable X]' errors.

Builds a simple-name -> FQN index from every import line in the upstream tree plus
the classes that exist in tgui, then adds imports where exactly one candidate exists.
"""
import os, re, subprocess
from collections import Counter, defaultdict
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "tgui/src/main/java")
UP = os.path.join(ROOT, "upstream/TMessagesProj/src/main/java")
index = defaultdict(Counter)
for base in (UP,):
    for d, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(".java"):
                for m in re.findall(r"^import\s+([\w.]+);", open(os.path.join(d, f), encoding="utf-8", errors="replace").read(), re.M):
                    if m.startswith(("java.", "javax.", "android.", "androidx.")):
                        index[m.rsplit(".", 1)[1]][m] += 1
present = {}
for d, _, fs in os.walk(LIB):
    for f in fs:
        if f.endswith(".java"):
            fq = os.path.relpath(os.path.join(d, f), LIB)[:-5].replace("/", ".")
            present[f[:-5]] = fq
out = subprocess.run([os.path.join(ROOT, "tools/errs.py")], capture_output=True, text=True).stdout
need = defaultdict(set)
for line in out.splitlines():
    m = re.match(r"(\w+):\d+: (?:cannot find symbol \[(?:class|variable) (\w+)\]|package (\w+) does not exist)", line)
    if m:
        need[m.group(1)].add(m.group(2) or m.group(3))
paths = {}
for d, _, fs in os.walk(LIB):
    for f in fs:
        paths[f[:-5]] = os.path.join(d, f)
for cls, names in need.items():
    p = paths.get(cls)
    if not p:
        continue
    s = open(p, encoding="utf-8").read()
    pkg = re.search(r"^package ([\w.]+);", s, re.M).group(1)
    add = []
    upf = os.path.join(UP, os.path.relpath(p, LIB))
    own = {}
    if os.path.exists(upf):
        for m in re.findall(r"^import\s+([\w.]+);", open(upf, encoding="utf-8").read(), re.M):
            if not m.startswith("org.telegram") or m.rsplit(".", 1)[1] in present or m.endswith(".R"):
                own[m.rsplit(".", 1)[1]] = m
    for n in sorted(names):
        if n in own:
            fq = own[n]
        elif n in present and present[n].rsplit(".", 1)[0] != pkg:
            fq = present[n]
        elif n in index:
            fq = index[n].most_common(1)[0][0]
        else:
            continue
        line = f"import {fq};"
        if line not in s:
            add.append(line)
    if add:
        s = re.sub(r"^(package [\w.]+;\n)", lambda mm: mm.group(1) + "\n" + "\n".join(add) + "\n", s, count=1, flags=re.M)
        open(p, "w", encoding="utf-8").write(s)
        print(cls, "+", ", ".join(a[7:-1] for a in add))
