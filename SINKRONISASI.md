# Sinkronisasi CekDroid: commit otomatis, push lewat kamu.

Repo ini dikerjakan lewat sesi agen (DeepSeek Harness). Sandbox agen bisa
menulis file dan membuat commit di repo ini, **tetapi tidak bisa push** ke
GitHub: kredensial Git Anda tinggal di luar jangkauan sandbox
(`C:\Users\chare\.git-credentials` ditolak, Credential Manager tidak terbaca).

Jadi pembagiannya:

| Siapa | Tugas | Otomatis? |
|---|---|---|
| Agen | ubah kode + `git commit` | ya, tiap ada perubahan |
| Kamu | jalankan `.\sync.ps1` | satu langkah |

## Alur harian

```powershell
cd C:\Users\chare\Documents\deepseek-harness\default-workspace\cekdroid-repo
.\sync.ps1
```

`sync.ps1` akan:

1. commit sisa perubahan yang belum ter-commit (dengan pesan otomatis),
2. `git push origin main`,
3. menulis **`SYNC.md`** berisi bukti: hash lokal, hash remote, status sinkron.

## Cara agen memeriksa tanpa kredensial

Setelah `sync.ps1` jalan, agen membaca `SYNC.md` dan `STATUS.log` — keduanya
di-commit dan ikut ter-push, jadi agen tahu persis apa yang ada di GitHub tanpa
pernah perlu koneksi.

Cukup bilang: **"cek SYNC.md"**.

## Kalau push gagal

`sync.ps1` akan menyebut penyebabnya. Yang paling sering:

- **belum login** → jalankan `gh auth login`, atau pastikan Git Credential
  Manager aktif saat push pertama,
- **repo belum ada** → buat di https://github.com/new, nama `cekdroid`,
  visibilitas Private, tanpa README/gitignore,
- **TLS Windows rusak** → `git config --global http.sslBackend openssl`.