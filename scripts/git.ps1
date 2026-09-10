# Git wrapper script.
#
# Why this wrapper exists instead of calling git directly:
#   1) There is no system-wide git on this machine. A portable MinGit lives in
#      .tools\mingit (see docs\DEVELOPMENT.md).
#   2) MinGit ships an MSYS ssh.exe which cannot start in this restricted
#      environment:
#        "fatal error - couldn't create signal pipe, Win32 error 5"
#      (MSYS ssh uses a named pipe for signal handling and named pipes are
#       blocked here.) So we force the system OpenSSH client instead.
#
# Usage -- arguments are passed straight through to git:
#   scripts\git.ps1 status
#   scripts\git.ps1 add -A
#   scripts\git.ps1 commit -F out\commitmsg.txt     # UTF-8 message file, safest
#   scripts\git.ps1 push
#   scripts\git.ps1 log --oneline -5
#
# NOTE: this file must stay ASCII-only. Windows PowerShell parses .ps1 using the
# ANSI codepage, so non-ASCII comments get mangled into stray quotes and the
# parser dies with a confusing "string is missing the terminator" error.
# Chinese explanations live in docs\DEVELOPMENT.md instead.
#
# NOTE: $ErrorActionPreference is deliberately left at Continue. git writes push
# progress to stderr, and under 'Stop' PowerShell treats native stderr output as
# a terminating error -- which aborts the script *after* a successful push and
# makes it look like a failure.
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GitArgs
)

$ErrorActionPreference = 'Continue'

$root = Split-Path $PSScriptRoot -Parent

# Prefer a system git if one ever gets installed; fall back to the portable copy.
$git = $null
$onPath = Get-Command git -ErrorAction SilentlyContinue
if ($onPath) { $git = $onPath.Source }
if (-not $git) {
    $portable = Join-Path $root '.tools\mingit\cmd\git.exe'
    if (Test-Path $portable) { $git = $portable }
}
if (-not $git) {
    throw "git not found. Install Git for Windows, or see the 'portable git' steps in docs\DEVELOPMENT.md."
}

$systemSsh = 'C:\Windows\System32\OpenSSH\ssh.exe'
if (Test-Path $systemSsh) { $env:GIT_SSH = $systemSsh }

& $git @GitArgs
exit $LASTEXITCODE
