"""Reversible remediation actions executed over ADB shell.

Every mutating action records the previous value in a snapshot journal before
writing, so `undo` can restore the device exactly. Actions are declared with
their privilege requirement and verified after execution.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from pathlib import Path

from .adb import Adb, AdbError
from .parser import _int

JOURNAL_DEFAULT = Path.home() / ".cekdroid" / "journal.jsonl"

# Android AppStandbyManager bucket constants (API 28+).
BUCKET_EXEMPTED = 5
BUCKET_ACTIVE = 10
BUCKET_WORKING_SET = 20
BUCKET_FREQUENT = 30
BUCKET_RARE = 40
BUCKET_RESTRICTED = 45

_BUCKET_NAME = {
    5: "EXEMPTED", 10: "ACTIVE", 20: "WORKING_SET",
    30: "FREQUENT", 40: "RARE", 45: "RESTRICTED",
}


def bucket_name(value: int | None) -> str:
    if value is None:
        return "?"
    return _BUCKET_NAME.get(value, str(value))


@dataclass
class Journal:
    path: Path | None = None

    def __post_init__(self) -> None:
        self.path = Path(self.path) if self.path else JOURNAL_DEFAULT

    def append(self, record: dict) -> None:
        assert self.path is not None
        self.path.parent.mkdir(parents=True, exist_ok=True)
        record = {**record, "ts": time.strftime("%Y-%m-%dT%H:%M:%S")}
        with self.path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")

    def load(self) -> list[dict]:
        assert self.path is not None
        if not self.path.exists():
            return []
        out = []
        for line in self.path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line:
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
        return out

    def last_undoable(self) -> dict | None:
        for rec in reversed(self.load()):
            if rec.get("undo") and not rec.get("undone"):
                return rec
        return None

    def mark_undone(self, record_id: str) -> None:
        assert self.path is not None
        recs = self.load()
        changed = False
        for rec in recs:
            if rec.get("id") == record_id and not rec.get("undone"):
                rec["undone"] = True
                changed = True
        if changed:
            self.path.write_text(
                "\n".join(json.dumps(r, ensure_ascii=False) for r in recs) + "\n",
                encoding="utf-8",
            )


@dataclass
class ActionResult:
    action: str
    target: str | None
    ok: bool
    message: str
    before: str | None = None
    after: str | None = None
    undoable: bool = False


class Actions:
    def __init__(self, adb: Adb, journal: Journal | None = None, dry_run: bool = False):
        self.adb = adb
        self.journal = journal or Journal()
        self.dry_run = dry_run

    # -- helpers ---------------------------------------------------------
    def _record(self, action: str, target: str | None, before, after, undo_cmd: str | None) -> None:
        if self.dry_run:
            return
        rec = {
            "id": f"{int(time.time() * 1000)}-{action}",
            "action": action,
            "serial": self.adb.serial,
            "target": target,
            "before": before,
            "after": after,
            "undo": undo_cmd,
        }
        self.journal.append(rec)

    def _done(self, action, target, msg, before=None, after=None, undoable=False) -> ActionResult:
        prefix = "[dry-run] " if self.dry_run else ""
        return ActionResult(action, target, True, prefix + msg, before, after, undoable)

    def _fail(self, action, target, msg) -> ActionResult:
        return ActionResult(action, target, False, msg)

    # -- actions ---------------------------------------------------------

    def set_standby_bucket(self, package: str, bucket: int = BUCKET_RESTRICTED) -> ActionResult:
        ok, before_raw = self.adb.shell_ok(f"am get-standby-bucket {package}")
        if not ok:
            return self._fail("set_standby_bucket", package,
                              f"tidak bisa membaca bucket: {before_raw}")
        before = before_raw.strip().splitlines()[-1].strip() if before_raw.strip() else ""

        if self.dry_run:
            return self._done("set_standby_bucket", package,
                              f"bucket {before} -> {bucket} ({bucket_name(bucket)})",
                              before=before, after=str(bucket), undoable=True)

        self.adb.shell(f"am set-standby-bucket --user 0 {package} {bucket}")
        time.sleep(0.4)
        ok2, after_raw = self.adb.shell_ok(f"am get-standby-bucket {package}")
        after = after_raw.strip().splitlines()[-1].strip() if after_raw.strip() else ""

        undo = None
        if before.isdigit() and before != str(bucket):
            undo = f"am set-standby-bucket --user 0 {package} {before}"

        if after == str(bucket):
            self._record("set_standby_bucket", package, before, after, undo)
            return self._done("set_standby_bucket", package,
                              f"bucket {before} -> {after} ({bucket_name(bucket)})",
                              before=before, after=after, undoable=bool(undo))

        # Some ROMs clamp RESTRICTED (45) to RARE (40). RARE is still a
        # restriction, so treat it as success with a note rather than failure.
        after_num = _int(after)
        if (bucket == BUCKET_RESTRICTED and after_num is not None
                and after_num >= BUCKET_RARE and before.isdigit()
                and int(before) > after_num):
            self._record("set_standby_bucket", package, before, after, undo)
            return self._done(
                "set_standby_bucket", package,
                f"bucket {before} -> {after} ({bucket_name(after_num)}); "
                f"vendor menolak RESTRICTED, RARE sudah diterapkan",
                before=before, after=after, undoable=bool(undo),
            )

        if undo:
            self._record("set_standby_bucket", package, before, after, undo)
        return self._fail(
            "set_standby_bucket", package,
            f"ditolak sistem (diminta {bucket}, hasil {after or '?'}). "
            "Vendor mengunci bucket untuk app ini; gunakan appops sebagai gantinya.",
        )

    def set_appop_background_ignore(self, package: str, op: str = "RUN_ANY_IN_BACKGROUND") -> ActionResult:
        ok, before_raw = self.adb.shell_ok(f"cmd appops get {package} {op}")
        before = ""
        if ok and ":" in before_raw:
            before = before_raw.split(":", 1)[1].strip().splitlines()[0]

        if self.dry_run:
            return self._done("set_appop", package,
                              f"{op} {before or '?'} -> ignore",
                              before=before, after="ignore", undoable=True)

        ok2, out = self.adb.shell_ok(f"cmd appops set {package} {op} ignore")
        time.sleep(0.3)
        ok3, after_raw = self.adb.shell_ok(f"cmd appops get {package} {op}")
        after = after_raw.split(":", 1)[1].strip().splitlines()[0] if ":" in after_raw else ""

        undo = None
        if before and before != "ignore":
            undo = f"cmd appops set {package} {op} {before}"

        if after == "ignore":
            self._record("set_appop", package, before, after, undo)
            return self._done("set_appop", package, f"{op} {before or '?'} -> ignore",
                              before=before, after=after, undoable=bool(undo))
        return self._fail("set_appop", package,
                          f"gagal menulis appops: {out or 'tanpa output'}")

    def remove_doze_whitelist(self, package: str) -> ActionResult:
        ok, listing = self.adb.shell_ok("cmd deviceidle whitelist")
        kinds = []
        for line in listing.splitlines():
            parts = line.strip().split(",")
            if len(parts) >= 2 and parts[1] == package:
                kinds.append(parts[0])

        if not kinds:
            return self._done("remove_doze_whitelist", package,
                              "tidak ada di whitelist", undoable=False)

        if self.dry_run:
            return self._done("remove_doze_whitelist", package,
                              f"lepas dari whitelist ({', '.join(kinds)})",
                              before=",".join(kinds), undoable=True)

        removed, blocked = [], []
        for kind in kinds:
            ok2, out = self.adb.shell_ok(f"cmd deviceidle whitelist -{packages_safe(package)}")
            if ok2 and "Removed" in out:
                removed.append(kind)
            elif kind.startswith("system"):
                blocked.append(kind)

        undo = None
        if removed:
            undo = f"cmd deviceidle whitelist +{package}"

        ok4, final = self.adb.shell_ok("cmd deviceidle whitelist")
        still = [line.strip() for line in final.splitlines()
                 if package in line and not line.strip().startswith("user,")]

        if still:
            if undo:
                self._record("remove_doze_whitelist", package, ",".join(kinds),
                             "partial", undo)
            return self._fail(
                "remove_doze_whitelist", package,
                f"sebagian ditolak (butuh root): {', '.join(s.split(',')[0] for s in still)}. "
                "Gunakan appops sebagai kompensasi.",
            )

        self._record("remove_doze_whitelist", package, ",".join(kinds), "removed", undo)
        return self._done("remove_doze_whitelist", package,
                          f"dicabut dari whitelist ({', '.join(kinds)})",
                          before=",".join(kinds), after="removed", undoable=True)

    def set_screen_timeout(self, ms: int = 60_000) -> ActionResult:
        ok, before_raw = self.adb.shell_ok("settings get system screen_off_timeout")
        before = before_raw.strip()

        if self.dry_run:
            return self._done("set_screen_timeout", "system",
                              f"screen_off_timeout {before} -> {ms}",
                              before=before, after=str(ms), undoable=True)

        ok2, out = self.adb.shell_ok(f"settings put system screen_off_timeout {ms}")
        time.sleep(0.3)
        ok3, after_raw = self.adb.shell_ok("settings get system screen_off_timeout")
        after = after_raw.strip()

        undo = None
        if before.isdigit():
            undo = f"settings put system screen_off_timeout {before}"

        if after == str(ms):
            self._record("set_screen_timeout", "system", before, after, undo)
            return self._done("set_screen_timeout", "system",
                              f"screen_off_timeout {before} -> {after}",
                              before=before, after=after, undoable=bool(undo))
        return self._fail("set_screen_timeout", "system",
                          f"gagal (nilai sekarang {after or '?'}): {out}")

    def set_battery_saver(self, enabled: bool = True) -> ActionResult:
        ok, before_raw = self.adb.shell_ok("settings get global low_power")
        before = before_raw.strip()

        if self.dry_run:
            return self._done("set_battery_saver", "global",
                              f"low_power {before} -> {1 if enabled else 0}",
                              before=before, undoable=True)

        value = "1" if enabled else "0"
        self.adb.shell_ok(f"settings put global low_power {value}")
        self.adb.shell_ok(f"cmd power set-mode {value}")
        time.sleep(0.4)
        ok3, after_raw = self.adb.shell_ok("settings get global low_power")
        after = after_raw.strip()

        undo = None
        if before.isdigit():
            undo = f"settings put global low_power {before}"

        if after == value:
            self._record("set_battery_saver", "global", before, after, undo)
            return self._done("set_battery_saver", "global",
                              f"low_power {before} -> {after}",
                              before=before, after=after, undoable=bool(undo))
        return self._fail("set_battery_saver", "global",
                          f"nilai akhir {after or '?'} (butuh privilege lebih?)")

    def force_stop(self, package: str) -> ActionResult:
        if self.dry_run:
            return self._done("force_stop", package, "app dihentikan", undoable=False)
        ok, out = self.adb.shell_ok(f"am force-stop {package}")
        if ok:
            return self._done("force_stop", package, "app dihentikan", undoable=False)
        return self._fail("force_stop", package, out)

    def undo_last(self) -> ActionResult:
        rec = self.journal.last_undoable()
        if not rec:
            return self._fail("undo", None, "tidak ada aksi yang bisa dibatalkan")
        undo_cmd = rec["undo"]
        if self.dry_run:
            return self._done("undo", rec.get("target"),
                              f"akan menjalankan: {undo_cmd}",
                              undoable=False)
        ok, out = self.adb.shell_ok(undo_cmd)
        if not ok:
            return self._fail("undo", rec.get("target"), f"gagal: {out}")
        self.journal.mark_undone(rec["id"])
        return self._done("undo", rec.get("target"),
                          f"dipulihkan: {rec['action']} {rec.get('target') or ''} "
                          f"({rec.get('after')} -> {rec.get('before')})")


def packages_safe(name: str) -> str:
    return name.replace(";", "").replace("&", "").replace("|", "")
