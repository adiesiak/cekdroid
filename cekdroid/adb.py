"""ADB transport layer for cekdroid.

Locates an adb binary, resolves a device serial, and executes probe scripts
without assuming a particular vendor or Android version.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

_CANDIDATE_ADB = [
    "adb",
    r"C:\platform-tools\adb.exe",
    r"C:\Android\sdk\platform-tools\adb.exe",
    os.path.expanduser("~/Android/Sdk/platform-tools/adb"),
    os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
    "/usr/local/bin/adb",
    "/usr/bin/adb",
    "/opt/homebrew/bin/adb",
]

PROBE_REMOTE = "/data/local/tmp/cekdroid_probe.sh"


class AdbError(RuntimeError):
    pass


@dataclass(frozen=True)
class Device:
    serial: str
    state: str
    props: dict[str, str]

    @property
    def label(self) -> str:
        model = self.props.get("ro.product.model") or self.props.get("device", "")
        brand = (self.props.get("ro.product.manufacturer") or "").strip()
        return f"{brand} {model}".strip() or self.serial


class Adb:
    def __init__(self, adb_path: str | None = None, serial: str | None = None, timeout: int = 30):
        self.adb_path = adb_path or self._locate()
        self.serial = serial
        self.timeout = timeout
        self._probe_pushed = False

    @staticmethod
    def _locate() -> str:
        for cand in _CANDIDATE_ADB:
            if os.path.sep in cand or cand.startswith("~"):
                p = Path(os.path.expanduser(cand))
                if p.is_file():
                    return str(p)
            else:
                found = shutil.which(cand)
                if found:
                    return found
        raise AdbError(
            "adb tidak ditemukan. Pasang platform-tools atau set env CEKDROID_ADB."
        )

    def _run(self, args: list[str], timeout: int | None = None) -> subprocess.CompletedProcess:
        cmd = [self.adb_path]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += args
        try:
            return subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout or self.timeout,
            )
        except subprocess.TimeoutExpired:
            raise AdbError(f"adb timeout setelah {timeout or self.timeout}s: {' '.join(args)}")

    def shell(self, command: str, timeout: int | None = None) -> str:
        proc = self._run(["shell", command], timeout=timeout)
        return proc.stdout

    def shell_ok(self, command: str, timeout: int | None = None) -> tuple[bool, str]:
        proc = self._run(["shell", command], timeout=timeout)
        out = (proc.stdout or "") + (proc.stderr or "")
        return proc.returncode == 0, out.strip()

    def devices(self) -> list[Device]:
        proc = self._run(["devices", "-l"])
        result: list[Device] = []
        for line in proc.stdout.splitlines()[1:]:
            line = line.strip()
            if not line or line.startswith("*"):
                continue
            parts = line.split()
            serial, state = parts[0], parts[1]
            result.append(Device(serial=serial, state=state, props={}))
        return result

    def device_info(self) -> Device:
        raw = self.shell(
            "getprop ro.product.manufacturer; getprop ro.product.model; "
            "getprop ro.product.device; getprop ro.build.version.release; "
            "getprop ro.build.version.sdk; getprop ro.build.version.security_patch"
        )
        lines = [line.strip() for line in raw.splitlines()]
        keys = [
            "ro.product.manufacturer",
            "ro.product.model",
            "device",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.build.version.security_patch",
        ]
        props = {k: (lines[i] if i < len(lines) else "") for i, k in enumerate(keys)}
        return Device(serial=self.serial or "", state="device", props=props)

    def is_shell_capable(self) -> bool:
        ok, _ = self.shell_ok("id -u")
        return ok

    def ensure_probe(self, local_probe: Path) -> None:
        if self._probe_pushed:
            return
        proc = self._run(["push", str(local_probe), PROBE_REMOTE], timeout=60)
        if proc.returncode != 0:
            raise AdbError(f"gagal push probe: {proc.stderr.strip()}")
        self.shell(f"chmod 755 {PROBE_REMOTE}")
        self._probe_pushed = True

    def read_probe(self, local_probe: Path, timeout: int = 240) -> str:
        """Run the on-device probe and return its raw output.

        The budget is generous on purpose: the probe now also dumps
        `pm list features` (a few hundred lines) and two extra `dumpsys`
        blocks, and a budget phone takes its time on those. A shorter timeout
        here used to truncate the report mid-section, which then looked like a
        parser bug rather than a slow device.
        """
        self.ensure_probe(local_probe)
        return self.shell(f"sh {PROBE_REMOTE}", timeout=timeout)

    def install(self, apk: Path, reinstall: bool = True) -> tuple[bool, str]:
        args = ["install"]
        if reinstall:
            args.append("-r")
        args.append(str(apk))
        proc = self._run(args, timeout=180)
        out = (proc.stdout or "") + (proc.stderr or "")
        return proc.returncode == 0 and "Success" in out, out.strip()

    def uninstall(self, package: str) -> tuple[bool, str]:
        proc = self._run(["uninstall", package], timeout=90)
        out = (proc.stdout or "") + (proc.stderr or "")
        return "Success" in out, out.strip()


def pick_device(adb: Adb) -> Device:
    devices = [d for d in adb.devices() if d.state == "device"]
    if not devices:
        raise AdbError(
            "Tidak ada device terhubung. Aktifkan USB debugging lalu sambungkan kabel."
        )
    if len(devices) == 1:
        return devices[0]
    print("Beberapa device terdeteksi:", file=sys.stderr)
    for i, d in enumerate(devices, 1):
        print(f"  {i}. {d.serial}", file=sys.stderr)
    raw = input("Pilih nomor device: ").strip()
    try:
        return devices[int(raw) - 1]
    except (ValueError, IndexError):
        raise AdbError("Pilihan tidak valid")
