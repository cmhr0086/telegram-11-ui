#!/bin/sh
# Compile tgui and dump javac errors to build/errors.txt
cd "$(dirname "$0")/.."
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mkdir -p build && ./gradlew -q --console=plain :tgui:compileDebugJavaWithJavac > build/build-out.txt 2>&1
status=$?

grep -E "^/.*\.java:[0-9]+: error:" build/build-out.txt > build/errors.txt
echo "exit=$status errors=$(wc -l < build/errors.txt)"
exit $status
