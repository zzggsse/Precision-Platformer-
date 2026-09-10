# Resolves the JDK used by this project.
#
# Priority: $env:PIXELPERIL_JDK  ->  D:\java\JDK21  ->  $env:JAVA_HOME  ->  javac on PATH
#
# NOTE: on this machine JAVA_HOME points at JDK 19 (EOL) while PATH points at JDK 21.
# We therefore prefer JDK 21 explicitly so build and runtime use the same version.
#
# Keep this file ASCII-only: Windows PowerShell may parse .ps1 as the ANSI codepage,
# which corrupts non-ASCII bytes and breaks the parser (learned the hard way).
$ErrorActionPreference = 'Stop'

$candidates = @()
if ($env:PIXELPERIL_JDK) { $candidates += $env:PIXELPERIL_JDK }
$candidates += 'D:\java\JDK21'
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

$jdk = $null
foreach ($c in $candidates) {
    if ($c -and (Test-Path (Join-Path $c 'bin\javac.exe'))) { $jdk = $c; break }
}
if (-not $jdk) {
    $onPath = Get-Command javac -ErrorAction SilentlyContinue
    if ($onPath) { $jdk = Split-Path (Split-Path $onPath.Source -Parent) -Parent }
}
if (-not $jdk) {
    throw "No JDK found. Install JDK 17+ and point PIXELPERIL_JDK at it."
}

$script:Jdk       = $jdk
$script:Java      = Join-Path $jdk 'bin\java.exe'
$script:Javac     = Join-Path $jdk 'bin\javac.exe'
$script:Root      = Split-Path $PSScriptRoot -Parent
$script:SrcDir    = Join-Path $script:Root 'src'
$script:ToolDir   = Join-Path $script:Root 'tools'
$script:LibsDir   = Join-Path $script:Root 'libs'
$script:AssetsDir = Join-Path $script:Root 'assets'
$script:OutDir    = Join-Path $script:Root 'out'
$script:MainClass = 'pixelperil.Main'
