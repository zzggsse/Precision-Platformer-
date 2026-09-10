# Runs the headless self-test: drives the simulation with scripted input and
# asserts physics, hazards, respawn and determinism. No window / GL required.
. "$PSScriptRoot\env.ps1"

& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$cp = "$OutDir;$AssetsDir;$LibsDir\*"

Write-Host ""
& $Java '-Dfile.encoding=UTF-8' -cp $cp pixelperil.SelfTest
exit $LASTEXITCODE
