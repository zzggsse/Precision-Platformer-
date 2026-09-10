# Compiles an ASCII level sketch into a Tiled TMX file with tools\GenLevel.java.
#
# The tool uses the tile id constants from src\pixelperil\level\Tiles.java, so the
# project must be compiled first (out\ on the classpath) to keep ids in sync.
#
# NOTE: parameter names must avoid PowerShell automatic variables -- naming one
# of them $Input silently fails to bind (it is the pipeline enumerator), which
# shows up as a confusing AccessDeniedException. Hence $AsciiFile / $TmxFile.
param(
    [string]$AsciiFile = 'assets\levels\level1.ascii',
    [string]$TmxFile   = 'assets\levels\level1.tmx'
)

. "$PSScriptRoot\env.ps1"

if (-not (Test-Path $LibsDir)) {
    throw "libs\ not found. Run scripts\fetch-deps.ps1 first."
}

& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$inPath  = Join-Path $Root $AsciiFile
$outPath = Join-Path $Root $TmxFile
$tileset = Join-Path $AssetsDir 'tiles\tiles.png'

if (-not (Test-Path $inPath)) { throw "ASCII level not found: $inPath" }

Write-Host ""
& $Java '-Dfile.encoding=UTF-8' -cp "$OutDir;$LibsDir\*" "$ToolDir\GenLevel.java" $inPath $outPath $tileset
exit $LASTEXITCODE
