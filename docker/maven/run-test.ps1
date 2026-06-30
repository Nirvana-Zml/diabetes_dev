param(
    [Parameter(Mandatory = $true)]
    [string]$Service,

    [switch]$Verify
)

$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

$goal = if ($Verify) { "verify" } else { "test" }
$installCmd = "mvn -pl $Service -am install -Dmaven.test.skip=true -B"
$testCmd = "mvn -pl $Service $goal -B"

Write-Host "==> 构建依赖: $installCmd" -ForegroundColor Cyan
docker compose -f docker-compose.maven.yml run --rm --entrypoint sh maven -c $installCmd
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> 运行测试: $testCmd" -ForegroundColor Cyan
docker compose -f docker-compose.maven.yml run --rm --entrypoint sh maven -c $testCmd
exit $LASTEXITCODE
