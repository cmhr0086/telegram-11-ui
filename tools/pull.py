#!/usr/bin/env python3
"""Copy upstream Telegram sources/resources into the tgui module.

Usage:
  tools/pull.py class org.telegram.ui.Components.LayoutHelper [...]
      Copy upstream source files (skips ones already present).
  tools/pull.py res
      Scan tgui java sources for R.<type>.<name> references and copy any
      missing drawables / strings / plurals / colors / dimens / anims etc.
  tools/pull.py missing
      List R references in the module that are still not defined.

The upstream checkout under ./upstream is read-only reference material.
"""
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UP = os.path.join(ROOT, "upstream/TMessagesProj/src/main")
UP_JAVA = os.path.join(UP, "java")
UP_RES = os.path.join(UP, "res")
LIB = os.path.join(ROOT, "tgui/src/main")
LIB_JAVA = os.path.join(LIB, "java")
LIB_RES = os.path.join(LIB, "res")

FILE_TYPES = {"drawable", "mipmap", "anim", "animator", "raw", "layout", "xml", "font", "interpolator"}
VALUE_TYPES = {"string": "string", "plurals": "plurals", "color": "color", "dimen": "dimen",
               "style": "style", "array": "array", "integer": "integer", "bool": "bool",
               "attr": "attr", "id": "item"}
SKIP_RAW = False  # raw = Lottie JSON assets (phase 2)


def pull_class(fqcn):
    rel = fqcn.replace(".", "/") + ".java"
    src = os.path.join(UP_JAVA, rel)
    dst = os.path.join(LIB_JAVA, rel)
    if os.path.exists(dst):
        return False
    if not os.path.exists(src):
        print("!! not found upstream:", fqcn)
        return False
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy(src, dst)
    print("+", fqcn)
    return True


def java_files(base):
    for d, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(".java") or f.endswith(".kt"):
                yield os.path.join(d, f)


DEMO_JAVA = os.path.join(ROOT, "demo/src/main/java")


def res_refs():
    refs = set()
    pat = re.compile(r"\bR\.(\w+)\.(\w+)")
    for f in list(java_files(LIB_JAVA)) + list(java_files(DEMO_JAVA)):
        with open(f, encoding="utf-8") as fh:
            for t, n in pat.findall(fh.read()):
                refs.add((t, n))
    # references from copied xml resources (@drawable/x, @string/y, ?attr ignored)
    xpat = re.compile(r"@(?:\+)?(\w+)/([\w.]+)")
    for d, _, fs in os.walk(LIB_RES):
        for f in fs:
            if f.endswith(".xml"):
                with open(os.path.join(d, f), encoding="utf-8") as fh:
                    for t, n in xpat.findall(fh.read()):
                        if t != "android":
                            refs.add((t, n.replace(".", "_")))
    return refs


def lib_defined():
    defined = set()
    for d, _, fs in os.walk(LIB_RES):
        folder = os.path.basename(d)
        for f in fs:
            p = os.path.join(d, f)
            if folder.startswith("values"):
                try:
                    tree = ET.parse(p)
                except ET.ParseError:
                    continue
                for el in tree.getroot():
                    name = el.get("name")
                    if not name:
                        continue
                    tag = el.tag
                    if tag == "item":
                        tag = el.get("type", "item")
                    if tag == "declare-styleable":
                        defined.add(("styleable", name))
                        for a in el:
                            defined.add(("attr", a.get("name")))
                        continue
                    defined.add((tag, name.replace(".", "_")))
            else:
                t = folder.split("-")[0]
                defined.add((t, f.split(".")[0]))
    return defined


_values_cache = {}


def upstream_values(folder, fname):
    key = (folder, fname)
    if key not in _values_cache:
        p = os.path.join(UP_RES, folder, fname)
        _values_cache[key] = ET.parse(p) if os.path.exists(p) else None
    return _values_cache[key]


def add_value(folder, fname, el):
    dst = os.path.join(LIB_RES, folder, fname)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if not os.path.exists(dst):
        with open(dst, "w", encoding="utf-8") as fh:
            fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n')
    with open(dst, encoding="utf-8") as fh:
        content = fh.read()
    text = ET.tostring(el, encoding="unicode").strip()
    content = content.replace("</resources>", "    " + text + "\n</resources>")
    with open(dst, "w", encoding="utf-8") as fh:
        fh.write(content)


def pull_value(t, name):
    tag = VALUE_TYPES[t]
    found = False
    for folder in sorted(os.listdir(UP_RES)):
        if not folder.startswith("values"):
            continue
        # translations are out of scope; keep default + night/v21 etc.
        qual = folder[len("values"):]
        if qual and not re.match(r"^(-night|-v\d+|-sw\d+dp)*$", qual):
            continue
        for fname in os.listdir(os.path.join(UP_RES, folder)):
            if not fname.endswith(".xml"):
                continue
            tree = upstream_values(folder, fname)
            if tree is None:
                continue
            for el in tree.getroot():
                n = el.get("name")
                if n is None or n.replace(".", "_") != name:
                    continue
                if el.tag == tag or (el.tag == "item" and el.get("type") == t) or (t == "id" and el.tag == "item"):
                    add_value(folder, fname, el)
                    found = True
    return found


def pull_file_res(t, name):
    found = False
    for folder in os.listdir(UP_RES):
        if folder.split("-")[0] != t:
            continue
        for f in os.listdir(os.path.join(UP_RES, folder)):
            if f.split(".")[0] == name:
                dst = os.path.join(LIB_RES, folder, f)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy(os.path.join(UP_RES, folder, f), dst)
                found = True
    return found


def pull_res():
    total = 0
    while True:
        defined = lib_defined()
        missing = sorted(r for r in res_refs() if r not in defined)
        progress = 0
        for t, n in missing:
            if t == "raw" and SKIP_RAW:
                continue
            ok = False
            if t in FILE_TYPES:
                ok = pull_file_res(t, n)
            elif t in VALUE_TYPES:
                ok = pull_value(t, n)
            if ok:
                progress += 1
                print("+ res", t, n)
        total += progress
        if not progress:
            break
    print("pulled", total)


def list_missing():
    defined = lib_defined()
    for t, n in sorted(r for r in res_refs() if r not in defined):
        print(t, n)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "class":
        for c in sys.argv[2:]:
            pull_class(c)
    elif cmd == "res":
        pull_res()
    elif cmd == "missing":
        list_missing()
