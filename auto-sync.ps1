# Auto-sync CekDroid: push otomatis dari luar sandbox agen.
# Dijalankan oleh scheduled task. Aman untuk dijalankan berulang.
$repo = 'C:\Users\chare\Documents\deepseek-harness\default-workspace\cekdroid-repo'
$log  = Join-Path $repo 'auto-sync.log'
Set-Location $repo

function Say($m) { "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $m" | Tee-Object -FilePath $log -Append | Out-Null }

# 1. Ambil commit terbaru dari agen (repo ini bukan layanan, jadi fetch diabaikan).
# 2. Sinkronkan commit yang belum terkirim.
#    Kalau upstream belum ada atau rev-list gagal, jangan berhenti diam-diam:
#    tetap lanjut supaya commit baru tidak tertinggal tanpa jejak.
$pendingRaw = (& cmd /c "git rev-list --count @{u}..HEAD 2>&1") | Out-String
$pending = ($pendingRaw -split "`r?`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
if ($pending -eq '0') { return }

git add -A
if (@(git diff --cached --name-only).Count -gt 0) {
    git commit -q -m "auto-sync: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
}

$out = (& cmd /c "git push origin main 2>&1") | Out-String
if ($LASTEXITCODE -eq 0) {
    Say "OK $((git rev-parse --short HEAD))"
} else {
    Say "GAGAL: $($out.Trim() -replace '\s+', ' ')"
}
