#!/usr/bin/env python3
"""Copy members (by name, all overloads) from the upstream version of a class back into tgui.

  tools/restore_member.py path/in/tgui/File.java name1 name2 ...
Members are appended before the final closing brace of the top-level class.
"""
import os, sys, importlib.util
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("prune", os.path.join(ROOT, "tools/prune.py"))
prune = importlib.util.module_from_spec(spec); spec.loader.exec_module(prune)
path = sys.argv[1]
rel = os.path.relpath(os.path.abspath(path), os.path.join(ROOT, "tgui/src/main/java"))
up = open(os.path.join(ROOT, "upstream/TMessagesProj/src/main/java", rel), encoding="utf-8").read()
names = set(sys.argv[2:])
chunks = []
for s, e, d, h in prune.members(up):
    if d == 1 and prune.member_name(h) in names:
        chunks.append(up[s:e].rstrip() + "\n")
        print("+", prune.member_name(h), file=sys.stderr)
src = open(path, encoding="utf-8").read()
idx = src.rstrip().rfind("}")
src = src[:idx].rstrip("\n") + "\n" + "".join(chunks) + "}\n"
open(path, "w", encoding="utf-8").write(src)
