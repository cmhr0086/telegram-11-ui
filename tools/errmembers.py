#!/usr/bin/env python3
"""List (or prune) the members of a class that contain compile errors.

  tools/errmembers.py ClassName [--prune] [--keep name1,name2] [--depth N]
"""
import os, re, subprocess, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import importlib.util
spec = importlib.util.spec_from_file_location("prune", os.path.join(ROOT, "tools/prune.py"))
prune = importlib.util.module_from_spec(spec); spec.loader.exec_module(prune)

name = sys.argv[1]
args = sys.argv[2:]
keep = set()
if "--keep" in args:
    keep = set(args[args.index("--keep") + 1].split(","))
depth = int(args[args.index("--depth") + 1]) if "--depth" in args else 1
errs = subprocess.run([os.path.join(ROOT, "tools/errs.py"), name], capture_output=True, text=True).stdout.splitlines()
lines = sorted({int(l.split(":")[1]) for l in errs if l.startswith(name + ":")})
path = None
for d, _, fs in os.walk(os.path.join(ROOT, "tgui/src/main/java")):
    if name + ".java" in fs:
        path = os.path.join(d, name + ".java")
src = open(path, encoding="utf-8").read()
ms = [m for m in prune.members(src) if m[2] == depth]
hits = {}
for ln in lines:
    for m in ms:
        s, e, d, h = m
        hs = s + len(h) - len(h.lstrip())
        if prune.line_of(src, hs) <= ln <= prune.line_of(src, e):
            hits.setdefault(m, []).append(ln)
            break
    else:
        print(f"  (no member) line {ln}")
for m, ls in sorted(hits.items(), key=lambda kv: kv[0][0]):
    nm = prune.member_name(m[3])
    flag = " KEEP" if nm in keep else ""
    print(f"{prune.line_of(src, m[0] + len(m[3]) - len(m[3].lstrip()))} {nm} {len(ls)}{flag}")
if "--prune" in args:
    targets = [str(ls[0]) for m, ls in hits.items() if prune.member_name(m[3]) not in keep]
    subprocess.run([os.path.join(ROOT, "tools/prune.py"), path, "--depth", str(depth), "--lines"] + targets)
