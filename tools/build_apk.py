"""Build the cekdroid companion APK without Gradle.

Pipeline: aapt2 compile/link -> javac -> d8 -> zipalign -> apksigner.
Only the Android SDK build-tools and a JDK are required.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

# Lives in tools/ inside the repo, so the project root is one level up.
ROOT = Path(__file__).resolve().parent.parent
OUT = Path(__file__).resolve().parent / "build"
PROJ = ROOT / "cekdroid-android"
SRC = PROJ / "src"
ASSETS = PROJ / "assets"
BUILD = OUT

MIN_SDK = "26"
TARGET_SDK = "34"
VERSION_CODE = "2"
VERSION_NAME = "0.4.0"

# `python build_apk.py --release` produces a Play-uploadable APK signed with a
# private keystore. The default stays debuggable because the CLI pulls reports
# with `adb run-as`, which only works on a debuggable build.
RELEASE = "--release" in sys.argv

SDK_CANDIDATES = [
    Path(r"C:\Android\sdk"),
    Path(os.path.expanduser("~/Android/Sdk")),
    Path(os.path.expanduser("~/Library/Android/sdk")),
    Path("/usr/local/lib/android/sdk"),
]


def find_sdk() -> Path:
    env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env and Path(env).is_dir():
        return Path(env)
    for cand in SDK_CANDIDATES:
        if cand.is_dir():
            return cand
    raise SystemExit("Android SDK tidak ditemukan. Set ANDROID_HOME.")


def latest(path: Path) -> Path:
    items = sorted([p for p in path.iterdir() if p.is_dir()])
    if not items:
        raise SystemExit(f"Tidak ada subdirektori di {path}")
    return items[-1]


def write_args_file(path: Path, entries: list[str]) -> Path:
    """Write an @argfile that both javac and d8 accept.

    One bare path per line, forward-slashed. javac silently drops backslashes
    (turning `C:\\Users\\...` into `C:Users...`), and d8 treats a quote
    character as part of the path, so neither quoting nor native separators
    work here.
    """
    lines = [e.replace("\\", "/") for e in entries if " " not in e]
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    printable = " ".join(str(c) for c in cmd)
    print(f"  $ {printable[:160]}")
    proc = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, **kw)
    if proc.returncode != 0:
        print(proc.stdout[-3000:], file=sys.stderr)
        print(proc.stderr[-3000:], file=sys.stderr)
        raise SystemExit(f"gagal: {printable[:120]} (exit {proc.returncode})")
    return proc


def main() -> int:
    sdk = find_sdk()
    build_tools = latest(sdk / "build-tools")
    platform = latest(sdk / "platforms")
    android_jar = platform / "android.jar"

    aapt2 = build_tools / "aapt2.exe" if os.name == "nt" else build_tools / "aapt2"
    d8 = build_tools / "d8.bat" if os.name == "nt" else build_tools / "d8"
    zipalign = build_tools / "zipalign.exe" if os.name == "nt" else build_tools / "zipalign"
    apksigner = build_tools / "apksigner.bat" if os.name == "nt" else build_tools / "apksigner"

    for tool in (aapt2, d8, zipalign, apksigner):
        if not tool.exists():
            raise SystemExit(f"tool tidak ditemukan: {tool}")
    if not android_jar.exists():
        raise SystemExit(f"android.jar tidak ditemukan: {android_jar}")

    print(f"SDK        : {sdk}")
    print(f"build-tools: {build_tools.name}")
    print(f"platform   : {platform.name}")

    if BUILD.exists():
        shutil.rmtree(BUILD)
    for sub in ("res", "gen", "classes", "dex"):
        (BUILD / sub).mkdir(parents=True, exist_ok=True)

    manifest = PROJ / "AndroidManifest.xml"
    apk_unsigned = BUILD / "cekdroid-unsigned.apk"
    apk_aligned = BUILD / "cekdroid-aligned.apk"
    apk_final = BUILD / "cekdroid.apk"

    print("[1/7] aapt2 compile (resources)")
    res_dir = PROJ / "res"
    compiled_res = BUILD / "res"
    if res_dir.is_dir() and any(res_dir.iterdir()):
        run([
            aapt2, "compile",
            "--dir", res_dir,
            "-o", compiled_res,
        ])
        res_args = [str(p) for p in sorted(compiled_res.iterdir())]
    else:
        res_args = []

    print("[2/7] aapt2 link (manifest + resources)")
    assets_args = ["-A", ASSETS] if ASSETS.is_dir() and any(ASSETS.iterdir()) else []
    run([
        aapt2, "link",
        "-I", android_jar,
        "--manifest", manifest,
        "--java", BUILD / "gen",
        "-o", apk_unsigned,
        "--min-sdk-version", MIN_SDK,
        "--target-sdk-version", TARGET_SDK,
        "--version-code", VERSION_CODE,
        "--version-name", VERSION_NAME,
        *assets_args,
        *res_args,
    ])

    print("[3/7] javac (compile sources)")
    sources = sorted(str(p) for p in SRC.rglob("*.java"))
    generated = sorted(str(p) for p in (BUILD / "gen").rglob("*.java"))
    # JDK 17+ rejects -bootclasspath with -target 11, so put android.jar on the
    # regular classpath and let javac resolve against it directly.
    # Paths go through an @argfile: a few hundred sources exceed the Windows
    # command-line limit and fail with WinError 206 instead of a build error.
    javac_args = write_args_file(BUILD / "args-javac.txt", [*generated, *sources])
    run([
        "javac",
        "-source", "11",
        "-target", "11",
        "-encoding", "UTF-8",
        "-classpath", android_jar,
        "-d", BUILD / "classes",
        f"@{javac_args}",
    ])

    print("[4/7] d8 (dex)")
    classes = sorted(str(p) for p in (BUILD / "classes").rglob("*.class"))
    d8_args = write_args_file(BUILD / "args-d8.txt", classes)
    run([d8, "--lib", android_jar, "--min-api", MIN_SDK,
         "--output", BUILD / "dex", f"@{d8_args}"])

    print("[5/7] repack dex files into APK")
    import zipfile
    dex_files = sorted((BUILD / "dex").glob("*.dex"))
    with zipfile.ZipFile(apk_unsigned, "a", zipfile.ZIP_DEFLATED) as zf:
        for dex in dex_files:
            zf.write(dex, dex.name)
    print(f"  {', '.join(d.name for d in dex_files)}")

    print("[6/7] zipalign")
    run([zipalign, "-f", "-p", "4", apk_unsigned, apk_aligned])

    print("[7/7] apksigner (sign)")
    if RELEASE:
        keystore = Path(os.path.expanduser("~/.cekdroid/release.keystore"))
        alias = "cekdroid"
        store_pass = os.environ.get("CEKDROID_KS_PASS", "")
        if not store_pass:
            raise SystemExit(
                "Mode rilis butuh sandi keystore.\n"
                "  set CEKDROID_KS_PASS=...  lalu jalankan ulang.\n"
                "  Buat keystore sekali dengan:\n"
                "  keytool -genkeypair -keystore %s -alias %s -keyalg RSA "
                "-keysize 2048 -validity 10000" % (keystore, alias))
        if not keystore.exists():
            raise SystemExit(
                f"Keystore rilis belum ada: {keystore}\n"
                f"  keytool -genkeypair -keystore \"{keystore}\" -alias {alias} "
                "-keyalg RSA -keysize 2048 -validity 10000")
        print(f"  keystore rilis: {keystore}")
        # A debuggable build may not be uploaded to Play, and `run-as` (used by
        # the CLI dump) only works when debuggable is set. Release keeps its
        # own flag so the two modes cannot be confused.
        run([
            apksigner, "sign",
            "--ks", keystore,
            "--ks-pass", f"pass:{store_pass}",
            "--key-pass", f"pass:{store_pass}",
            "--ks-key-alias", alias,
            "--min-sdk-version", MIN_SDK,
            "--out", apk_final,
            apk_aligned,
        ])
    else:
        keystore = Path(os.path.expanduser("~/.android/debug.keystore"))
        if not keystore.exists():
            print("  membuat debug keystore…")
            run([
                "keytool", "-genkeypair", "-v",
                "-keystore", keystore,
                "-storepass", "android",
                "-alias", "androiddebugkey",
                "-keypass", "android",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=Android Debug,O=Android,C=US",
            ])
        run([
            apksigner, "sign",
            "--ks", keystore,
            "--ks-pass", "pass:android",
            "--key-pass", "pass:android",
            "--ks-key-alias", "androiddebugkey",
            "--min-sdk-version", MIN_SDK,
            "--out", apk_final,
            apk_aligned,
        ])

    run([apksigner, "verify", "--min-sdk-version", MIN_SDK, apk_final])
    size = apk_final.stat().st_size
    mode = "RILIS" if RELEASE else "debug"
    print(f"\nAPK siap ({mode}): {apk_final}  ({size / 1024:.0f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
