# Generates the placeholder art with tools\GenAssets.java.
# Output: assets\tiles\tiles.png, assets\sprites\kid.png
#
# Needs libGDX's native pixmap library (no GL context / window required), so the
# dependencies must already be present. Safe to re-run: it overwrites.
. "$PSScriptRoot\env.ps1"

if (-not (Test-Path $LibsDir)) {
    throw "libs\ not found. Run scripts\fetch-deps.ps1 first."
}

& $Java '-Dfile.encoding=UTF-8' -cp "$LibsDir\*" "$ToolDir\GenAssets.java" $Root
exit $LASTEXITCODE
