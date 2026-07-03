# 构建前端生产包并切换 Nginx 为静态托管（供 Ngrok 外网演示）
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

function Build-Frontend($Name) {
    $Dir = Join-Path $Root $Name
    Write-Host "==> Building $Name ..."
    docker run --rm `
        -v "${Dir}:/app" `
        -w /app `
        node:20-alpine `
        sh -c "npm ci && npm run build"
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $Name" }
}

Build-Frontend "frontend"
Build-Frontend "admin-frontend"

Write-Host ""
Write-Host "Build complete. Restart stack with Ngrok profile:"
Write-Host "  docker compose -f docker-compose.yml -f docker-compose.ngrok.yml up -d nginx"
Write-Host ""
Write-Host "Then expose port 80:"
Write-Host "  ngrok http 80"
