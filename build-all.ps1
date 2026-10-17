param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)
$ErrorActionPreference = 'Stop'
$RepositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @('buildAndCollect')
}
foreach ($Generation in @('old', '1.21.X', '26.X')) {
    Write-Host "==> Additional Pipes $Generation: $($GradleArgs -join ' ')"
    Push-Location (Join-Path $RepositoryRoot "builds/$Generation")
    try {
        & .\gradlew.bat @GradleArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    finally { Pop-Location }
}
