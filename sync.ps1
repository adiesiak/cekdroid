# Sinkronisasi CekDroid ke GitHub.
# Penggunaan: .\sync.ps1   (atau klik kanan > Run with PowerShell)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

git add -A
$staged = git diff --cached --name-only
if ($staged) {
    $msg = if ($args.Count -gt 0) { $args -join ' ' } else { "sync: $(Get-Date -Format 'yyyy-MM-dd HH:mm')" }
    git commit -q -m $msg
    Write-Host "[commit] $msg" -ForegroundColor Green
    $staged | ForEach-Object { Write-Host "         + $_" }
} else {
    Write-Host "[commit] tidak ada perubahan baru" -ForegroundColor DarkGray
}

git push origin main
if ($LASTEXITCODE -eq 0) {
    Write-Host "[push]  sukses -> $(git remote get-url origin)" -ForegroundColor Green
} else {
    Write-Host "[push]  GAGAL. Jalankan 'gh auth login' atau cek token, lalu ulangi." -ForegroundColor Red
    exit 1
}
