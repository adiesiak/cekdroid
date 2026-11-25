"""Output renderers: terminal text, JSON, and standalone HTML."""

from __future__ import annotations

import html
import json
from pathlib import Path

from .parser import Report
from .rules import Finding, SEV_CRITICAL, SEV_OK, SEV_WARN

_COLORS = {
    "critical": "\033[91m",
    "warn": "\033[93m",
    "info": "\033[96m",
    "ok": "\033[92m",
    "dim": "\033[90m",
    "bold": "\033[1m",
    "reset": "\033[0m",
}

_MARK = {"critical": "!!", "warn": " !", "info": " i", "ok": " +"}


def _c(text: str, color: str, enabled: bool) -> str:
    if not enabled:
        return text
    return f"{_COLORS[color]}{text}{_COLORS['reset']}"


def _gb(kb: int | None) -> str:
    return "?" if kb is None else f"{kb / 1024 / 1024:.1f} GB"


def _mb(kb: int | None) -> str:
    return "?" if kb is None else f"{kb / 1024:.0f} MB"


def render_text(rep: Report, findings: list[Finding], color: bool = True) -> str:
    d = rep.device
    lines: list[str] = []
    add = lines.append

    model = f"{d.get('ro.product.manufacturer', '')} {d.get('ro.product.model', '')}".strip()
    add(_c("CekDroid", "bold", color) + _c("  laporan kesehatan perangkat", "dim", color))
    add("")
    add(_c(model or "Perangkat tidak dikenal", "bold", color))
    add(f"  Android {d.get('ro.build.version.release', '?')} "
        f"(SDK {d.get('ro.build.version.sdk', '?')})  ·  patch {d.get('ro.build.version.security_patch', '?')}")
    add(f"  ABI {d.get('ro.product.cpu.abi', '?')}  ·  uptime {rep.cpu.uptime_h or '?'} jam")
    add("")

    b, m, s = rep.battery, rep.memory, rep.storage
    add(_c("RINGKASAN PERANGKAT", "bold", color))
    batt = f"{b.level}%" if b.level is not None else "?"
    if b.status:
        batt += f" ({b.status})"
    if b.temperature_c is not None:
        batt += f"  {b.temperature_c} °C"
    add(f"  Baterai   {batt}")
    if b.wear_pct is not None:
        add(f"            kapasitas {b.wear_pct}% dari desain")
    if m.total_kb:
        add(f"  RAM       {_gb(m.total_kb)} · tersedia {_mb(m.available_kb)}"
            + (f" · swap {m.swap_used_pct}%" if m.swap_used_pct else ""))
    if s.total_kb:
        add(f"  Storage   {_gb(s.total_kb)} · sisa {_gb(s.free_kb)} ({s.free_pct}%)")
    if rep.thermal.max_c is not None:
        add(f"  Suhu      maks {rep.thermal.max_c} °C")
    add("")

    counts = {k: 0 for k in ("critical", "warn", "info", "ok")}
    for f in findings:
        counts[f.severity] = counts.get(f.severity, 0) + 1
    parts = []
    for sev, label in (("critical", "kritis"), ("warn", "peringatan"),
                       ("info", "info"), ("ok", "normal")):
        if counts[sev]:
            parts.append(_c(f"{counts[sev]} {label}", sev, color))
    add(_c("TEMUAN", "bold", color) + "  " + "  ".join(parts))
    add("")

    for f in findings:
        mark = _MARK.get(f.severity, "  ")
        head = f" {mark} "
        colored_head = _c(head, f.severity, color) if f.severity != "ok" else _c(head, "ok", color)
        title = f.title
        if f.task:
            title += _c(f"  [tugas #{f.task}]", "dim", color)
        add(colored_head + title)
        for i, det_line in enumerate(_wrap(f.detail, 76)):
            add("      " + det_line)
        if f.evidence:
            shown = f.evidence[:6]
            for ev in shown:
                add(_c("      · " + ev, "dim", color))
            if len(f.evidence) > len(shown):
                add(_c(f"      · (+{len(f.evidence) - len(shown)} lainnya)", "dim", color))
        add("")

    undoable = _PENDING_ACTIONS
    if undoable:
        add(_c("SIAP DIEKSEKUSI", "bold", color))
        for i, t in enumerate(undoable, 1):
            add(f"  {i}. {t}")
        add("")

    return "\n".join(lines)


_show_ok = True
_PENDING_ACTIONS: list[str] = []


def set_show_ok(value: bool) -> None:
    global _show_ok
    _show_ok = value


def set_pending(actions: list[str]) -> None:
    global _PENDING_ACTIONS
    _PENDING_ACTIONS = actions


def _wrap(text: str, width: int) -> list[str]:
    words = text.split()
    out, cur = [], ""
    for w in words:
        if not cur:
            cur = w
        elif len(cur) + 1 + len(w) <= width:
            cur += " " + w
        else:
            out.append(cur)
            cur = w
    if cur:
        out.append(cur)
    return out or [""]


def _cluster(ranges: dict[str, tuple[int, int]]):
    """Group cores by identical cpufreq range: one entry per cluster.

    A big.LITTLE SoC drives one policy per cluster, so cores that report the
    same (min, max) pair are the same cluster. Yields (cores, min_khz, max_khz)
    sorted by descending max clock, which is how a spec sheet lists clusters.
    """
    groups: dict[tuple[int, int], list[str]] = {}
    for core, bounds in sorted(ranges.items()):
        groups.setdefault(bounds, []).append(core)
    out = []
    for (lo, hi), cores in groups.items():
        out.append((cores, lo, hi))
    out.sort(key=lambda item: item[2], reverse=True)
    return out


def to_dict(rep: Report, findings: list[Finding]) -> dict:
    b, m, s, c = rep.battery, rep.memory, rep.storage, rep.cpu
    import datetime as _dt
    return {
        "schema": "cekdroid.report.v2",
        # Same metadata block the app writes, so a consumer can treat a CLI
        # report and an app dump interchangeably.
        "source": "cli-adb",
        "generated_at": _dt.datetime.now().astimezone().strftime("%Y-%m-%dT%H:%M:%S%z"),
        "device": {
            "manufacturer": rep.device.get("ro.product.manufacturer"),
            "model": rep.device.get("ro.product.model"),
            "android": rep.device.get("ro.build.version.release"),
            "sdk": rep.device.get("ro.build.version.sdk"),
            "security_patch": rep.device.get("ro.build.version.security_patch"),
            "abi": rep.device.get("ro.product.cpu.abi"),
            "fingerprint": rep.device.get("ro.build.fingerprint"),
        },
        "battery": {
            "level": b.level,
            "status": b.status,
            "health": b.health,
            "temperature_c": b.temperature_c,
            "voltage_mv": b.voltage_mv,
            "cycle_count": b.cycle_count,
            "wear_pct": b.wear_pct,
            "charge_full_uah": b.charge_full_uah,
            "charge_full_design_uah": b.charge_full_design_uah,
        },
        "memory": {
            "total_kb": m.total_kb,
            "available_kb": m.available_kb,
            "used_pct": m.used_pct,
            "swap_total_kb": m.swap_total_kb,
            "swap_used_kb": m.swap_used_kb,
            "swap_used_pct": m.swap_used_pct,
            "allocstall": m.allocstall,
    },
        "storage": {
            "total_kb": s.total_kb,
            "free_kb": s.free_kb,
            "free_pct": s.free_pct,
            "inode_used_pct": s.inode_used_pct,
            "app_data_kb": s.app_data_size,
        },
        "cpu": {
            "load1": c.load1,
            "idle_pct": c.idle_pct,
            "cores_online": c.cores_online,
            "uptime_h": c.uptime_h,
        },
        "thermal": {"max_c": rep.thermal.max_c, "zones": rep.thermal.zones},
        "power": {
            "screen_off_timeout_ms": rep.power.screen_off_timeout_ms,
            "low_power": rep.power.low_power,
            "doze_state": rep.power.doze_state,
        },
        "security": {
            "boot_verified": rep.security.boot_verified,
            "verity": rep.security.verity,
            "debug": rep.security.debug,
            "su_present": rep.security.su_present,
            "accessibility_services": rep.security.accessibility_services,
            "device_admins": rep.security.device_admins,
        },
        "packages": {
            "total": rep.package_total,
            "third_party": rep.package_thirdparty,
            "sideloaded": [
                {"name": p.name, "installer": p.installer}
                for p in rep.packages if p.sideloaded
            ],
        },
        "system": {
            "kernel": rep.system.kernel,
            "build_date": rep.system.build_date,
            "build_user": rep.system.build_user,
            "build_host": rep.system.build_host,
            "build_type": rep.system.build_type,
            "build_flavor": rep.system.build_flavor,
            "abilist": rep.system.abilist,
            "abilist32": rep.system.abilist32,
            "abilist64": rep.system.abilist64,
            "board_platform": rep.system.board_platform,
            "kernel_cmdline": rep.system.kernel_cmdline,
            "marketing_name": rep.system.marketing_name,
            "boot_count": rep.system.boot_count,
            "uptime_text": rep.system.uptime_text,
            "system_as_root": rep.system.system_as_root,
            "dynamic_partitions": rep.system.dynamic_partitions,
            "seamless_updates": rep.system.seamless_updates,
            "baseband": rep.system.baseband,
            "sdk_ext": rep.system.sdk_ext,
        },
        "features": {
            "count": len(rep.features.features),
            "gles": rep.features.gles,
            "list": [
                {"name": name, "value": value}
                for name, value in rep.features.features
            ],
        },
        "traffic": {
            "mobile_rx_kb": rep.traffic.mobile_rx_kb,
            "mobile_tx_kb": rep.traffic.mobile_tx_kb,
            "wifi_rx_kb": rep.traffic.wifi_rx_kb,
            "wifi_tx_kb": rep.traffic.wifi_tx_kb,
        },
        "cpudetail": {
            "model_name": rep.cpudetail.model_name,
            "hardware": rep.cpudetail.hardware,
            "implementer": rep.cpudetail.implementer,
            "architecture": rep.cpudetail.architecture,
            "variant": rep.cpudetail.variant,
            "part": rep.cpudetail.part,
            "revision": rep.cpudetail.revision,
            "governors": rep.cpudetail.governors,
            "capacities": rep.cpudetail.capacities,
            "clusters": [
                {
                    "index": i,
                    "core_list": ", ".join(cores),
                    "cores": len(cores),
                    "min_khz": min_khz,
                    "max_khz": max_khz,
                }
                for i, (cores, min_khz, max_khz)
                in enumerate(_cluster(rep.cpudetail.ranges))
            ],
        },
        "display": {
            "width_px": rep.display.width_px,
            "height_px": rep.display.height_px,
            "density_dpi": rep.display.density_dpi,
            "refresh_hz": rep.display.refresh_hz,
            "name": rep.display.name,
        },
        "gpu": {
            "kgsl_model": rep.gpu.kgsl_model,
            "kgsl_busy": rep.gpu.kgsl_busy,
            "mali": rep.gpu.mali,
            "gles": rep.gpu.gles,
        },
        "vulkan": {
            "driver": rep.vulkan.driver,
            "version": rep.vulkan.version,
            "level": rep.vulkan.level,
            "compute": rep.vulkan.compute,
        },
        "container": {
            "extractors": rep.containers.extractors,
        },
        "network": {
            "network_type": rep.network.network_type,
            "network_operator": rep.network.network_operator,
            "network_country": rep.network.network_country,
            "operator_numeric": rep.network.operator_numeric,
            "interfaces": [
                {"name": n, "addr": a} for n, a in rep.network.interfaces
            ],
        },
        "sim": {
            "sim_state": rep.sim.sim_state,
            "sim_operator": rep.sim.sim_operator,
            "sim_country": rep.sim.sim_country,
            "service_state": rep.sim.service_state,
            "iccid_raw": rep.sim.iccid_raw,
        },
        "codec": {
            "lines": rep.codec.lines,
        },
        "camera": {
            "device_count": rep.camera.device_count,
            "normal_count": rep.camera.normal_count,
            "public_count": rep.camera.public_count,
            "mapping": [
                {"service_id": a, "api_id": b} for a, b in rep.camera.mapping
            ],
        },
        "sensor": {
            "total_hw": rep.sensor.total_hw,
            "running": rep.sensor.running,
            "sensors": [
                {"name": n, "vendor": v, "type": t}
                for n, v, t in rep.sensor.sensors
            ],
        },
        "id": {
            "android_id": rep.ident.android_id,
            "serial": rep.ident.serial,
            "fingerprint": rep.ident.fingerprint,
            "build_id": rep.ident.build_id,
            "display_id": rep.ident.display_id,
            "bootloader": rep.ident.bootloader,
        },
        "findings": [
            {
                "id": f.id,
                "title": f.title,
                "severity": f.severity,
                "detail": f.detail,
                "evidence": f.evidence,
                "task": f.task,
            }
            for f in findings
        ],
        "summary": {
            sev: sum(1 for f in findings if f.severity == sev)
            for sev in ("critical", "warn", "info", "ok")
        },
    }


_HTML = """<!doctype html>
<html lang="id"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CekDroid — {title}</title>
<style>
:root{{--bg:#0f1115;--card:#171a21;--fg:#e6e9ef;--dim:#8b93a3;
--crit:#ff5c5c;--warn:#ffb020;--info:#4cc2ff;--ok:#3ddc84;--line:#252a34}}
*{{box-sizing:border-box}}
body{{margin:0;background:var(--bg);color:var(--fg);
font:15px/1.55 ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,sans-serif}}
.wrap{{max-width:900px;margin:0 auto;padding:32px 20px 64px}}
h1{{font-size:24px;margin:0 0 4px}}
.sub{{color:var(--dim);font-size:13px;margin-bottom:24px}}
.stats{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));
gap:10px;margin-bottom:28px}}
.stat{{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}}
.stat .k{{color:var(--dim);font-size:11px;text-transform:uppercase;letter-spacing:.06em}}
.stat .v{{font-size:18px;font-weight:600;margin-top:4px}}
h2{{font-size:13px;text-transform:uppercase;letter-spacing:.08em;color:var(--dim);
margin:28px 0 12px;font-weight:600}}
.f{{background:var(--card);border:1px solid var(--line);border-left-width:3px;
border-radius:8px;padding:13px 15px;margin-bottom:9px}}
.f.critical{{border-left-color:var(--crit)}}
.f.warn{{border-left-color:var(--warn)}}
.f.info{{border-left-color:var(--info)}}
.f.ok{{border-left-color:var(--ok)}}
.f .t{{font-weight:600;display:flex;justify-content:space-between;gap:12px;align-items:baseline}}
.f .sev{{font-size:10px;text-transform:uppercase;letter-spacing:.07em;color:var(--dim);white-space:nowrap}}
.f.critical .sev{{color:var(--crit)}} .f.warn .sev{{color:var(--warn)}}
.f.ok .sev{{color:var(--ok)}} .f.info .sev{{color:var(--info)}}
.f .d{{color:#c2c8d4;font-size:13.5px;margin-top:5px}}
.f ul{{margin:8px 0 0;padding-left:16px;color:var(--dim);font-size:12px}}
.f li{{margin:2px 0;font-family:ui-monospace,monospace}}
section{{background:var(--card);border:1px solid var(--line);border-radius:10px;
padding:14px 16px;margin-bottom:12px}}
section h3{{margin:0 0 10px;font-size:14px;letter-spacing:.02em;
display:flex;justify-content:space-between;gap:10px;align-items:baseline}}
section h3 .n{{color:var(--dim);font-size:11px;font-weight:400;
text-transform:uppercase;letter-spacing:.07em}}
table{{width:100%;border-collapse:collapse}}
td{{padding:5px 0;vertical-align:top;font-size:13px;border-bottom:1px solid #1e222b}}
td.k{{color:var(--dim);width:42%;padding-right:12px}}
td.v{{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
color:var(--fg);word-break:break-word}}
tr:last-child td{{border-bottom:none}}
td.v.null{{color:#5b6273}}
.meta{{color:var(--dim);font-size:11px;margin-top:28px;border-top:1px solid var(--line);padding-top:14px}}
.note{{color:var(--warn);font-size:12px;margin:8px 0 0;padding-left:16px}}
</style></head><body><div class="wrap">
<h1>{title}</h1>
<div class="sub">Android {android} · SDK {sdk} · patch {patch} · {abi}</div>
<div class="stats">{stats}</div>
{sections}
<h2>Temuan</h2>
{findings}
<div class="meta">Dihasilkan CekDroid · skema cekdroid.report.v2</div>
</div></body></html>"""


# Order the detail blocks the way the tabs are ordered in the app, so the
# HTML and the phone read the same way.
_HTML_SECTION_ORDER = (
    ("device", "Perangkat"),
    ("system", "Sistem"),
    ("cpu", "CPU"),
    ("cpudetail", "Detail CPU"),
    ("gpu", "GPU"),
    ("memory", "Memori"),
    ("storage", "Penyimpanan"),
    ("battery", "Baterai"),
    ("display", "Layar"),
    ("camera", "Kamera"),
    ("sensor", "Sensor"),
    ("network", "Jaringan"),
    ("traffic", "Lalu lintas"),
    ("sim", "SIM"),
    ("packages", "Aplikasi"),
    ("thermal", "Suhu"),
    ("power", "Daya"),
    ("security", "Keamanan"),
    ("codec", "Codec"),
    ("id", "Identitas"),
)


def _humanize(key: str) -> str:
    return key.replace("_", " ").strip().capitalize()


def _cell(value) -> str:
    if value is None or value == "" or value == {} or value == []:
        # A dash, not a blank: the row exists, the platform just refused it.
        return '<td class="v null">—</td>'
    if isinstance(value, list):
        if len(value) <= 4 and all(isinstance(v, (str, int, float)) for v in value):
            return f'<td class="v">{html.escape(", ".join(str(v) for v in value))}</td>'
        return f'<td class="v">{len(value)} baris</td>'
    if isinstance(value, dict):
        inner = ", ".join(f"{k}={v}" for k, v in list(value.items())[:6] if v not in (None, ""))
        return f'<td class="v">{html.escape(inner or "—")}</td>'
    return f'<td class="v">{html.escape(str(value))}</td>'


def _html_sections(d: dict) -> str:
    """One <section> per tab, built from the same dict the JSON writer emits."""
    out = []
    for key, label in _HTML_SECTION_ORDER:
        block = d.get(key)
        if not isinstance(block, dict) or not block:
            continue
        rows = []
        for field, value in block.items():
            if isinstance(value, list) and not value:
                continue
            rows.append(
                f"<tr><td class=\"k\">{html.escape(_humanize(field))}</td>"
                f"{_cell(value)}</tr>"
            )
        if not rows:
            continue
        out.append(
            f'<section id="{html.escape(key)}"><h3>{html.escape(label)}'
            f'<span class="n">{len(rows)} baris</span></h3>'
            f"<table>{''.join(rows)}</table></section>"
        )

    # Why some values are dashes. Kept out of to_dict(): it is commentary for a
    # human reader, not a fact about the device that a JSON consumer can use.
    notes = _platform_limits()
    if notes:
        items = "".join(
            f'<p class="note">· {html.escape(str(n))}</p>' for n in notes
        )
        out.append(f'<section><h3>Batasan platform</h3>{items}</section>')
    return "\n".join(out)



def render_html(rep: Report, findings: list[Finding], sections: bool = True) -> str:
    d = rep.device
    b, m, s = rep.battery, rep.memory, rep.storage
    title = f"{d.get('ro.product.manufacturer', '')} {d.get('ro.product.model', '')}".strip() or "Perangkat"

    stats = []
    if b.level is not None:
        stats.append(("Baterai", f"{b.level}%"))
    if b.temperature_c is not None:
        stats.append(("Suhu baterai", f"{b.temperature_c} °C"))
    if b.wear_pct is not None:
        stats.append(("Kapasitas", f"{b.wear_pct}%"))
    if m.total_kb:
        stats.append(("RAM", _gb(m.total_kb)))
    if m.swap_used_pct is not None:
        stats.append(("Swap", f"{m.swap_used_pct}%"))
    if s.free_pct is not None:
        stats.append(("Storage sisa", f"{s.free_pct}%"))
    if rep.thermal.max_c is not None:
        stats.append(("Suhu maks", f"{rep.thermal.max_c} °C"))

    stats_html = "".join(
        f'<div class="stat"><div class="k">{html.escape(k)}</div>'
        f'<div class="v">{html.escape(v)}</div></div>'
        for k, v in stats
    )

    blocks = []
    for f in findings:
        ev = ""
        if f.evidence:
            items = "".join(f"<li>{html.escape(e)}</li>" for e in f.evidence[:8])
            ev = f"<ul>{items}</ul>"
        blocks.append(
            f'<div class="f {f.severity}">'
            f'<div class="t"><span>{html.escape(f.title)}</span>'
            f'<span class="sev">{f.severity}</span></div>'
            f'<div class="d">{html.escape(f.detail)}</div>{ev}</div>'
        )

    return _HTML.format(
        title=html.escape(title),
        android=html.escape(str(d.get("ro.build.version.release", "?"))),
        sdk=html.escape(str(d.get("ro.build.version.sdk", "?"))),
        patch=html.escape(str(d.get("ro.build.version.security_patch", "?"))),
        abi=html.escape(str(d.get("ro.product.cpu.abi", "?"))),
        stats=stats_html,
        sections=_html_sections(to_dict(rep, findings)) if sections else "",
        findings="\n".join(blocks),
    )


def render_json(rep: Report, findings: list[Finding], indent: int = 2) -> str:
    return json.dumps(to_dict(rep, findings), indent=indent, ensure_ascii=False)


# ------------------------------------------------------------------ info

INFO_SECTIONS = (
    "system", "traffic", "cpudetail", "display", "gpu", "network", "sim", "codec",
    "camera", "sensor", "id",
)

# The dataclass for the "id" tab is named `ident` because `id` shadows a
# builtin; the section name shown to the user is still "id", so the tab name
# has to be mapped rather than used as an attribute name.
_INFO_ATTR = {"id": "ident"}


def _scalar_lines(obj) -> list[tuple[str, str]]:
    """Flatten a dataclass into (label, value) pairs, skipping empties."""
    out: list[tuple[str, str]] = []
    for key, value in vars(obj).items():
        if value is None or value == {} or value == []:
            continue
        if isinstance(value, (list, dict)):
            continue
        text = str(value)
        if key == "kernel":
            # The full /proc/version line is unreadable in a terminal row.
            m = text.split(") #", 1)
            text = m[0] + ")" if len(m) > 1 else text
            text = text[:72] + ("…" if len(text) > 72 else "")
        out.append((key.replace("_", " "), text))
    return out


def render_info_text(rep: Report, color: bool = True, only: str | None = None) -> str:
    lines: list[str] = []
    add = lines.append
    d = rep.device
    model = f"{d.get('ro.product.manufacturer', '')} {d.get('ro.product.model', '')}".strip()
    add(_c("CekDroid", "bold", color) + _c("  info perangkat", "dim", color))
    add("")
    add(_c(model or "Perangkat tidak dikenal", "bold", color))
    add(f"  Android {d.get('ro.build.version.release', '?')} "
        f"(SDK {d.get('ro.build.version.sdk', '?')})  ·  "
        f"patch {d.get('ro.build.version.security_patch', '?')}")
    add("")

    for name in INFO_SECTIONS:
        if only and name != only:
            continue
        obj = getattr(rep, _INFO_ATTR.get(name, name), None)
        if obj is None:
            continue
        rows = _scalar_lines(obj)
        nested = [(k, v) for k, v in vars(obj).items() if isinstance(v, (list, dict)) and v]
        if not rows and not nested:
            continue
        add(_c(name.upper(), "bold", color))
        for label, value in rows:
            add(f"  {label:<22} {value}")
        for label, value in nested:
            if isinstance(value, dict):
                present = {k: v for k, v in value.items() if v is not None}
                if not present:
                    continue
                for k, v in present.items():
                    add(f"  {label + '.' + k:<22} {v}")
            else:
                items = ", ".join(str(v) if not isinstance(v, tuple)
                                   else " ".join(str(x) for x in v if x)
                                   for v in value[:8])
                add(f"  {label:<22} {items}"
                    + (f"  (+{len(value) - 8})" if len(value) > 8 else ""))
        add("")

    if not only:
        add(_c("CATATAN", "bold", color))
        for note in _platform_limits():
            add(f"  · {note}")
        add("")
    return "\n".join(lines)


def _platform_limits() -> list[str]:
    """What a non-privileged app cannot read, stated once and plainly."""
    return [
        "IMEI/MEID/IMSI/ICCID butuh READ_PRIVILEGED_PHONE_STATE (Android 10+), "
        "tidak tersedia lewat app biasa — lewat ADB probe hanya nilai mentah ril.*",
        "Serial perangkat bernilai 'unknown' untuk app biasa",
        "SSID WiFi butuh izin lokasi aktif (Android 10+)",
    ]

