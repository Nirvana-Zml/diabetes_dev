param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

if ($MavenArgs.Count -eq 0) {
    $MavenArgs = @("-version")
}

$mvnCmd = "mvn " + ($MavenArgs -join ' ')
Write-Host "==> $mvnCmd" -ForegroundColor Cyan

docker compose -f docker-compose.maven.yml run --rm --entrypoint sh maven -c $mvnCmd
exit $LASTEXITCODE
