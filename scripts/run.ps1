# Builds (if needed) and launches the game window.
#
# Example:
#   scripts\run.ps1                                  # normal run
#   scripts\run.ps1 -DebugBoxes                      # start with F1 hitbox view on
#   scripts\run.ps1 -Screenshot docs\shot.png -ShotFrame 40
#   scripts\run.ps1 -Level levels\level2.tmx -SkipBuild
#
# NOTE: the flag is -DebugBoxes rather than -Debug on purpose: -Debug is a
# reserved common parameter name in PowerShell and collides confusingly.
param(
    [switch]$SkipBuild,
    [string]$Level = 'levels/level1.tmx',
    [switch]$DebugBoxes,
    [string]$Screenshot = '',
    [int]$ShotFrame = 30
)

. "$PSScriptRoot\env.ps1"

if (-not $SkipBuild) {
    & "$PSScriptRoot\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# The level path is relative to the assets folder and uses forward slashes.
$Level = $Level -replace '\\', '/'
$appArgs = @("--level=$Level")
if ($DebugBoxes) { $appArgs += '--debug' }
if ($Screenshot) {
    $appArgs += "--screenshot=$Screenshot"
    $appArgs += "--shot-frame=$ShotFrame"
}

# ; is the Windows classpath separator.
# assets\ is on the classpath so Gdx.files.internal resolves level/tile paths.
$cp = "$OutDir;$AssetsDir;$LibsDir\*"

Write-Host "launching $MainClass $($appArgs -join ' ')`n"
& $Java '-Dfile.encoding=UTF-8' -cp $cp $MainClass @appArgs
exit $LASTEXITCODE
