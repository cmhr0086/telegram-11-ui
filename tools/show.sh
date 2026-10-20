#!/bin/sh
# show.sh File.java start end  — print lines with numbers
f=$(find "$(dirname "$0")/../tgui/src/main/java" -name "$1.java" | head -1)
awk -v a="$2" -v b="$3" 'NR>=a && NR<=b {printf "%d: %s\n", NR, $0}' "$f"
