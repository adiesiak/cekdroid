# Sinkronisasi CekDroid ke GitHub.
#
# Penggunaan:
#   .\sync.ps1                  -> commit sisa perubahan, push, tulis SYNC.md
#   .\sync.ps1 "pesan commit"   -> sama, dengan pesan kustom
#
# Setelah selesai, berkas SYNC.md berisi bukti sinkronisasi yang bisa dibaca
# agen (agen tidak punya akses kredensial Git, jadi ini jalur pemeriksaannya).

param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Message)

Set-Location $PSScriptRoot
$ErrorActionPreference = 'Stop'

function Log($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

Log "== CekDroid sync ==" DarkCyan

# 1. Commit sisa perubahan.
git add -A
$staged = @(git diff --cached --name-only)
if ($staged.Count -gt 0) {
    $msg = if ($Message) { $Message -join ' ' } else { "sync: $(Get-Date -Format 'yyyy-MM-dd HH:mm')" }
    git commit -q -m $msg
    if ($LASTEXITCODE -ne 0) { Log "[commit] GAGAL" Red; exit 1 }
    Log "[commit] $msg" Green
    $staged | ForEach-Object { Log "         + $_" DarkGray }
} else {
    Log "[commit] tidak ada perubahan baru" DarkGray
}

$local = (git rev-parse --short HEAD).Trim()

# 2. Push.
Log "[push]   mengirim ke $(git remote get-url origin) ..." Gray
# git menulis progres ke stderr; Windows PowerShell 5.1 mengubah baris stderr
# native command menjadi ErrorRecord, sehingga 2>&1 saja tetap memunculkan
# "git : To https://..." merah. Arahkan ke stdout di level proses lalu pecah
# per baris supaya tidak ada objek error yang lolos.
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$pushOut = (& cmd /c "git push origin main 2>&1") | Out-String
$ErrorActionPreference = $prevEap
$pushCode = $LASTEXITCODE
$pushOut -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^(remote:|To https://)' } |
    ForEach-Object { Log "         $_" DarkGray }

if ($pushCode -ne 0) {
    Log "[push]   GAGAL (exit $pushCode). Lihat SINKRONISASI.md untuk penyebab umum." Red
    @(
        '# Hasil sinkronisasi CekDroid'
        ''
        "- **Status:** GAGAL"
        "- **Waktu:** $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        "- **Commit lokal:** ``$local``"
        ''
        '```'
        ($pushOut -join "`n")
        '```'
    ) | Set-Content SYNC.md -Encoding utf8
    exit 1
}

# 3. Tulis bukti sinkronisasi.
$remote = (git rev-parse --short origin/main 2>$null)
if (-not $remote) { $remote = 'tidak terbaca' }
$clean = if (@(git status --porcelain).Count -eq 0) { 'bersih' } else { 'ada perubahan belum ter-commit' }
$total = @(git rev-list --count HEAD)[0]
$url = (git remote get-url origin) -replace '\.git$', ''

@(
    '# Hasil sinkronisasi CekDroid'
    ''
    "- **Status:** SUKSES"
    "- **Waktu:** $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    "- **Commit lokal (HEAD):** ``$local``"
    "- **Commit remote (origin/main):** ``$remote``"
    "- **Total commit:** $total"
    "- **Working tree:** $clean"
    "- **Repo:** $url"
    ''
    'Dibuat otomatis oleh `sync.ps1`. Commit ini ikut ter-push, sehingga agen'
    'dapat memverifikasi isi GitHub cukup dengan membaca berkas ini.'
) | Set-Content SYNC.md -Encoding utf8

git add SYNC.md
git commit -q -m "sync: catat hasil sinkronisasi $local"
if ($LASTEXITCODE -eq 0) { $null = & cmd /c "git push origin main 2>&1" }

Log "[push]   sukses -> $local" Green
Log "[bukti]  SYNC.md ditulis. Bilang 'cek SYNC.md' ke agen." Green
