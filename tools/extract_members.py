#!/usr/bin/env python3
"""Copy top-level members by name from an upstream file into a tgui file, using a simple
brace matcher (for files tools/prune.py cannot parse, e.g. MediaDataController).

  tools/extract_members.py tgui/path/File.java name1 name2 ...
"""
import os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
path = sys.argv[1]
rel = os.path.relpath(os.path.abspath(path), os.path.join(ROOT, "tgui/src/main/java"))
src = open(os.path.join(ROOT, "upstream/TMessagesProj/src/main/java", rel), encoding="utf-8").read()


def extract(start):
    i, d, seen = start, 0, False
    while True:
        c = src[i]
        if c == '"':
            i += 1
            while src[i] != '"':
                i += 2 if src[i] == "\\" else 1
        elif c == "'":
            i += 1
            while src[i] != "'":
                i += 2 if src[i] == "\\" else 1
        elif src.startswith("//", i):
            i = src.index("\n", i)
        elif c == '{':
            d += 1; seen = True
        elif c == '}':
            d -= 1
            if seen and d == 0:
                j = i + 1
                if src[j:j + 1] == ';':
                    j += 1
                return src[start:j]
        elif c == ';' and not seen:
            return src[start:i + 1]
        elif c == '=' and not seen and d == 0:
            # field initializer: run to the terminating ';' at depth 0 (lambdas/arrays allowed)
            k, dd = i, 0
            while True:
                ch = src[k]
                if ch in '({[':
                    dd += 1
                elif ch in ')}]':
                    dd -= 1
                elif ch == ';' and dd == 0:
                    return src[start:k + 1]
                k += 1
        i += 1


chunks = []
for name in sys.argv[2:]:
    pat = re.compile(r"^    (?=\S)(?:@\w+\s+)*(?:(?:public|private|protected|static|final|abstract|synchronized)\s+)*[\w<>\[\],.?]*(?:\s+[\w<>\[\],.?]+)*?\s+" + re.escape(name) + r"\s*(?:\(|=|;|\{|extends|implements|<)", re.M)
    found = 0
    for m in pat.finditer(src):
        chunks.append(extract(m.start()))
        found += 1
    print(("+ " if found else "!! not found ") + name + (f" x{found}" if found > 1 else ""), file=sys.stderr)
dst = open(path, encoding="utf-8").read()
idx = dst.rstrip().rfind("}")
dst = dst[:idx].rstrip("\n") + "\n\n" + "\n\n".join(c.rstrip() for c in chunks) + "\n}\n"
open(path, "w", encoding="utf-8").write(dst)
