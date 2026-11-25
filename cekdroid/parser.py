"""Parse the raw probe output into a structured report.

The probe emits `@@section` delimited blocks. Each parser here is defensive:
vendor ROMs differ in dumpsys formatting, missing sysfs nodes, and locale,
so every field is optional and absence is recorded rather than guessed.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


def split_sections(raw: str) -> dict[str, str]:
    sections: dict[str, str] = {}
    current: str | None = None
    buf: list[str] = []
    for line in raw.splitlines():
        if line.startswith("@@"):
            if current is not None:
                sections[current] = "\n".join(buf).strip()
            current = line[2:].strip()
            buf = []
        elif current is not None:
            buf.append(line)
    if current is not None:
        sections[current] = "\n".join(buf).strip()
    return sections


def _kv(block: str, sep: str = "=") -> dict[str, str]:
    out: dict[str, str] = {}
    for line in block.splitlines():
        if sep in line:
            k, _, v = line.partition(sep)
            out[k.strip()] = v.strip()
    return out


def _int(value: str | None) -> int | None:
    if value is None:
        return None
    m = re.search(r"-?\d+", value)
    return int(m.group()) if m else None


def _float(value: str | None) -> float | None:
    if value is None:
        return None
    m = re.search(r"-?\d+(?:\.\d+)?", value)
    return float(m.group()) if m else None


@dataclass
class Battery:
    level: int | None = None
    status: str | None = None
    health: str | None = None
    temperature_c: float | None = None
    voltage_mv: int | None = None
    current_ua: int | None = None
    technology: str | None = None
    charge_counter_uah: int | None = None
    cycle_count: int | None = None
    charge_full_uah: int | None = None
    charge_full_design_uah: int | None = None
    capacity_level: str | None = None
    ac_powered: bool | None = None
    usb_powered: bool | None = None
    wireless_powered: bool | None = None
    health_pct: float | None = None

    @property
    def wear_pct(self) -> float | None:
        if self.charge_full_uah and self.charge_full_design_uah:
            if self.charge_full_design_uah > 0:
                return round(100.0 * self.charge_full_uah / self.charge_full_design_uah, 1)
        return None


@dataclass
class Memory:
    total_kb: int | None = None
    free_kb: int | None = None
    available_kb: int | None = None
    cached_kb: int | None = None
    swap_total_kb: int | None = None
    swap_free_kb: int | None = None
    zram_used_kb: int | None = None
    pgsteal_direct: int | None = None
    pgscan_direct: int | None = None
    allocstall: int = 0
    pressure_memory_some_avg10: float | None = None

    @property
    def swap_used_kb(self) -> int | None:
        if self.swap_total_kb is None or self.swap_free_kb is None:
            return None
        return max(0, self.swap_total_kb - self.swap_free_kb)

    @property
    def swap_used_pct(self) -> float | None:
        used = self.swap_used_kb
        if not used or not self.swap_total_kb:
            return None
        return round(100.0 * used / self.swap_total_kb, 1)

    @property
    def used_pct(self) -> float | None:
        if self.total_kb and self.available_kb is not None:
            used = self.total_kb - self.available_kb
            return round(100.0 * used / self.total_kb, 1)
        return None


@dataclass
class Storage:
    total_kb: int | None = None
    used_kb: int | None = None
    free_kb: int | None = None
    inode_total: int | None = None
    inode_used: int | None = None
    app_size: int | None = None
    app_data_size: int | None = None
    photos_size: int | None = None
    videos_size: int | None = None
    audio_size: int | None = None
    sdcard_dirs: list[tuple[int, str]] = field(default_factory=list)

    @property
    def free_pct(self) -> float | None:
        if self.total_kb and self.free_kb is not None:
            return round(100.0 * self.free_kb / self.total_kb, 1)
        return None

    @property
    def inode_used_pct(self) -> float | None:
        if self.inode_total and self.inode_used is not None:
            return round(100.0 * self.inode_used / self.inode_total, 1)
        return None


@dataclass
class Cpu:
    load1: float | None = None
    load5: float | None = None
    load15: float | None = None
    cores_online: int | None = None
    idle_pct: float | None = None
    frequencies: list[tuple[str, int, int]] = field(default_factory=list)
    uptime_h: float | None = None


@dataclass
class Thermal:
    zones: list[tuple[str, float]] = field(default_factory=list)
    max_c: float | None = None
    status: str | None = None


@dataclass
class Power:
    screen_off_timeout_ms: int | None = None
    low_power: bool | None = None
    wakefulness: str | None = None
    doze_state: str | None = None
    doze_light_state: str | None = None
    doze_whitelist: list[tuple[str, str, int | None]] = field(default_factory=list)


@dataclass
class Security:
    security_patch: str | None = None
    boot_verified: str | None = None
    verity: str | None = None
    debug: bool | None = None
    accessibility_enabled: bool | None = None
    accessibility_services: list[str] = field(default_factory=list)
    notification_listeners: list[str] = field(default_factory=list)
    device_admins: list[str] = field(default_factory=list)
    su_present: bool | None = None
    selinux: str | None = None


@dataclass
class Process:
    pid: int
    ppid: int
    user: str
    stat: str
    name: str

    @property
    def state(self) -> str:
        return self.stat[:1]


@dataclass
class Package:
    name: str
    uid: int | None = None
    installer: str | None = None

    @property
    def sideloaded(self) -> bool:
        if self.installer is None:
            return False
        if self.installer in ("com.android.vending", "com.google.android.packageinstaller"):
            return self.installer == "com.google.android.packageinstaller"
        return True

    @property
    def self_installing(self) -> bool:
        return self.installer == self.name


@dataclass
class System:
    kernel: str | None = None
    build_date: str | None = None
    build_user: str | None = None
    build_host: str | None = None
    build_type: str | None = None
    build_flavor: str | None = None
    abilist: str | None = None
    abilist32: str | None = None
    abilist64: str | None = None
    board_platform: str | None = None
    kernel_cmdline: str | None = None
    marketing_name: str | None = None
    boot_count: int | None = None
    uptime_text: str | None = None
    # Rows DevInfo prints beside the build identity. Each is a getprop value
    # that a ROM may not define; None means "not reported", never "false".
    system_as_root: str | None = None
    dynamic_partitions: str | None = None
    seamless_updates: str | None = None
    baseband: str | None = None
    sdk_ext: str | None = None


@dataclass
class SystemFeatures:
    """The declared feature list, one entry per `pm list features` line."""
    features: list[tuple[str, str | None]] = field(default_factory=list)
    gles: str | None = None


@dataclass
class Vulkan:
    driver: str | None = None
    version: str | None = None
    level: str | None = None
    compute: bool | None = None


@dataclass
class Containers:
    """Extractor class names the media framework registers."""
    extractors: list[str] = field(default_factory=list)


@dataclass
class Traffic:
    mobile_rx_kb: int | None = None
    mobile_tx_kb: int | None = None
    wifi_rx_kb: int | None = None
    wifi_tx_kb: int | None = None


@dataclass
class CpuDetail:
    model_name: str | None = None
    hardware: str | None = None
    implementer: str | None = None
    architecture: str | None = None
    variant: str | None = None
    part: str | None = None
    revision: str | None = None
    governors: dict[str, str] = field(default_factory=dict)
    # cpu_capacity per core, the kernel's own cluster hint.
    capacities: dict[str, int] = field(default_factory=dict)
    # (min_khz, max_khz) per core from cpufreq, used to group clusters.
    ranges: dict[str, tuple[int, int]] = field(default_factory=dict)


@dataclass
class Display:
    width_px: int | None = None
    height_px: int | None = None
    density_dpi: int | None = None
    refresh_hz: float | None = None
    name: str | None = None


@dataclass
class Gpu:
    kgsl_model: str | None = None
    kgsl_busy: str | None = None
    mali: str | None = None
    gles: list[str] = field(default_factory=list)


@dataclass
class Network:
    network_type: str | None = None
    network_operator: str | None = None
    network_country: str | None = None
    operator_numeric: str | None = None
    interfaces: list[tuple[str, str]] = field(default_factory=list)


@dataclass
class Sim:
    sim_state: str | None = None
    sim_operator: str | None = None
    sim_country: str | None = None
    service_state: str | None = None
    iccid_raw: str | None = None


@dataclass
class Codec:
    lines: list[str] = field(default_factory=list)


@dataclass
class Camera:
    device_count: int | None = None
    normal_count: int | None = None
    public_count: int | None = None
    # (service id, API id) pairs, e.g. ("2", "3")
    mapping: list[tuple[str, str]] = field(default_factory=list)


@dataclass
class Sensor:
    total_hw: int | None = None
    running: int | None = None
    # (name, vendor, type) one entry per sensor the framework lists
    sensors: list[tuple[str, str, str]] = field(default_factory=list)


@dataclass
class Ident:
    android_id: str | None = None
    serial: str | None = None
    fingerprint: str | None = None
    build_id: str | None = None
    display_id: str | None = None
    bootloader: str | None = None


@dataclass
class Report:
    device: dict[str, str] = field(default_factory=dict)
    battery: Battery = field(default_factory=Battery)
    memory: Memory = field(default_factory=Memory)
    storage: Storage = field(default_factory=Storage)
    cpu: Cpu = field(default_factory=Cpu)
    thermal: Thermal = field(default_factory=Thermal)
    power: Power = field(default_factory=Power)
    security: Security = field(default_factory=Security)
    packages: list[Package] = field(default_factory=list)
    processes: list[Process] = field(default_factory=list)
    package_total: int | None = None
    package_thirdparty: int | None = None
    system: System = field(default_factory=System)
    features: SystemFeatures = field(default_factory=SystemFeatures)
    cpudetail: CpuDetail = field(default_factory=CpuDetail)
    display: Display = field(default_factory=Display)
    gpu: Gpu = field(default_factory=Gpu)
    vulkan: Vulkan = field(default_factory=Vulkan)
    containers: Containers = field(default_factory=Containers)
    network: Network = field(default_factory=Network)
    traffic: Traffic = field(default_factory=Traffic)
    sim: Sim = field(default_factory=Sim)
    codec: Codec = field(default_factory=Codec)
    camera: Camera = field(default_factory=Camera)
    sensor: Sensor = field(default_factory=Sensor)
    ident: Ident = field(default_factory=Ident)
    raw_sections: dict[str, str] = field(default_factory=dict)


def _first_line(block: str) -> str | None:
    for line in block.splitlines():
        if line.strip():
            return line.strip()
    return None


def _parse_system(block: str) -> System:
    s = System()
    kv = _kv(block)
    s.build_date = kv.get("build_date") or None
    s.build_user = kv.get("build_user") or None
    s.build_host = kv.get("build_host") or None
    s.build_type = kv.get("build_type") or None
    s.build_flavor = kv.get("build_flavor") or None
    s.abilist = kv.get("abilist") or None
    s.abilist32 = kv.get("abilist32") or None
    s.abilist64 = kv.get("abilist64") or None
    s.board_platform = kv.get("board_platform") or None
    s.kernel_cmdline = kv.get("cmdline") or None

    # The DevInfo parity rows. A getprop that prints nothing yields an empty
    # string here, which becomes None — "not reported", not "false".
    s.system_as_root = kv.get("system_as_root") or None
    s.dynamic_partitions = kv.get("dynamic_partitions") or None
    s.seamless_updates = kv.get("seamless_updates") or None
    s.baseband = kv.get("baseband") or None
    s.sdk_ext = kv.get("sdk_ext") or None

    # First non-empty vendor marketing-name prop wins, mirroring the app.
    for key in ("marketing_name", "marketing_name2", "marketing_name3"):
        value = (kv.get(key) or "").strip()
        if value:
            s.marketing_name = value
            break

    boots = _int(kv.get("boot_count"))
    s.boot_count = boots if boots is not None and boots >= 0 else None

    uptime = _int(kv.get("uptime_s"))
    if uptime is not None and uptime >= 0:
        s.uptime_text = _format_uptime(uptime)

    for line in block.splitlines():
        if line.startswith("Linux version"):
            s.kernel = line.strip()
            break
    return s


def _format_uptime(seconds: int) -> str:
    d, rem = divmod(seconds, 86400)
    h, rem = divmod(rem, 3600)
    m, sec = divmod(rem, 60)
    prefix = f"{d}d " if d else ""
    return f"{prefix}{h}h {m}m {sec}s"


def _parse_traffic(block: str) -> Traffic:
    """Sum /proc/net/dev rows into Wi-Fi and cellular buckets.

    Columns after the colon are: rx_bytes rx_packets ... tx_bytes at index 8.
    Interfaces starting with "ccmni" / "rmnet" are cellular; "wlan" / "ap" are
    Wi-Fi. Anything else (lo, tunl0, dummy0) is skipped so the totals are not
    inflated by loopback traffic.
    """
    t = Traffic()
    wifi_rx = wifi_tx = mobile_rx = mobile_tx = 0
    seen_wifi = seen_mobile = False

    for line in block.splitlines():
        if ":" not in line:
            continue
        name, _, rest = line.partition(":")
        name = name.strip()
        parts = rest.split()
        if len(parts) < 9:
            continue
        try:
            rx = int(parts[0])
            tx = int(parts[8])
        except ValueError:
            continue

        cellular = name.startswith("ccmni") or name.startswith("rmnet")
        wireless = name.startswith("wlan") or name.startswith("ap")
        if cellular:
            mobile_rx += rx
            mobile_tx += tx
            seen_mobile = True
        elif wireless:
            wifi_rx += rx
            wifi_tx += tx
            seen_wifi = True

    if seen_wifi:
        t.wifi_rx_kb = wifi_rx // 1024
        t.wifi_tx_kb = wifi_tx // 1024
    if seen_mobile:
        t.mobile_rx_kb = mobile_rx // 1024
        t.mobile_tx_kb = mobile_tx // 1024
    return t


def _parse_cpudetail(block: str) -> CpuDetail:
    d = CpuDetail()
    section = "--governor--"
    for line in block.splitlines():
        line = line.strip()
        if not line:
            continue
        # Markers inside cpudetail tell the three tables apart: the governor
        # rows, the cpu_capacity rows, and the cpufreq ranges. They are checked
        # before the generic "--" skip so they can change the active table.
        if line == "--governor--":
            section = "--governor--"
            continue
        if line == "--capacity--":
            section = "--capacity--"
            continue
        if line == "--cpufreq-range--":
            section = "--cpufreq-range--"
            continue
        if line.startswith("--"):
            continue

        if line.startswith("cpu") and "=" in line:
            key, _, value = line.partition("=")
            key, value = key.strip(), value.strip()
            if section == "--capacity--":
                parsed = _int(value)
                if parsed is not None:
                    d.capacities[key] = parsed
            elif section == "--cpufreq-range--":
                parts = value.split("/")
                if len(parts) == 2:
                    lo, hi = _int(parts[0]), _int(parts[1])
                    if lo is not None and hi is not None:
                        d.ranges[key] = (lo, hi)
            else:
                d.governors[key] = value or None
            continue

        key, _, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if key == "model name":
            d.model_name = value or None
        elif key == "Hardware":
            d.hardware = value or None
        elif key == "CPU implementer":
            d.implementer = value or None
        elif key == "CPU architecture":
            d.architecture = value or None
        elif key == "CPU variant":
            d.variant = value or None
        elif key == "CPU part":
            d.part = value or None
        elif key == "CPU revision":
            d.revision = value or None
    return d


def _parse_features(block: str) -> SystemFeatures:
    """`pm list features` prints "feature:name=value" or "feature:name"."""
    f = SystemFeatures()
    for line in block.splitlines():
        line = line.strip()
        if not line.startswith("feature:"):
            continue
        body = line[len("feature:"):]
        name, _, value = body.partition("=")
        name = name.strip()
        if not name:
            continue
        if name == "android.hardware.opengles.aep" or "opengles" in name:
            if value:
                f.gles = value
        f.features.append((name, value.strip() if value else None))
    return f


def _parse_vulkan(block: str) -> Vulkan:
    v = Vulkan()
    for line in block.splitlines():
        text = line.strip()
        if text.startswith("driver="):
            v.driver = text.split("=", 1)[1].strip() or None
            continue
        if not text.startswith("feature:"):
            continue
        body = text[len("feature:"):]
        name, _, value = body.partition("=")
        name, value = name.strip(), value.strip()
        if name == "android.hardware.vulkan.version":
            v.version = value or None
        elif name == "android.hardware.vulkan.level":
            v.level = value or None
        elif name == "android.hardware.vulkan.compute":
            v.compute = value in ("", "true", "1")
    return v


def _parse_containers(block: str) -> Containers:
    c = Containers()
    for line in block.splitlines():
        text = line.strip()
        if not text or text.startswith("--"):
            continue
        # The extractor dump names one class per line; anything with
        # "Extractor" in it is a registered parser.
        if "Extractor" in text:
            c.extractors.append(text)
    return c


def _parse_display(block: str) -> Display:
    d = Display()
    import re as _re
    for line in block.splitlines():
        line = line.strip()
        for pattern, attr in (
            (r"real (\d+) x (\d+)", "size"),
            (r"density (\d+)", "density"),
        ):
            m = _re.search(pattern, line)
            if m and attr == "size":
                d.width_px, d.height_px = int(m.group(1)), int(m.group(2))
            elif m and attr == "density":
                d.density_dpi = int(m.group(1))
        m = _re.search(r"refreshRate[= ]+(\d+(?:\.\d+)?)", line)
        if m:
            d.refresh_hz = float(m.group(1))
        if "DisplayDeviceInfo{" in line:
            m = _re.search(r'DisplayDeviceInfo\{"([^"]+)"', line)
            d.name = m.group(1) if m else None
    return d


def _parse_gpu(block: str) -> Gpu:
    g = Gpu()
    kv = _kv(block)
    g.kgsl_model = kv.get("kgsl_model") or None
    g.kgsl_busy = kv.get("kgsl_busy") or None
    g.mali = kv.get("mali") or None
    for line in block.splitlines():
        stripped = line.strip()
        if stripped.startswith("GLES"):
            g.gles.append(stripped)
    return g


def _normalize_sim_state(value: str | None) -> str | None:
    """getprop prints LOADED/READY; the app asks TelephonyManager and gets
    `ready`. Normalising here is what makes the two reports comparable."""
    if value is None:
        return None
    text = value.strip()
    if not text:
        return None
    upper = text.upper()
    if upper in ("LOADED", "READY"):
        return "ready"
    if upper == "ABSENT":
        return "absent"
    if upper == "NOT_READY":
        return "not_ready"
    if upper == "PIN_REQUIRED":
        return "pin_required"
    if upper == "PUK_REQUIRED":
        return "puk_required"
    if upper == "NETWORK_LOCKED":
        return "network_locked"
    if upper in ("CARD_IO_ERROR", "IO_ERROR", "ERROR"):
        return "card_io_error"
    if upper in ("PERM_DISABLED", "PERM_DISABLED"):
        return "perm_disabled"
    return text.lower()


def _first_value(value: str | None) -> str | None:
    """getprop returns multi-slot values as "by.U," — keep the first slot."""
    if not value:
        return None
    cleaned = value.split(",")[0].strip()
    return cleaned or None


def _parse_network(block: str) -> Network:
    n = Network()
    kv = _kv(block)
    n.network_type = _first_value(kv.get("network_type"))
    n.network_operator = _first_value(kv.get("network_operator"))
    n.network_country = _first_value(kv.get("network_country"))
    n.operator_numeric = _first_value(kv.get("operator_numeric"))
    in_interfaces = False
    for line in block.splitlines():
        if line.strip() == "--interfaces--":
            in_interfaces = True
            continue
        if line.strip() == "--wifi--":
            in_interfaces = False
            continue
        if not in_interfaces:
            continue
        stripped = line.strip()
        if ":" in stripped and stripped.split(":")[0].isdigit():
            n.interfaces.append((stripped.split(":")[1].strip(), ""))
        elif stripped.startswith("inet "):
            parts = stripped.split()
            if len(parts) >= 2:
                if n.interfaces:
                    iface, _ = n.interfaces[-1]
                    n.interfaces[-1] = (iface, parts[1])
                else:
                    n.interfaces.append(("", parts[1]))
    return n


def _parse_sim(block: str) -> Sim:
    s = Sim()
    kv = _kv(block)
    s.sim_state = _normalize_sim_state(_first_value(kv.get("sim_state")))
    s.sim_operator = _first_value(kv.get("sim_operator"))
    s.sim_country = _first_value(kv.get("sim_country"))
    s.iccid_raw = kv.get("iccid_raw") or None
    for line in block.splitlines():
        if "mServiceState=" not in line:
            continue
        # The raw dumpsys line is thousands of characters. Pull out only the
        # registration facts worth showing.
        import re as _re
        text = line.split("mServiceState=", 1)[1]
        parts = []
        m = _re.search(r"mVoiceRegState=\d+\(([^)]+)\)", text)
        if m:
            parts.append("voice=" + m.group(1))
        m = _re.search(r"mDataRegState=\d+\(([^)]+)\)", text)
        if m:
            parts.append("data=" + m.group(1))
        m = _re.search(r"mOperatorAlphaLong=([^,]+)", text)
        if m:
            parts.append("operator=" + m.group(1).strip())
        m = _re.search(r"getRilDataRadioTechnology=\d+\(([^)]+)\)", text)
        if m:
            parts.append("radio=" + m.group(1))
        s.service_state = ", ".join(parts) or None
        break
    return s


def _parse_codec(block: str) -> Codec:
    c = Codec()
    for line in block.splitlines():
        if line.strip():
            c.lines.append(line.rstrip())
    return c


def _parse_camera(block: str) -> Camera:
    """Camera *counts* only: the shell sees the service map, not sensor specs."""
    c = Camera()
    for line in block.splitlines():
        text = line.strip()
        if "Number of camera devices:" in text:
            c.device_count = _int(text.split(":", 1)[1])
        elif "Number of normal camera devices:" in text:
            c.normal_count = _int(text.split(":", 1)[1])
        elif "Number of public camera devices" in text:
            c.public_count = _int(text.split(":", 1)[1])
        else:
            # "Device 2 maps to "3""
            m = re.match(r'Device (\d+) maps to "?(\d+)"?', text)
            if m:
                c.mapping.append((m.group(1), m.group(2)))
    return c


def _parse_sensor(block: str) -> Sensor:
    s = Sensor()
    for line in block.splitlines():
        text = line.strip()
        m = re.match(r"Total (\d+) h/w sensors, (\d+) running", text)
        if m:
            s.total_hw = int(m.group(1))
            s.running = int(m.group(2))
            continue
        # "0x00000001) kx022 ACCELEROMETER | invensen | ver: 1 | type: ..."
        m = re.match(r"0x[0-9a-fA-F]+\)\s+(.+?)\s*\|\s*(.+?)\s*\|.*?"
                     r"type:\s*(\S+?)\((\d+)\)", text)
        if m:
            s.sensors.append((m.group(1).strip(), m.group(2).strip(), m.group(3)))
    return s


def _parse_ident(block: str) -> Ident:
    kv = _kv(block)
    i = Ident()
    i.android_id = kv.get("android_id") or None
    i.serial = kv.get("serial") or None
    i.fingerprint = kv.get("fingerprint") or None
    i.build_id = kv.get("build_id") or None
    i.display_id = kv.get("display_id") or None
    i.bootloader = kv.get("bootloader") or None
    return i



def _parse_battery(block: str, ps_block: str) -> Battery:
    b = Battery()
    kv = _kv(block, sep=":")
    level = _int(kv.get("level"))
    b.level = level
    b.status = kv.get("status")
    # `dumpsys battery` prints `health: 2`. Android's BatteryManager constants
    # are the only correct decoding (2 == good); showing the raw integer made
    # the CLI disagree with the app, which prints the name.
    health = kv.get("health")
    if health is not None and health.strip().isdigit():
        b.health = {
            1: "unknown",
            2: "good",
            3: "overheat",
            4: "dead",
            5: "over_voltage",
            6: "failure",
            7: "cold",
        }.get(int(health.strip()), health.strip())
    else:
        b.health = health
    temp = _float(kv.get("temperature"))
    if temp is not None:
        b.temperature_c = round(temp / 10.0, 1)
    b.voltage_mv = _int(kv.get("voltage"))
    b.current_ua = _int(kv.get("Battery current"))
    if b.current_ua is None:
        b.current_ua = _int(kv.get("current_now"))
    b.technology = kv.get("technology")
    b.charge_counter_uah = _int(kv.get("Charge counter"))
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("AC powered:"):
            b.ac_powered = line.endswith("true")
        elif line.startswith("USB powered:"):
            b.usb_powered = line.endswith("true")
        elif line.startswith("Wireless powered:"):
            b.wireless_powered = line.endswith("true")

    current_batt: dict[str, str] = {}
    for line in ps_block.splitlines():
        line = line.strip()
        if line.startswith("[") and line.endswith("]"):
            current_batt = {}
        elif "=" in line:
            k, _, v = line.partition("=")
            current_batt[k.strip()] = v.strip()
            if k.strip() == "cycle_count":
                b.cycle_count = _int(v)
            elif k.strip() == "charge_full":
                b.charge_full_uah = _int(v)
            elif k.strip() == "charge_full_design":
                b.charge_full_design_uah = _int(v)
            elif k.strip() == "capacity_level":
                b.capacity_level = v.strip()
            elif k.strip() == "health" and b.health is None:
                b.health = v.strip()
    return b


def _parse_memory(block: str, vm_block: str, pressure_block: str) -> Memory:
    m = Memory()
    kv = _kv(block, sep=":")
    m.total_kb = _int(kv.get("MemTotal"))
    m.free_kb = _int(kv.get("MemFree"))
    m.available_kb = _int(kv.get("MemAvailable"))
    m.cached_kb = _int(kv.get("Cached"))
    m.swap_total_kb = _int(kv.get("SwapTotal"))
    m.swap_free_kb = _int(kv.get("SwapFree"))
    m.zram_used_kb = _int(kv.get("ZramUsed"))

    vkv = _kv(vm_block, sep=" ")
    m.pgsteal_direct = _int(vkv.get("pgsteal_direct"))
    m.pgscan_direct = _int(vkv.get("pgscan_direct"))
    m.allocstall = sum(
        _int(v) or 0 for k, v in vkv.items() if k.startswith("allocstall")
    )

    for line in pressure_block.splitlines():
        if line.startswith("some"):
            m.pressure_memory_some_avg10 = _float(
                re.search(r"avg10=([\d.]+)", line).group(1)
                if re.search(r"avg10=([\d.]+)", line)
                else None
            )
            break
    return m


_DATA_MOUNTS = ("/data", "/data/user/0", "/data/user_de/0")


def _parse_storage(df_block: str, inode_block: str, disk_block: str, sdcard_block: str) -> Storage:
    s = Storage()

    def find_mount(block: str, need_cols: int) -> list[str] | None:
        fallback: list[str] | None = None
        for line in block.splitlines():
            parts = line.split()
            if len(parts) < need_cols:
                continue
            mount = parts[-1]
            if mount in _DATA_MOUNTS:
                if mount == "/data":
                    return parts
                if fallback is None:
                    fallback = parts
        return fallback

    parts = find_mount(df_block, 6)
    if parts:
        s.total_kb = _int(parts[1])
        s.used_kb = _int(parts[2])
        s.free_kb = _int(parts[3])

    iparts = find_mount(inode_block, 6)
    if iparts:
        s.inode_total = _int(iparts[1])
        s.inode_used = _int(iparts[2])
    for line in disk_block.splitlines():
        line = line.strip()
        for key, attr in (
            ("App Size:", "app_size"),
            ("App Data Size:", "app_data_size"),
            ("Photos Size:", "photos_size"),
            ("Videos Size:", "videos_size"),
            ("Audio Size:", "audio_size"),
        ):
            if line.startswith(key):
                setattr(s, attr, _int(line.split(":", 1)[1]))
    for line in sdcard_block.splitlines():
        parts = line.split(None, 1)
        if len(parts) == 2 and _int(parts[0]) is not None:
            s.sdcard_dirs.append((_int(parts[0]) or 0, parts[1].strip()))
    return s


def _parse_cpu(block: str, uptime_block: str) -> Cpu:
    c = Cpu()
    first_line = block.splitlines()[0] if block else ""
    parts = first_line.split()
    if len(parts) >= 3:
        c.load1 = _float(parts[0])
        c.load5 = _float(parts[1])
        c.load15 = _float(parts[2])

    stat_samples = [line for line in block.splitlines() if line.startswith("cpu ")]
    if len(stat_samples) >= 2:
        def totals(line: str) -> tuple[int, int]:
            nums = [int(x) for x in line.split()[1:]]
            idle = nums[3] + (nums[4] if len(nums) > 4 else 0)
            return sum(nums), idle

        t1, i1 = totals(stat_samples[0])
        t2, i2 = totals(stat_samples[1])
        dt, di = t2 - t1, i2 - i1
        if dt > 0:
            c.idle_pct = round(100.0 * di / dt, 1)

    # The probe writes a "--online--" marker and the sysfs content on the next
    # line, so a same-line regex never matched and cores_online stayed None.
    online_part = block.split("--online--", 1)
    if len(online_part) == 2:
        spec = online_part[1].strip().splitlines()
        spec = spec[0].strip() if spec else ""
        if spec:
            try:
                if "-" in spec and "," not in spec:
                    lo, hi = spec.split("-", 1)
                    c.cores_online = int(hi) - int(lo) + 1
                else:
                    ranges = spec.split(",")
                    total = 0
                    for item in ranges:
                        if "-" in item:
                            lo, hi = item.split("-", 1)
                            total += int(hi) - int(lo) + 1
                        else:
                            total += 1
                    c.cores_online = total
            except ValueError:
                c.cores_online = None

    for line in block.splitlines():
        m = re.match(r"(cpu\d+)=(\d+)/(\d+)", line.strip())
        if m:
            c.frequencies.append((m.group(1), int(m.group(2)), int(m.group(3))))

    up = _float(uptime_block.split()[0] if uptime_block.split() else None)
    if up is not None:
        c.uptime_h = round(up / 3600.0, 2)
    return c


_THERMAL_RE = re.compile(
    r"Temperature\{mValue=(-?\d+(?:\.\d+)?).*?mName=([A-Za-z0-9_ ./()-]+)"
)
_ZONE_RE = re.compile(r"^\s*[\w./-]+=(-?\d+)\s*$")


def _parse_thermal(block: str) -> Thermal:
    """Handle both AOSP `Temperature{...}` dumpsys output and raw sysfs zones."""
    t = Thermal()

    hal = block.split("Current temperatures from HAL:", 1)
    hal_part = hal[1] if len(hal) > 1 else ""
    cached = block.split("Cached temperatures:", 1)
    cached_part = cached[1].split("HAL Ready:", 1)[0] if len(cached) > 1 else ""

    for source, part in (("hal", hal_part), ("cached", cached_part)):
        for m in _THERMAL_RE.finditer(part):
            value = float(m.group(1))
            name = m.group(2).strip()
            if value == 0.0 or not (-40 <= value <= 150):
                continue
            label = f"{name}"
            if any(z[0] == label for z in t.zones):
                continue
            t.zones.append((label, round(value, 1)))

    if not t.zones:
        for line in block.splitlines():
            m = re.match(r"([A-Za-z0-9_./-]+)=(-?\d+)$", line.strip())
            if not m:
                continue
            name, raw = m.group(1), int(m.group(2))
            temp = raw / 1000.0 if abs(raw) > 1000 else raw / 10.0
            if -40 <= temp <= 150:
                t.zones.append((name, round(temp, 1)))

    if t.zones:
        t.max_c = max(z[1] for z in t.zones)

    m_stat = re.search(r"Thermal Status:\s*(\d+)", block)
    if m_stat:
        t.status = m_stat.group(1).strip()
    return t


def _parse_power(block: str, settings_block: str, doze_block: str, wl_block: str) -> Power:
    p = Power()
    kv = _kv(block)
    p.screen_off_timeout_ms = _int(kv.get("mScreenOffTimeoutSetting"))
    p.wakefulness = kv.get("mWakefulness")

    skv = _kv(settings_block)
    p.screen_off_timeout_ms = p.screen_off_timeout_ms or _int(skv.get("screen_off_timeout"))
    lp = skv.get("low_power")
    if lp is not None:
        p.low_power = lp.strip() == "1"

    dkv = _kv(doze_block)
    p.doze_state = dkv.get("mState")
    p.doze_light_state = dkv.get("mLightState")

    for line in wl_block.splitlines():
        parts = line.strip().split(",")
        if len(parts) >= 2:
            p.doze_whitelist.append((parts[0], parts[1], _int(parts[2]) if len(parts) > 2 else None))
    return p


def _parse_security(device_block: str, settings_block: str, policy_block: str, root_block: str) -> Security:
    dkv = _kv(device_block)
    skv = _kv(settings_block)
    s = Security()
    s.security_patch = dkv.get("ro.build.version.security_patch") or None
    s.boot_verified = dkv.get("ro.boot.verifiedbootstate") or None
    s.verity = dkv.get("ro.boot.veritymode") or None
    s.selinux = dkv.get("ro.boot.selinux") or None
    dbg = dkv.get("ro.debuggable")
    if dbg is not None:
        s.debug = dbg.strip() == "1"

    ax = skv.get("accessibility_enabled")
    if ax is not None:
        s.accessibility_enabled = ax.strip() == "1"
    raw_ax = skv.get("accessibility_services") or ""
    if raw_ax and raw_ax.strip() not in ("null", ""):
        s.accessibility_services = [x for x in raw_ax.split(":") if x]
    raw_nl = skv.get("notification_listeners") or ""
    if raw_nl and raw_nl.strip() not in ("null", ""):
        s.notification_listeners = [x for x in raw_nl.split(":") if x]

    for line in policy_block.splitlines():
        if "ComponentInfo{" in line:
            m = re.search(r"ComponentInfo\{([^}]+)\}", line)
            if m:
                s.device_admins.append(m.group(1))

    if "su=present" in root_block:
        s.su_present = True
    elif "su=absent" in root_block:
        s.su_present = False
    return s


def _parse_packages(block: str, count_block: str) -> tuple[list[Package], int | None, int | None]:
    pkgs: list[Package] = []
    for line in block.splitlines():
        line = line.strip()
        if not line.startswith("package:"):
            continue
        body = line[len("package:"):]
        tokens = body.split()
        name = tokens[0] if tokens else ""
        if not name:
            continue
        uid = None
        installer = None
        for tok in tokens[1:]:
            if tok.startswith("uid:"):
                uid = _int(tok.split(":", 1)[1])
            elif tok.startswith("installer="):
                installer = tok.split("=", 1)[1]
        pkgs.append(Package(name=name, uid=uid, installer=installer))
    kv = _kv(count_block)
    return pkgs, _int(kv.get("total")), _int(kv.get("thirdparty"))


def _parse_processes(block: str) -> list[Process]:
    procs: list[Process] = []
    lines = block.splitlines()
    for line in lines:
        parts = line.split()
        if len(parts) < 5:
            continue
        if parts[0] == "PID":
            continue
        try:
            pid = int(parts[0])
        except ValueError:
            continue
        stat = parts[3]
        name = " ".join(parts[4:])
        procs.append(Process(pid=pid, ppid=int(parts[1]) if parts[1].isdigit() else 0,
                             user=parts[2], stat=stat, name=name))
    return procs


def parse(raw: str) -> Report:
    sec = split_sections(raw)
    rep = Report(raw_sections=sec)

    rep.device = _kv(sec.get("device", ""))
    rep.battery = _parse_battery(sec.get("battery", ""), sec.get("powersupply", ""))
    rep.memory = _parse_memory(
        sec.get("mem", ""), sec.get("vmstat", ""), sec.get("pressure", "")
    )
    rep.storage = _parse_storage(
        sec.get("storage", "").split("--inodes--")[0],
        sec.get("storage", "").split("--inodes--")[-1],
        sec.get("diskstats", ""),
        sec.get("sdcard", ""),
    )
    rep.cpu = _parse_cpu(sec.get("cpu", ""), sec.get("uptime", ""))
    rep.thermal = _parse_thermal(sec.get("thermal", ""))
    rep.power = _parse_power(
        sec.get("power", ""), sec.get("settings", ""),
        sec.get("doze", ""), sec.get("dozewhitelist", ""),
    )
    rep.security = _parse_security(
        sec.get("device", ""), sec.get("settings", ""),
        sec.get("devpolicy", ""), sec.get("rootcheck", ""),
    )
    pkgs, total, third = _parse_packages(sec.get("packages", ""), sec.get("pkgcount", ""))
    rep.packages = pkgs
    rep.package_total = total
    rep.package_thirdparty = third
    rep.processes = _parse_processes(sec.get("ps", ""))
    rep.system = _parse_system(sec.get("system", ""))
    rep.features = _parse_features(sec.get("sysfeatures", ""))
    rep.cpudetail = _parse_cpudetail(sec.get("cpudetail", ""))
    rep.display = _parse_display(sec.get("display", ""))
    rep.gpu = _parse_gpu(sec.get("gpu", ""))
    rep.vulkan = _parse_vulkan(sec.get("vulkan", ""))
    rep.containers = _parse_containers(sec.get("containers", ""))
    rep.network = _parse_network(sec.get("network", ""))
    rep.traffic = _parse_traffic(sec.get("traffic", ""))
    rep.sim = _parse_sim(sec.get("sim", ""))
    rep.codec = _parse_codec(sec.get("codec", ""))
    rep.camera = _parse_camera(sec.get("camera", ""))
    rep.sensor = _parse_sensor(sec.get("sensor", ""))
    rep.ident = _parse_ident(sec.get("id", ""))
    return rep
