#!/usr/bin/env python3
"""Remove class members from a Java source file.

  tools/prune.py File.java [--class Outer.Inner] name1 name2 ...   remove members by name
  tools/prune.py File.java --lines 120 455 ...                      remove members enclosing lines
  tools/prune.py File.java --list [--class X]                       list members with line ranges

Members are fields, methods, constructors, nested types and initializer
blocks of the top-level class (or of --class). Removing by name drops every
overload. --lines removes the innermost member (of any class) that contains
the line, unless --depth N restricts it to members at that nesting depth.
"""
import re
import sys


def scan(src):
    """Yield (index, char, depth_before) for structural chars outside comments/strings."""
    i, n, depth = 0, len(src), 0
    out = []
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            i = src.find("\n", i)
            if i < 0:
                break
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            i = src.find("*/", i + 2) + 2
            continue
        if src.startswith('"""', i):
            i = src.find('"""', i + 3) + 3
            continue
        if c == '"' or c == "'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            i = j + 1
            continue
        if c in "{}();=":
            if c == "=" and i + 1 < n and src[i + 1] == "=":
                i += 2
                continue
            if c == "{":
                out.append((i, c, depth))
                depth += 1
            elif c == "}":
                depth -= 1
                out.append((i, c, depth))
            else:
                out.append((i, c, depth))
        i += 1
    return out


def members(src):
    """Return list of (start, end, depth, header) for members at every depth >= 1."""
    toks = scan(src)
    result = []
    # stack of open type bodies: (depth_of_body, member_start, saw_eq, saw_paren)
    # We track per body depth the start of the current member.
    body_start = {}  # depth -> start index of current member
    state = {}  # depth -> dict(eq=bool, brace_member_depth=None)
    type_body = set()  # depths that are type bodies (class/interface/enum)
    # decide what each '{' opens: a type body if header contains class/interface/enum/@interface
    # or anonymous class 'new X(...) {' ; method bodies otherwise.
    stack = []  # list of (open_index, kind, depth)
    last_top = 0
    for idx, c, d in toks:
        if d == 0 and c in ";}":
            last_top = idx + 1
        if c == "{":
            header_start = body_start.get(d, None)
            header = src[header_start:idx] if header_start is not None else src[last_top:idx]
            hdr = re.sub(r"//[^\n]*|/\*.*?\*/", "", header, flags=re.S)
            is_type = bool(re.search(r"\b(class|interface|enum|record)\b", hdr)) and not re.search(r"[=(]", hdr.split("class")[0] if "class" in hdr else "")
            if d in type_body and is_type or d == 0 and is_type:
                kind = "type"
            elif re.search(r"\bnew\s+[\w.<>\[\], ?]+\([^;]*\)\s*$", hdr, flags=re.S):
                kind = "type"  # anonymous class body
            else:
                kind = "block"
            stack.append((idx, kind, d))
            if kind == "type":
                type_body.add(d + 1)
                body_start[d + 1] = idx + 1
                state[d + 1] = {"eq": False, "paren": 0}
            elif d in type_body and not state[d]["eq"]:
                # method / initializer body opening at member level
                state[d]["block"] = True
        elif c == "}":
            open_idx, kind, od = stack.pop()
            if kind == "type":
                type_body.discard(d + 1)
                body_start.pop(d + 1, None)
                state.pop(d + 1, None)
            if d in type_body and state[d].get("block") and not state[d]["eq"]:
                start = body_start[d]
                result.append((start, idx + 1, d, src[start:open_idx]))
                body_start[d] = idx + 1
                state[d] = {"eq": False, "paren": 0}
            elif d in type_body and kind == "type" and not state[d]["eq"]:
                # nested type declaration finished
                start = body_start[d]
                result.append((start, idx + 1, d, src[start:open_idx]))
                body_start[d] = idx + 1
                state[d] = {"eq": False, "paren": 0}
        elif c == "(" and d in type_body:
            state[d]["paren"] = state[d].get("paren", 0) + 1
        elif c == ")" and d in type_body:
            state[d]["paren"] = state[d].get("paren", 0) - 1
        elif c == "=" and d in type_body and state[d].get("paren", 0) == 0:
            state[d]["eq"] = True
        elif c == ";" and d in type_body:
            start = body_start[d]
            result.append((start, idx + 1, d, src[start:idx]))
            body_start[d] = idx + 1
            state[d] = {"eq": False, "paren": 0}
    return result


def member_name(header):
    h = re.sub(r"//[^\n]*|/\*.*?\*/", "", header, flags=re.S)
    h = re.sub(r"@\w+(\([^)]*\))?", "", h).strip()
    m = re.search(r"\b(class|interface|enum|record)\s+(\w+)", h)
    if m:
        return m.group(2)
    m = re.search(r"(\w+)\s*\(", h)
    if m and "=" not in h.split("(")[0]:
        return m.group(1)
    h = h.split("=")[0].strip()
    m = re.findall(r"(\w+)\s*(?:\[\s*\])*\s*$", h)
    return m[-1] if m else None


def line_of(src, idx):
    return src.count("\n", 0, idx) + 1


def trim_start(src, start):
    # keep the preceding newline structure: begin removal at start of the member's first line
    return start


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    src = open(path, encoding="utf-8").read()
    ms = members(src)
    depth = None
    cls_filter = None
    if "--depth" in args:
        i = args.index("--depth")
        depth = int(args[i + 1])
        del args[i:i + 2]
    if "--class" in args:
        i = args.index("--class")
        cls_filter = args[i + 1]
        del args[i:i + 2]

    # map member -> enclosing type depth & name
    types = [(s, e, d, member_name(h)) for s, e, d, h in ms if re.search(r"\b(class|interface|enum)\s+\w+", re.sub(r"//[^\n]*|/\*.*?\*/", "", h, flags=re.S))]

    def in_class(m):
        s, e, d, h = m
        if cls_filter is None:
            return d == 1
        target = cls_filter.split(".")[-1]
        for ts, te, td, tn in types:
            if tn == target and ts < s and e <= te and d == td + 1:
                return True
        return False

    if args and args[0] == "--list":
        for m in ms:
            if in_class(m):
                print(f"{line_of(src, m[0])}-{line_of(src, m[1])} d{m[2]} {member_name(m[3])}")
        return

    remove = []
    if args and args[0] == "--lines":
        lines = [int(x) for x in args[1:]]
        for ln in lines:
            cands = [m for m in ms if line_of(src, m[0]) <= ln <= line_of(src, m[1]) and (depth is None or m[2] == depth)]
            # header region may include leading comments; require line within actual content
            cands = [m for m in cands if line_of(src, m[0] + len(m[3]) - len(m[3].lstrip())) <= ln]
            if not cands:
                print(f"!! no member at line {ln}", file=sys.stderr)
                continue
            best = max(cands, key=lambda m: m[2]) if depth is None else cands[0]
            remove.append(best)
    else:
        names = set(args)
        found = set()
        for m in ms:
            if in_class(m):
                nm = member_name(m[3])
                if nm in names:
                    remove.append(m)
                    found.add(nm)
        for nm in names - found:
            print(f"!! not found: {nm}", file=sys.stderr)

    # drop nested duplicates (removing outer covers inner)
    remove = sorted(set(remove), key=lambda m: (m[0], -m[1]))
    final = []
    for m in remove:
        if final and m[0] >= final[-1][0] and m[1] <= final[-1][1]:
            continue
        final.append(m)
    for s, e, d, h in reversed(final):
        print(f"- {path.split('/')[-1]}:{line_of(src, s + len(h) - len(h.lstrip()))} {member_name(h)}", file=sys.stderr)
        # remove whole lines where possible
        ls = src.rfind("\n", 0, s + len(h) - len(h.lstrip())) + 1
        if src[s:ls].strip() == "":
            s2 = s
        else:
            s2 = s
        le = src.find("\n", e)
        tail = src[e:le] if le >= 0 else src[e:]
        if tail.strip() == "":
            e = le if le >= 0 else len(src)
        src = src[:s2] + src[e:]
    open(path, "w", encoding="utf-8").write(src)


if __name__ == "__main__":
    main()
