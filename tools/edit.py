#!/usr/bin/env python3
"""Apply batched exact-text replacements read from stdin.

Format:
  @@ relative/or/absolute/path.java      (paths relative to tgui/src/main/java/org/telegram)
  <<<
  old text
  ===
  new text
  >>>
Repeat <<< === >>> blocks; a new @@ line switches file. Fails loudly when old text is
missing or ambiguous (use more context).
"""
import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "tgui/src/main/java/org/telegram")
data = sys.stdin.read().split("\n")
path = None
i = 0
errors = 0
cache = {}
def load(p):
    if p not in cache:
        cache[p] = open(p, encoding="utf-8").read()
    return cache[p]
while i < len(data):
    line = data[i]
    if line.startswith("@@ "):
        p = line[3:].strip()
        path = p if os.path.isabs(p) else os.path.join(BASE, p)
        i += 1
    elif line == "<<<":
        j = data.index("===", i)
        k = data.index(">>>", j)
        old = "\n".join(data[i + 1:j])
        new = "\n".join(data[j + 1:k])
        s = load(path)
        n = s.count(old)
        if n != 1:
            print(f"!! {os.path.basename(path)}: old text found {n} times:\n{old[:200]}")
            errors += 1
        else:
            cache[path] = s.replace(old, new)
        i = k + 1
    else:
        i += 1
for p, s in cache.items():
    open(p, "w", encoding="utf-8").write(s)
print(f"applied, {errors} errors")
sys.exit(1 if errors else 0)
