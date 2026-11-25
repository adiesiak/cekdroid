# CekDroid

Alat diagnosa dan perbaikan kesehatan perangkat Android: satu CLI Python yang
bekerja dari PC lewat ADB, plus app companion untuk diagnosa mandiri di HP.

Dokumentasi lengkap — arsitektur, skema laporan `cekdroid.report.v2`,
batasan tanpa root, dan panduan build APK — ada di
[`cekdroid/README.md`](cekdroid/README.md).

## Isi repo

| Path | Isi |
|---|---|
| `cekdroid/` | Package Python: transport ADB, probe, parser, rule engine, aksi reversible, renderer |
| `cekdroid-android/` | Sumber APK companion (`src/`, `res/`, `AndroidManifest.xml`) |
| `tools/build_apk.py` | Build APK tanpa Gradle: `aapt2 → javac → d8 → zipalign → apksigner` |

## Cepat mulai

```bash
# diagnosa perangkat yang tersambung
python -m cekdroid diagnose

# laporan HTML
python -m cekdroid diagnose --html laporan.html

# build + pasang APK companion
python tools/build_apk.py
python -m cekdroid app
```

Butuh Python 3.10+ dan `adb` (platform-tools). Build APK butuh Android SDK
build-tools + JDK 8+.

## Prinsip

- **Read-only secara default.** `diagnose` tidak mengubah apa pun di perangkat.
- **Aksi selalu reversible.** Setiap perubahan mencatat nilai sebelumnya ke
  `~/.cekdroid/journal.jsonl` sebelum ditulis, dan bisa dibatalkan dengan
  `python -m cekdroid undo`.
- **Tidak menebak.** Field yang tidak tersedia bernilai `null`/absen, dan
  batasan tanpa root dilaporkan apa adanya alih-alih pura-pura berhasil.
- **Tidak vendor-spesifik.** Parser ditangani per bagian karena format
  `dumpsys` berbeda antar produsen.