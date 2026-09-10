# Compiles src\ into out\ using javac directly (no Gradle needed).
#
# The assets folder is put on the runtime classpath by run.ps1, which is how
# Gdx.files.internal("levels/level1.tmx") finds assets\levels\level1.tmx.
. "$PSScriptRoot\env.ps1"

if (-not (Test-Path $LibsDir)) {
    throw "libs\ not found. Run scripts\fetch-deps.ps1 first."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$sources = Get-ChildItem -Path $SrcDir -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if (-not $sources -or $sources.Count -eq 0) {
    throw "No .java files under $SrcDir"
}

# javac @argfile avoids command-line length limits as the project grows.
$listFile = Join-Path $OutDir 'sources.txt'
$sources | Set-Content -Path $listFile -Encoding ascii

Write-Host "JDK    : $Jdk"
Write-Host "sources: $($sources.Count) files"
Write-Host "output : $OutDir"
Write-Host ""

# -encoding UTF-8 is required: the sources carry Chinese comments and javac
# would otherwise read them with the platform codepage (GBK) and fail.
& $Javac -encoding UTF-8 -d $OutDir -cp "$LibsDir\*" -Xlint:all "@$listFile"
$code = $LASTEXITCODE
if ($code -eq 0) { Write-Host "`nBUILD OK" -ForegroundColor Green }
else { Write-Host "`nBUILD FAILED (exit $code)" -ForegroundColor Red }
exit $code
