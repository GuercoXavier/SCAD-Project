# ============================================
# SCAD - DEMONSTRACAO INTEGRADA
# ============================================

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  SCAD - DEMO INTEGRADA (5 COMPONENTES)" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# ---- 1. HEALTH CHECKS ----
Write-Host "[1/5] Health checks..." -ForegroundColor Yellow
$tudoOk = $true

try {
    Invoke-WebRequest -Uri "http://localhost:8888" -TimeoutSec 3 -UseBasicParsing | Out-Null
    Write-Host "  [OK] SeaweedFS (DFS) na porta 8888" -ForegroundColor Green
} catch {
    Write-Host "  [X] SeaweedFS NAO esta a correr! Corre: weed.exe mini -dir=C:\seaweed-data" -ForegroundColor Red
    $tudoOk = $false
}

try {
    Invoke-WebRequest -Uri "http://localhost:8080/ws/fornecedor.wsdl" -TimeoutSec 3 -UseBasicParsing | Out-Null
    Write-Host "  [OK] Spring Boot (REST+SOAP) na porta 8080" -ForegroundColor Green
} catch {
    Write-Host "  [X] Spring Boot NAO esta a correr!" -ForegroundColor Red
    $tudoOk = $false
}

try {
    # CORBA Name Service (orbd)
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("localhost", 1050)
    $tcp.Close()
    Write-Host "  [OK] CORBA Name Service na porta 1050" -ForegroundColor Green
} catch {
    Write-Host "  [AVISO] CORBA Name Service nao detetado (opcional para este teste)" -ForegroundColor DarkYellow
}

if (-not $tudoOk) {
    Write-Host "`nCorrige os servicos em falta antes de continuar." -ForegroundColor Red
    exit 1
}

# ---- 2. STOCK INICIAL ----
Write-Host "`n[2/5] Stock inicial (via REST):" -ForegroundColor Yellow
$produtos = Invoke-RestMethod -Uri "http://localhost:8080/api/produtos" -Method Get
$produtos | Format-Table id, nome, quantidade, regiao -AutoSize

# ---- 3. COMPRA INTEGRADA ----
Write-Host "[3/5] A executar COMPRA INTEGRADA (2PC + DFS + REST/SOAP)..." -ForegroundColor Yellow
$body = @{
    produto    = "Parafusos"
    quantidade = 10
    fornecedor = "Fornecedor ABC"
} | ConvertTo-Json

$resultado = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/compras/integrar" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

Write-Host ""
Write-Host "  RESULTADO DA COMPRA INTEGRADA:" -ForegroundColor Cyan
Write-Host "  ----------------------------------------" -ForegroundColor Cyan
Write-Host "  Status geral: $($resultado.status)" -ForegroundColor $(if ($resultado.status -eq 'SUCESSO') {'Green'} else {'Red'})
Write-Host ""
Write-Host "  Passos executados:" -ForegroundColor Yellow
$resultado.passos.PSObject.Properties | ForEach-Object {
    $cor = if ($_.Value -match "ERRO") { "Red" } else { "Green" }
    Write-Host "    $($_.Name): $($_.Value)" -ForegroundColor $cor
}

if ($resultado.faturaId) {
    Write-Host "`n  Fatura guardada no DFS: $($resultado.faturaId)" -ForegroundColor Green
    Write-Host "  Log guardado no DFS:    $($resultado.logId)" -ForegroundColor Green
}

# ---- 4. STOCK POS-COMPRA ----
Write-Host "`n[4/5] Stock após compra:" -ForegroundColor Yellow
$produtos = Invoke-RestMethod -Uri "http://localhost:8080/api/produtos" -Method Get
$produtos | Format-Table id, nome, quantidade, regiao -AutoSize

# ---- 5. VERIFICACAO DFS ----
Write-Host "[5/5] Verificacao do DFS:" -ForegroundColor Yellow
Write-Host "  Filer UI:  http://localhost:8888" -ForegroundColor Cyan
Write-Host "  Master UI: http://localhost:9333" -ForegroundColor Cyan
Write-Host "  (Confirma que o ficheiro $($resultado.faturaId) aparece em scad-fs/faturas/)" -ForegroundColor Cyan

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  DEMO CONCLUIDA" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""