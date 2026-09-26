#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "Install JDK 21 or newer (a JRE alone is not enough)." >&2; exit 1; }
mkdir -p .build/classes
find engine/src/main/java standalone/src/main/java -name '*.java' | sort > .build/sources.txt
javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d .build/classes @.build/sources.txt
exec java --add-modules jdk.httpserver,jdk.compiler -cp .build/classes dev.portfolio.launcher.Main "$@"
