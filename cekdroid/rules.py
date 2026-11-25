"""Rule engine: turn a parsed Report into findings with severity and advice.

Rules are vendor-agnostic and evidence-based. Each finding carries the raw
evidence that triggered it so the report never asserts something it cannot
show.
"""

from __future__ import annotations

import datetime as _dt
from dataclasses import dataclass, field

from .parser import Report

SEV_CRITICAL = "critical"
SEV_WARN = "warn"
SEV_INFO = "info"
SEV_OK = "ok"

_ORDER = {SEV_CRITICAL: 0, SEV_WARN: 1, SEV_INFO: 2, SEV_OK: 3}


@dataclass
class Finding:
    id: str
    title: str
    severity: str
    detail: str
    evidence: list[str] = field(default_factory=list)
    action: str | None = None
    task: int | None = None


def _gb(kb: int | None) -> str:
    if kb is None:
        return "?"
    return f"{kb / 1024 / 1024:.1f} GB"


def _mb(kb: int | None) -> str:
    if kb is None:
        return "?"
    return f"{kb / 1024:.0f} MB"


def _patch_age_days(patch: str | None) -> int | None:
    if not patch:
        return None
    try:
        d = _dt.date.fromisoformat(patch.strip())
    except ValueError:
        return None
    return (_dt.date.today() - d).days


def _zombie_and_dstate(rep: Report) -> tuple[list[str], list[str]]:
    d_state = [p.name for p in rep.processes if p.state == "D"]
    zombies = [p.name for p in rep.processes if p.state == "Z"]
    return d_state, zombies


def evaluate(rep: Report) -> list[Finding]:
    out: list[Finding] = []
    b, m, s, c, pw, sec = (
        rep.battery, rep.memory, rep.storage, rep.cpu, rep.power, rep.security
    )

    # --- Security: patch level -------------------------------------------
    age = _patch_age_days(sec.security_patch)
    if age is None:
        out.append(Finding(
            "sec.patch.unknown", "Patch keamanan tidak terbaca", SEV_INFO,
            "Nilai ro.build.version.security_patch kosong atau tidak valid.",
            [f"security_patch={sec.security_patch!r}"],
        ))
    elif age > 365 * 2:
        out.append(Finding(
            "sec.patch.eol", "Perangkat sudah EOL (patch > 2 tahun)", SEV_CRITICAL,
            f"Patch keamanan tertinggal {age} hari. Vendor kemungkinan berhenti "
            "mengirim update. Hindari perangkat ini untuk mobile banking utama.",
            [f"security_patch={sec.security_patch}", f"age_days={age}"],
        ))
    elif age > 180:
        out.append(Finding(
            "sec.patch.stale", "Patch keamanan tertinggal", SEV_WARN,
            f"Patch keamanan {age} hari (lebih dari 6 bulan). Cek update sistem.",
            [f"security_patch={sec.security_patch}", f"age_days={age}"],
        ))
    else:
        out.append(Finding(
            "sec.patch.ok", "Patch keamanan wajar", SEV_OK,
            f"Patch {sec.security_patch}, usia {age} hari.",
            [f"age_days={age}"],
        ))

    # --- Security: integrity ---------------------------------------------
    if sec.boot_verified and sec.boot_verified not in ("green", ""):
        out.append(Finding(
            "sec.boot.tampered", "Verifikasi boot tidak hijau", SEV_CRITICAL,
            "Boot state bukan 'green': bootloader terbuka atau sistem dimodifikasi.",
            [f"verifiedbootstate={sec.boot_verified}"],
        ))
    if sec.su_present is True:
        out.append(Finding(
            "sec.root", "Akses root terdeteksi", SEV_WARN,
            "Biner 'su' ditemukan. Root membatalkan jaminan keamanan sebagian app "
            "perbankan dan membuka permukaan serangan.",
            ["su=present"],
        ))

    # --- Security: accessibility -----------------------------------------
    if sec.accessibility_services:
        out.append(Finding(
            "sec.a11y.active", "Accessibility service aktif", SEV_WARN,
            "Accessibility bisa membaca seluruh layar dan melakukan input. "
            "Pastikan setiap service di daftar ini memang dipasang sendiri.",
            sec.accessibility_services, task=7,
        ))
    else:
        out.append(Finding(
            "sec.a11y.none", "Tidak ada accessibility service", SEV_OK,
            "Tidak ada service yang bisa membaca layar. Ini kondisi aman.",
            [],
        ))

    if sec.device_admins:
        out.append(Finding(
            "sec.admin", "Device admin terpasang", SEV_WARN,
            "Device admin bisa mengunci/menghapus perangkat. Verifikasi pemiliknya.",
            sec.device_admins,
        ))
    else:
        out.append(Finding(
            "sec.admin.none", "Tidak ada device admin", SEV_OK,
            "Tidak ada app dengan hak admin perangkat.", [],
        ))

    # --- Security: sideload ----------------------------------------------
    self_installing = [p for p in rep.packages if p.self_installing]
    unknown = [
        p for p in rep.packages
        if p.installer and p.installer not in
        ("com.android.vending", "com.google.android.packageinstaller", p.name)
    ]
    sideloaded = [p for p in rep.packages if p.sideloaded]
    if self_installing:
        out.append(Finding(
            "sec.sideload.selfinstaller", "App bisa memasang APK sendiri", SEV_WARN,
            "Installer package sama dengan nama package: app ini bisa memperbarui "
            "dirinya di luar Play Store.",
            [f"{p.name} (installer={p.installer})" for p in self_installing],
            task=5,
        ))
    if unknown:
        out.append(Finding(
            "sec.sideload.unknown", "App dengan installer tidak dikenal", SEV_INFO,
            "App ini tidak dipasang lewat Play Store maupun package installer standar.",
            [f"{p.name} (installer={p.installer})" for p in unknown],
        ))
    if sideloaded:
        out.append(Finding(
            "sec.sideload.count", "Aplikasi di luar Play Store", SEV_INFO,
            f"{len(sideloaded)} app terpasang di luar Play Store.",
            [p.name for p in sideloaded[:20]],
            task=5,
        ))

    # --- Battery ----------------------------------------------------------
    if b.health is not None:
        health_txt = str(b.health).strip()
        if health_txt in ("2", "Good", "good"):
            out.append(Finding(
                "bat.health.ok", "Kesehatan baterai baik", SEV_OK,
                f"health={health_txt}, capacity_level={b.capacity_level or '?'}.",
                [f"health={health_txt}"],
            ))
        elif health_txt in ("3", "Overheat", "overheat", "4", "Dead", "dead"):
            out.append(Finding(
                "bat.health.bad", "Baterai bermasalah", SEV_CRITICAL,
                f"health={health_txt}. Pertimbangkan ganti baterai.",
                [f"health={health_txt}"],
            ))

    wear = b.wear_pct
    if wear is not None:
        if wear < 70:
            out.append(Finding(
                "bat.wear.bad", "Kapasitas baterai menurun drastis", SEV_CRITICAL,
                f"Kapasitas terukur {wear}% dari desain. Ganti baterai.",
                [f"charge_full={b.charge_full_uah}", f"design={b.charge_full_design_uah}"],
            ))
        elif wear < 80:
            out.append(Finding(
                "bat.wear.aged", "Baterai sudah menua", SEV_WARN,
                f"Kapasitas {wear}% dari desain.",
                [f"wear_pct={wear}"],
            ))
        else:
            out.append(Finding(
                "bat.wear.ok", "Kapasitas baterai sehat", SEV_OK,
                f"Kapasitas {wear}% dari desain.", [f"wear_pct={wear}"],
            ))

    if b.temperature_c is not None:
        if b.temperature_c >= 45:
            out.append(Finding(
                "bat.temp.hot", "Baterai terlalu panas", SEV_CRITICAL,
                f"{b.temperature_c} °C. Lepas charger dan jauhkan dari panas.",
                [f"temperature_c={b.temperature_c}"],
            ))
        elif b.temperature_c >= 40:
            out.append(Finding(
                "bat.temp.warm", "Baterai hangat", SEV_WARN,
                f"{b.temperature_c} °C. Wajar saat fast-charge, pantau bila menetap.",
                [f"temperature_c={b.temperature_c}"],
            ))

    if b.cycle_count is not None and b.cycle_count > 800:
        out.append(Finding(
            "bat.cycles", "Siklus pengisian tinggi", SEV_INFO,
            f"{b.cycle_count} siklus. Baterai lithium mulai menua di atas ~800 siklus.",
            [f"cycle_count={b.cycle_count}"],
        ))

    # --- Memory -----------------------------------------------------------
    if m.swap_used_pct is not None and m.swap_used_pct >= 40:
        out.append(Finding(
            "mem.swap.pressure", "Tekanan memori tinggi", SEV_WARN,
            f"{_mb(m.swap_used_kb)} dari {_mb(m.swap_total_kb)} swap terpakai "
            f"({m.swap_used_pct}%). App akan sering di-reload saat berpindah.",
            [f"swap_used_pct={m.swap_used_pct}"],
            task=1,
        ))
    elif m.available_kb is not None and m.available_kb < 512 * 1024:
        out.append(Finding(
            "mem.low_available", "RAM tersedia menipis", SEV_WARN,
            f"Hanya {_mb(m.available_kb)} tersedia dari {_gb(m.total_kb)}.",
            [f"available_kb={m.available_kb}"],
            task=1,
        ))

    if m.allocstall and m.allocstall > 5000:
        out.append(Finding(
            "mem.reclaim", "Direct reclaim aktif", SEV_WARN,
            "Kernel memaksa pembebasan page cache langsung (bukan background). "
            "Gejala: UI tersendat, app restart sendiri.",
            [f"allocstall={m.allocstall}", f"pgsteal_direct={m.pgsteal_direct}"],
            task=1,
        ))

    if m.pressure_memory_some_avg10 is not None and m.pressure_memory_some_avg10 > 10:
        out.append(Finding(
            "mem.psi", "PSI memori tinggi", SEV_WARN,
            f"Tekanan memori 10 detik terakhir {m.pressure_memory_some_avg10}%.",
            [f"psi_some_avg10={m.pressure_memory_some_avg10}"],
        ))

    # --- Storage ----------------------------------------------------------
    fp = s.free_pct
    if fp is not None:
        if fp < 5:
            out.append(Finding(
                "sto.critical", "Storage hampir penuh", SEV_CRITICAL,
                f"Sisa {_gb(s.free_kb)} ({fp}%). Sistem bisa gagal menulis.",
                [f"free_pct={fp}"],
            ))
        elif fp < 15:
            out.append(Finding(
                "sto.low", "Storage menipis", SEV_WARN,
                f"Sisa {_gb(s.free_kb)} ({fp}%).",
                [f"free_pct={fp}"],
                task=6,
            ))
        else:
            out.append(Finding(
                "sto.ok", "Storage memadai", SEV_OK,
                f"Sisa {_gb(s.free_kb)} dari {_gb(s.total_kb)} ({fp}%).",
                [f"free_pct={fp}"],
            ))

    if s.inode_used_pct is not None and s.inode_used_pct >= 90:
        out.append(Finding(
            "sto.inodes", "Inode hampir habis", SEV_WARN,
            f"Inode terpakai {s.inode_used_pct}% walau storage mungkin masih lega. "
            "Banyak file kecil (cache chat) penyebabnya.",
            [f"inode_used_pct={s.inode_used_pct}"],
            task=6,
        ))

    if s.app_data_size and s.app_size:
        ratio = s.app_data_size / max(1, s.app_size + s.app_data_size)
        if ratio > 0.5:
            out.append(Finding(
                "sto.appdata", "Data aplikasi mendominasi", SEV_INFO,
                f"App data {_gb(s.app_data_size // 1024)} lebih besar dari APK "
                f"{_gb(s.app_size // 1024)}. Cache chat/foto biasanya penyebab.",
                [f"app_data={s.app_data_size}", f"app_size={s.app_size}"],
                task=6,
            ))

    if s.sdcard_dirs:
        top = s.sdcard_dirs[0]
        if top[0] >= 3000:
            out.append(Finding(
                "sto.sdcard", "Folder besar di penyimpanan bersama", SEV_INFO,
                f"{top[1]} memakai {top[0] / 1024:.1f} GB.",
                [f"{d[1]}={d[0]}MB" for d in s.sdcard_dirs[:5]],
                task=6,
            ))

    # --- CPU / load -------------------------------------------------------
    d_state, zombies = _zombie_and_dstate(rep)
    if c.idle_pct is not None and c.load1 is not None:
        if c.load1 > 8 and c.idle_pct > 50:
            out.append(Finding(
                "cpu.load.artifact", "Load average tinggi tapi CPU idle", SEV_INFO,
                f"Load {c.load1} tapi CPU {c.idle_pct}% idle. Biasanya artefak "
                f"kernel thread vendor ({len(d_state)} proses status D), bukan beban nyata.",
                [f"load1={c.load1}", f"idle_pct={c.idle_pct}", f"d_state={len(d_state)}"],
            ))
        elif c.load1 > 4 and c.idle_pct < 20:
            out.append(Finding(
                "cpu.busy", "CPU sibuk", SEV_WARN,
                f"Load {c.load1}, CPU hanya {c.idle_pct}% idle.",
                [f"load1={c.load1}", f"idle_pct={c.idle_pct}"],
            ))

    if len(zombies) >= 5:
        out.append(Finding(
            "cpu.zombies", "Banyak proses zombie", SEV_INFO,
            f"{len(zombies)} proses zombie. Biasanya sisa proses vendor, tidak berbahaya.",
            zombies[:10],
        ))

    # --- Thermal ----------------------------------------------------------
    if rep.thermal.max_c is not None:
        hottest = max(rep.thermal.zones, key=lambda z: z[1])
        if rep.thermal.max_c >= 70:
            out.append(Finding(
                "therm.hot", "Suhu komponen tinggi", SEV_WARN,
                f"{hottest[0]} mencapai {rep.thermal.max_c} °C. Throttling mungkin terjadi.",
                [f"{n}={t}" for n, t in sorted(rep.thermal.zones, key=lambda z: -z[1])[:5]],
            ))

    # --- Power settings ---------------------------------------------------
    if pw.screen_off_timeout_ms and pw.screen_off_timeout_ms >= 300_000:
        out.append(Finding(
            "pow.timeout.long", "Screen timeout terlalu panjang", SEV_WARN,
            f"{pw.screen_off_timeout_ms // 1000} detik. Menyumbang konsumsi layar "
            "secara signifikan.",
            [f"screen_off_timeout_ms={pw.screen_off_timeout_ms}"],
            action="set_screen_timeout", task=3,
        ))
    elif pw.screen_off_timeout_ms and pw.screen_off_timeout_ms <= 120_000:
        out.append(Finding(
            "pow.timeout.ok", "Screen timeout wajar", SEV_OK,
            f"{pw.screen_off_timeout_ms // 1000} detik.",
            [f"screen_off_timeout_ms={pw.screen_off_timeout_ms}"],
        ))

    if pw.low_power is False:
        out.append(Finding(
            "pow.saver.off", "Battery saver mati", SEV_INFO,
            "Battery saver tidak aktif. Nyalakan saat baterai rendah.",
            ["low_power=0"],
            action="enable_battery_saver", task=4,
        ))

    # --- Doze whitelist ---------------------------------------------------
    exempt_system = list(dict.fromkeys(
        name for kind, name, _ in pw.doze_whitelist
        if kind in ("system", "system-excidle")
        and not name.startswith("com.android.")
        and not name.startswith("com.google.android.")
        and not name.startswith("com.mediatek.")
        and not name.startswith("com.qualcomm.")
    ))
    if exempt_system:
        out.append(Finding(
            "pow.doze.exempt", "App pihak ketiga dikecualikan dari Doze", SEV_WARN,
            "Entri ini ditanam di level sistem sehingga tidak bisa dicabut tanpa root. "
            "App tersebut tidak pernah dibekukan saat layar mati.",
            exempt_system,
            task=2,
        ))

    user_exempt = list(dict.fromkeys(
        name for kind, name, _ in pw.doze_whitelist if kind == "user"
    ))
    if user_exempt:
        out.append(Finding(
            "pow.doze.user", "App di whitelist Doze oleh pengguna", SEV_INFO,
            f"{len(user_exempt)} app dikecualikan manual.",
            user_exempt,
        ))

    out.sort(key=lambda f: _ORDER.get(f.severity, 9))
    return out


def summarize(findings: list[Finding]) -> dict[str, int]:
    counts = {SEV_CRITICAL: 0, SEV_WARN: 0, SEV_INFO: 0, SEV_OK: 0}
    for f in findings:
        counts[f.severity] = counts.get(f.severity, 0) + 1
    return counts
