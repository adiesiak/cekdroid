"""cekdroid CLI entry point.

Usage:
  python -m cekdroid diagnose [--json|--html FILE] [--all]
  python -m cekdroid fix --from-report [--dry-run] [--yes]
  python -m cekdroid fix --restrict-bg PKG [--dry-run]
  python -m cekdroid fix --screen-timeout MS [--dry-run]
  python -m cekdroid fix --doze-unexempt PKG [--dry-run]
  python -m cekdroid fix --battery-saver on|off [--dry-run]
  python -m cekdroid undo [--dry-run]
  python -m cekdroid journal
  python -m cekdroid devices
  python -m cekdroid info [--section NAME] [--json|--html FILE]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import parser as report_parser
from . import render, rules
from .actions import Actions, Journal, bucket_name
from .adb import Adb, AdbError, pick_device

HERE = Path(__file__).resolve().parent
PROBE = HERE / "probe.sh"


def _load_adb(args) -> Adb:
    adb = Adb(serial=getattr(args, "serial", None))
    if not adb.serial:
        dev = pick_device(adb)
        adb.serial = dev.serial
    return adb


def _collect(adb: Adb, show_progress: bool = True) -> report_parser.Report:
    if show_progress:
        print("Membaca perangkat…", file=sys.stderr, end="\r")
    raw = adb.read_probe(PROBE)
    if "@@end" not in raw:
        raise AdbError(
            "Probe tidak selesai — koneksi terputus atau shell dibatasi vendor. "
            f"Output diterima {len(raw)} byte."
        )
    if show_progress:
        print(" " * 30, end="\r")
    return report_parser.parse(raw)


# ---------------------------------------------------------------- commands


def cmd_app(args) -> int:
    """Manage the companion APK: install, run headless dump, uninstall."""
    adb = _load_adb(args)
    apk = Path(args.apk) if args.apk else HERE.parent / "tools" / "build" / "cekdroid.apk"

    if args.uninstall:
        ok, out = adb.uninstall("ai.cekdroid")
        print(("[OK] " if ok else "[GAGAL] ") + out)
        return 0 if ok else 1

    if args.install or not _app_installed(adb):
        if not apk.exists():
            print(f"APK tidak ada: {apk}\nBuild dulu: python build_apk.py", file=sys.stderr)
            return 1
        ok, out = adb.install(apk)
        print(("[OK] terpasang" if ok else "[GAGAL] " + out))
        if not ok:
            return 1

    adb.shell("am force-stop ai.cekdroid", timeout=20)
    adb.shell("am start -n ai.cekdroid/.DumpActivity", timeout=30)

    # The dump collects fifteen sections on a mid-range phone and can take
    # well over four seconds. A fixed sleep pulled the *previous* report while
    # the new one was still being written, which made every comparison look
    # like a parity failure (stale battery, stale memory).
    proc = _pull_fresh_report(adb)
    if proc is None:
        print("Gagal membaca laporan dari app. Buka app secara manual sekali, lalu ulangi.",
              file=sys.stderr)
        return 1

    if args.json == "-":
        print(proc)
    else:
        out_path = Path(args.json or "cekdroid-app.json")
        out_path.write_text(proc, encoding="utf-8")
        print(f"Laporan app: {out_path.resolve()}")
    return 0


def _pull_fresh_report(adb: Adb, timeout_s: float = 40.0) -> str | None:
    """Wait until the dump reports a `generated_at` newer than the moment we
    asked the activity to run, then return that JSON."""
    import datetime as _dt
    import time as _time

    started = _dt.datetime.now()
    deadline = _time.monotonic() + timeout_s
    last = None
    while _time.monotonic() < deadline:
        proc = adb._run(
            ["exec-out", "run-as ai.cekdroid cat files/cekdroid-report.json"],
            timeout=60)
        if proc.returncode == 0 and proc.stdout.strip():
            last = proc.stdout
            try:
                stamp = json.loads(last).get("generated_at")
                generated = _dt.datetime.strptime(stamp, "%Y-%m-%dT%H:%M:%S%z")
                if generated.replace(tzinfo=None) >= started - _dt.timedelta(seconds=1):
                    return last
            except (ValueError, TypeError, json.JSONDecodeError):
                pass
        _time.sleep(1.0)
    # Timed out: returning the last readable file beats returning nothing, but
    # the caller is told nothing here, so make it explicit on stderr.
    if last:
        print("Peringatan: laporan app mungkin belum diperbarui penuh.",
              file=sys.stderr)
    return last


def _app_installed(adb: Adb) -> bool:
    out = adb.shell("pm list packages ai.cekdroid")
    return "package:ai.cekdroid" in out


def cmd_devices(args) -> int:
    adb = Adb(serial=None)
    devices = adb.devices()
    if not devices:
        print("Tidak ada device. Aktifkan USB debugging lalu sambungkan.")
        return 1
    for d in devices:
        print(f"{d.serial:<24} {d.state}")
    return 0


def cmd_diagnose(args) -> int:
    adb = _load_adb(args)
    rep = _collect(adb)
    findings = rules.evaluate(rep)

    if args.html:
        out = Path(args.html)
        out.write_text(render.render_html(rep, findings, sections=False),
                       encoding="utf-8")
        print(f"Laporan HTML: {out.resolve()}", file=sys.stderr)

    if args.json:
        text = render.render_json(rep, findings)
        if args.json == "-":
            print(text)
        else:
            Path(args.json).write_text(text, encoding="utf-8")
            print(f"Laporan JSON: {Path(args.json).resolve()}", file=sys.stderr)

    if not args.quiet and not (args.json == "-"):
        color = sys.stdout.isatty() and not args.no_color
        render.set_show_ok(args.all)
        pending = _pending_text(findings)
        render.set_pending(pending)
        visible = findings if args.all else [f for f in findings if f.severity != rules.SEV_OK]
        print(render.render_text(rep, visible, color=color))

    return 0


def _pending_text(findings: list[rules.Finding]) -> list[str]:
    out = []
    for f in findings:
        if f.task == 1:
            out.append("Batasi background app berat:  fix --restrict-bg <package>")
        elif f.task == 2:
            out.append("Cabut dari Doze whitelist:      fix --doze-unexempt <package>")
        elif f.task == 3:
            out.append("Perpendek screen timeout:       fix --screen-timeout 60000")
    seen, uniq = set(), []
    for t in out:
        if t not in seen:
            seen.add(t)
            uniq.append(t)
    return uniq


def cmd_fix(args) -> int:
    adb = _load_adb(args)
    journal = Journal(Path(args.journal) if args.journal else None)
    act = Actions(adb, journal=journal, dry_run=args.dry_run)
    results = []

    if args.restrict_bg:
        for pkg in args.restrict_bg:
            results.append(act.set_standby_bucket(pkg))
            results.append(act.set_appop_background_ignore(pkg))

    if args.doze_unexempt:
        for pkg in args.doze_unexempt:
            results.append(act.remove_doze_whitelist(pkg))
            results.append(act.set_appop_background_ignore(pkg))

    if args.screen_timeout is not None:
        results.append(act.set_screen_timeout(args.screen_timeout))

    if args.battery_saver:
        results.append(act.set_battery_saver(args.battery_saver == "on"))

    if args.from_report:
        rep = _collect(adb, show_progress=False)
        findings = rules.evaluate(rep)
        planned = _plan_from_findings(findings, rep, act)
        if not planned:
            print("Tidak ada aksi otomatis yang disarankan dari laporan ini.")
            return 0
        print("Rencana aksi:")
        for desc, _fn in planned:
            print(f"  - {desc}")
        if not args.yes and not args.dry_run:
            raw = input("\nJalankan? [y/N] ").strip().lower()
            if raw != "y":
                print("Dibatalkan.")
                return 0
        for _desc, fn in planned:
            produced = fn()
            if isinstance(produced, tuple):
                results.extend(produced)
            else:
                results.append(produced)

    if not results:
        print("Tidak ada aksi yang dipilih. Lihat --help.", file=sys.stderr)
        return 2

    failed = 0
    for r in results:
        mark = "OK  " if r.ok else "GAGAL"
        target = f" {r.target}" if r.target else ""
        print(f"[{mark}] {r.action}{target}: {r.message}")
        if not r.ok:
            failed += 1
        if r.undoable:
            print("        bisa dibatalkan: cekdroid undo")
    return 1 if failed else 0


def _plan_from_findings(findings, rep, act: Actions):
    """Build an ordered plan of safe, reversible actions from diagnose findings."""
    planned: list[tuple[str, object]] = []

    heavy_apps = _heavy_background_apps(rep)
    for pkg, reason in heavy_apps:
        planned.append((
            f"Batasi background {pkg} ({reason})",
            (lambda p=pkg: (act.set_standby_bucket(p),
                            act.set_appop_background_ignore(p))),
        ))

    for f in findings:
        if f.id == "pow.doze.exempt":
            for pkg in _third_party_from_evidence(f.evidence):
                planned.append((
                    f"Cabut {pkg} dari Doze whitelist",
                    (lambda p=pkg: (act.remove_doze_whitelist(p),
                                    act.set_appop_background_ignore(p))),
                ))
        elif f.id == "pow.timeout.long":
            planned.append(("Perpendek screen timeout ke 60 detik",
                            lambda: act.set_screen_timeout(60_000)))
    return planned


_HEAVY_PREFIXES = (
    "com.instagram", "com.facebook", "com.threads", "com.twitter",
    "com.snapchat", "com.tiktok", "com.zhiliaoapp", "com.samsung.android.app.",
    "com.miui.", "com.oppo.", "com.coloros.",
)


def _heavy_background_apps(rep) -> list[tuple[str, str]]:
    """Pick installed third-party social/media apps worth restricting."""
    out = []
    for p in rep.packages:
        if any(p.name.startswith(pre) for pre in _HEAVY_PREFIXES):
            if p.name.endswith(".services") or p.name.endswith(".appmanager"):
                continue
            out.append((p.name, "app media/sosial dengan background berat"))
    return out[:6]


def _third_party_from_evidence(evidence: list[str]) -> list[str]:
    out = []
    for ev in evidence:
        name = ev.split(",")[0].strip()
        if name.startswith("com.") and "android." not in name and "google." not in name:
            out.append(name)
    return out


def cmd_info(args) -> int:
    adb = _load_adb(args)
    rep = _collect(adb)
    findings = rules.evaluate(rep)

    if args.json:
        text = render.render_json(rep, findings)
        if args.json == "-":
            print(text)
        else:
            Path(args.json).write_text(text, encoding="utf-8")
            print(f"Laporan JSON: {Path(args.json).resolve()}", file=sys.stderr)

    if args.html:
        out = Path(args.html)
        # `info` is the device-facts command, so its HTML carries one block per
        # tab. `diagnose` stays findings-only: its reader wants the verdict,
        # not a second copy of every field.
        out.write_text(render.render_html(rep, findings, sections=True),
                       encoding="utf-8")
        print(f"Laporan HTML: {out.resolve()}", file=sys.stderr)

    if not (args.quiet or args.json == "-"):
        color = sys.stdout.isatty() and not args.no_color
        print(render.render_info_text(rep, color=color, only=args.section))
    return 0


def cmd_undo(args) -> int:
    adb = _load_adb(args)
    journal = Journal(Path(args.journal) if args.journal else None)
    act = Actions(adb, journal=journal, dry_run=args.dry_run)
    r = act.undo_last()
    print(f"[{'OK' if r.ok else 'GAGAL'}] {r.message}")
    return 0 if r.ok else 1


def cmd_journal(args) -> int:
    journal = Journal(Path(args.journal) if args.journal else None)
    entries = journal.load()
    if not entries:
        print("Journal kosong.")
        return 0
    for e in entries:
        status = "dibatalkan" if e.get("undone") else "aktif"
        print(f"{e.get('ts','?')}  {e['action']:<24} {str(e.get('target')):<32} "
              f"{e.get('before')} -> {e.get('after')}  [{status}]")
    return 0


# ------------------------------------------------------------------- main


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="cekdroid",
        description="Diagnosa dan perbaikan kesehatan perangkat Android lewat ADB.",
    )
    p.add_argument("-s", "--serial", help="serial device (untuk multi-device)")
    p.add_argument("--journal", help="path journal undo alternatif")
    sub = p.add_subparsers(dest="cmd", required=True)

    d = sub.add_parser("diagnose", help="baca dan nilai kesehatan perangkat")
    d.add_argument("--json", nargs="?", const="-", metavar="FILE",
                   help="keluarkan JSON (tanpa nilai = stdout)")
    d.add_argument("--html", metavar="FILE", help="tulis laporan HTML")
    d.add_argument("--all", action="store_true", help="tampilkan juga temuan normal")
    d.add_argument("--quiet", action="store_true", help="tanpa output teks")
    d.add_argument("--no-color", action="store_true")
    d.set_defaults(func=cmd_diagnose)

    f = sub.add_parser("fix", help="jalankan perbaikan (reversible)")
    f.add_argument("--restrict-bg", nargs="+", metavar="PKG",
                   help="batasi background app (bucket + appops)")
    f.add_argument("--doze-unexempt", nargs="+", metavar="PKG",
                   help="cabut app dari Doze whitelist")
    f.add_argument("--screen-timeout", type=int, metavar="MS",
                   help="set screen off timeout dalam milidetik")
    f.add_argument("--battery-saver", choices=["on", "off"])
    f.add_argument("--from-report", action="store_true",
                   help="jalankan saran yang aman dari diagnosa")
    f.add_argument("--dry-run", action="store_true", help="tampilkan tanpa mengubah")
    f.add_argument("--yes", action="store_true", help="tanpa konfirmasi")
    f.set_defaults(func=cmd_fix)

    u = sub.add_parser("undo", help="batalkan aksi terakhir")
    u.add_argument("--dry-run", action="store_true")
    u.set_defaults(func=cmd_undo)

    j = sub.add_parser("journal", help="lihat riwayat aksi")
    j.set_defaults(func=cmd_journal)

    dv = sub.add_parser("devices", help="daftar device")
    dv.set_defaults(func=cmd_devices)

    inf = sub.add_parser("info", help="info perangkat lengkap (system, cpu, gpu, "
                                     "display, network, sim, codec)")
    inf.add_argument("--section", help="hanya satu bagian: "
                                       + ", ".join(render.INFO_SECTIONS))
    inf.add_argument("--json", nargs="?", const="-", metavar="FILE",
                     help="keluarkan JSON (tanpa nilai = stdout)")
    inf.add_argument("--html", metavar="FILE", help="tulis laporan HTML")
    inf.add_argument("--quiet", action="store_true", help="tanpa output teks")
    inf.add_argument("--no-color", action="store_true")
    inf.set_defaults(func=cmd_info)

    ap = sub.add_parser("app", help="pasang/jalankan app companion di HP")
    ap.add_argument("--install", action="store_true", help="pasang ulang APK")
    ap.add_argument("--uninstall", action="store_true", help="hapus app companion")
    ap.add_argument("--apk", help="path APK alternatif")
    ap.add_argument("--json", nargs="?", const="-", metavar="FILE",
                    help="tujuan JSON (tanpa nilai = stdout)")
    ap.set_defaults(func=cmd_app)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except AdbError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\ndibatalkan", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
