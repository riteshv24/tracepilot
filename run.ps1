$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw "Install JDK 21 or newer and add its bin directory to PATH." }
New-Item -ItemType Directory -Force .build/classes | Out-Null
$sources = Get-ChildItem engine/src/main/java,standalone/src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d .build/classes $sources
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed." }
& java --add-modules jdk.httpserver,jdk.compiler -cp .build/classes dev.portfolio.launcher.Main @args
exit $LASTEXITCODE
