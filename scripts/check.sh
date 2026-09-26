#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .build/tests
find engine/src/main/java standalone/src/main/java tests -name '*.java' | sort > .build/test-sources.txt
javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d .build/tests @.build/test-sources.txt
java --add-modules jdk.httpserver,jdk.compiler -cp .build/tests dev.portfolio.tests.AllTests
if command -v node >/dev/null; then node --test web/tests/*.test.mjs; else echo 'Node not found; browser-module tests skipped.'; fi
