#!/usr/bin/env python3
"""For each tgui file with an upstream counterpart, list top-level members removed vs upstream."""
import os, sys, importlib.util
from collections import Counter
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("prune", os.path.join(ROOT, "tools/prune.py"))
prune = importlib.util.module_from_spec(spec); spec.loader.exec_module(prune)
LIB = os.path.join(ROOT, "tgui/src/main/java")
UP = os.path.join(ROOT, "upstream/TMessagesProj/src/main/java")
only = set(sys.argv[1:])
for d, _, fs in os.walk(LIB):
    for f in sorted(fs):
        if not f.endswith(".java") or (only and f[:-5] not in only):
            continue
        p = os.path.join(d, f)
        u = os.path.join(UP, os.path.relpath(p, LIB))
        if not os.path.exists(u):
            continue
        a = Counter(prune.member_name(h) for s, e, dd, h in prune.members(open(u, encoding="utf-8").read()) if dd == 1)
        b = Counter(prune.member_name(h) for s, e, dd, h in prune.members(open(p, encoding="utf-8").read()) if dd == 1)
        missing = a - b
        if missing:
            print(f"{f}: " + ", ".join(f"{k}" + (f"x{v}" if v > 1 else "") for k, v in sorted(missing.items(), key=lambda kv: str(kv[0]))))
