# CekDroid

Diagnosa dan perbaikan kesehatan perangkat Android, dari PC lewat ADB, plus app
companion untuk diagnosa mandiri di HP.

Dibangun untuk bekerja di **Android apa saja** (minSdk 26) — hasil pembacaan
tidak diasumsikan terhadap vendor tertentu, dan perbedaan format `dumpsys`
ditangani per bagian.

## Kenapa hybrid

Perintah yang paling berguna (`am set-standby-bucket`, `cmd deviceidle`,
`settings put`) butuh privilege **UID 2000 (shell)**. App biasa tidak punya itu.
Jadi:

| Bagian | Privilege | Bisa eksekusi? |
|---|---|---|
| CLI Python (`cekdroid`) | shell penuh via ADB | ya |
| APK companion | app biasa (public API) | tidak, diagnosa saja |

APK tetap berguna saat tidak ada PC: membaca baterai, RAM, storage, suhu, paket
sideload, accessibility, dan pemakaian app.

## Mulai

```bash
# diagnosa
python -m cekdroid diagnose

# laporan HTML / JSON
python -m cekdroid diagnose --html laporan.html
python -m cekdroid diagnose --json laporan.json

# lihat semua temuan termasuk yang normal
python -m cekdroid diagnose --all
```

Output teks dirancang untuk dibaca langsung: ringkasan perangkat, lalu temuan
diurutkan menurut keparahan dengan bukti mentah di tiap butir.

## Perbaikan (reversible)

Semua aksi mencatat nilai sebelumnya ke `~/.cekdroid/journal.jsonl` sebelum
menulis, dan bisa dibatalkan.

```bash
# batasi background app (standby bucket + appops)
python -m cekdroid fix --restrict-bg com.instagram.android

# cabut dari Doze whitelist
python -m cekdroid fix --doze-unexempt com.facebook.katana

# perpendek screen timeout
python -m cekdroid fix --screen-timeout 60000

# battery saver
python -m cekdroid fix --battery-saver on

# lihat rencana tanpa mengubah apa pun
python -m cekdroid fix --restrict-bg com.instagram.android --dry-run

# batalkan aksi terakhir
python -m cekdroid undo

# riwayat
python -m cekdroid journal
```

`fix --from-report` menyusun rencana dari hasil diagnosa (app media/sosial berat,
Doze exemption pihak ketiga, screen timeout panjang), menampilkan daftarnya, dan
meminta konfirmasi sebelum jalan.

### Batasan yang dilaporkan apa adanya

Beberapa hal **tidak bisa** dilakukan tanpa root, dan tool akan mengatakannya
alih-alih berpura-pura berhasil:

- `system` / `system-excidle` di Doze whitelist — ditanam di level sistem.
  Kompensasinya: `RUN_ANY_IN_BACKGROUND=ignore`.
- Bucket `RESTRICTED` (45) kadang di-clamp vendor ke `RARE` (40). Tool
  melaporkan ini sebagai berhasil-dengan-catatan karena RARE tetap restriksi.

## App companion

```bash
# build (butuh Android SDK + JDK, tanpa Gradle)
python build_apk.py

# pasang, jalankan, tarik laporan
python -m cekdroid app

# hanya pasang / hapus
python -m cekdroid app --install
python -m cekdroid app --uninstall
```

APK dibangun dengan `aapt2 → javac → d8 → zipalign → apksigner`, jadi tidak
memerlukan Gradle atau resolusi dependency. Ukuran ~180 KB, tidak ada AndroidX.

### Debug vs rilis

Build default **debuggable**, dan itu disengaja: `DumpActivity` menulis laporan
ke `files/` yang dibaca CLI lewat `adb run-as`, dan `run-as` hanya bekerja pada
build debuggable. Konsekuensinya siapa pun yang memegang kabel ADB bisa membaca
data internal app ini.

Untuk membagikan APK ke orang lain, bangun mode rilis:

```bash
python build_apk.py --release     # butuh CEKDROID_KS_PASS + keystore
```

Mode rilis tidak debuggable, jadi `python -m cekdroid app` (yang bergantung pada
`run-as`) tidak akan bisa menarik laporan dari build itu — pakai `diagnose` yang
membaca perangkat lewat ADB langsung.

Untuk statistik pemakaian per app, beri izin satu kali:

```bash
adb shell appops set ai.cekdroid GET_USAGE_STATS allow
```

## Tab aplikasi

App mengikuti urutan tab DevInfo: **Dasbor, Perangkat, Sistem, CPU, Lokasi,
Jaringan, Penyimpanan, Baterai, Layar, Kamera, Sensor, Aplikasi, Tes, Alat**.

Beberapa tab menggabungkan lebih dari satu blok data (Perangkat = identitas +
pengenal + kemampuan; CPU = CPU + GPU; Jaringan = jaringan + SIM; Penyimpanan =
penyimpanan + memori; Aplikasi = app + codec). JSON tetap menulis satu blok per
kolektor dengan id yang sama, jadi konsumen skema tidak berubah.

## Arsitektur

```
cekdroid/
  adb.py       transport, deteksi adb lintas OS, push probe
  probe.sh     pembaca read-only, output ber-delimiter @@section
  parser.py    parser defensif per vendor -> dataclass
  rules.py     rule engine -> Finding(severity, evidence, task)
  actions.py   aksi reversible + journal undo
  render.py    teks / JSON (cekdroid.report.v2) / HTML
  cli.py       argparse

cekdroid-android/
  src/ai/cekdroid/
    MainActivity.java      shell tab + dasbor
    DumpActivity.java      headless dump untuk CLI
    collect/              satu kelas per kolektor data
    ui/Tabs.java          urutan & pengelompokan tab
    ui/TabPage.java       renderer tab gabungan
    test/                 layar tes hardware dan alat
```

Skema JSON `cekdroid.report.v2` dipakai kedua sisi; field yang tidak tersedia
bernilai `null`/absen, tidak pernah ditebak.

## Yang dinilai

Baterai (level, suhu, health, cycle count, wear dari `charge_full`/`_design`),
memori (RAM tersedia, swap, `allocstall`, PSI), storage (sisa, inode, ukuran
app/data/foto), CPU (load, idle, frekuensi, proses D/Z), thermal (zona + status
HAL), power (screen timeout, battery saver, doze), keamanan (patch age,
verified boot, root, accessibility aktif, device admin, sideload), dan paket.

## Keterbatasan

- Probe butuh shell yang tidak dibatasi. Beberapa ROM enterprise mematikan
  `dumpsys` untuk shell; tool akan melaporkan probe tidak selesai.
- Thermal sysfs hanya terbaca lewat ADB; dari app biasa dibatasi SELinux.
- Cycle count baterai tidak diekspos semua kernel MediaTek, apa pun metodenya.
- Uji nyata baru pada OPPO CPH2269 / Android 11. Parser ditulis defensif tapi
  belum divalidasi pada Samsung, Xiaomi, Vivo, atau Pixel.
