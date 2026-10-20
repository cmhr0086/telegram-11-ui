#!/usr/bin/env python3
"""Re-add upstream imports (org.telegram / androidx.recyclerview) that fiximports dropped
earlier but whose classes now exist in tgui."""
import os, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "tgui/src/main/java")
UP = os.path.join(ROOT, "upstream/TMessagesProj/src/main/java")
present = set()
for d, _, fs in os.walk(LIB):
    for f in fs:
        if f.endswith(".java"):
            present.add(os.path.relpath(os.path.join(d, f), LIB)[:-5].replace("/", "."))
pat = re.compile(r"^import\s+(static\s+)?((?:org\.telegram|androidx\.recyclerview)[\w.]*?)(\.\*)?;\s*$", re.M)
for d, _, fs in os.walk(LIB):
    for f in fs:
        if not f.endswith(".java"):
            continue
        p = os.path.join(d, f)
        up = os.path.join(UP, os.path.relpath(p, LIB))
        if not os.path.exists(up):
            continue
        s = open(p, encoding="utf-8").read()
        if "tgui stub" in s or "tgui:" in s[:2000]:
            continue
        added = []
        for m in pat.finditer(open(up, encoding="utf-8").read()):
            line = m.group(0).strip()
            if line in s:
                continue
            parts = m.group(2).split(".")
            ok = any(".".join(parts[:i]) in present for i in range(len(parts), 1, -1))
            if ok:
                added.append(line)
        if added:
            s = re.sub(r"^(package [\w.]+;\n)", lambda mm: mm.group(1) + "\n" + "\n".join(added) + "\n", s, count=1, flags=re.M)
            open(p, "w", encoding="utf-8").write(s)
            print("restored", f, len(added))
