$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force .build/tests | Out-Null
$sources = Get-ChildItem engine/src/main/java,standalone/src/main/java,tests -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d .build/tests $sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
& java --add-modules jdk.httpserver,jdk.compiler -cp .build/tests dev.portfolio.tests.AllTests
if ($LASTEXITCODE -ne 0) { throw "Java tests failed" }
if (Get-Command node -ErrorAction SilentlyContinue) { & node --test (Get-ChildItem web/tests/*.test.mjs).FullName; if ($LASTEXITCODE -ne 0) { throw "Browser-module tests failed" } }
