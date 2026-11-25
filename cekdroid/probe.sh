#!/system/bin/sh
# cekdroid read-only probe
# Emits sections delimited by @@name lines. Parsed host-side by cekdroid.
# POSIX sh only; no vendor-specific assumptions.

sec() { echo "@@$1"; }

sec device
for p in ro.product.manufacturer ro.product.model ro.product.device \
         ro.build.version.release ro.build.version.sdk \
         ro.build.version.security_patch ro.build.date ro.build.fingerprint \
         ro.build.type ro.build.tags ro.debuggable \
         ro.boot.verifiedbootstate ro.boot.veritymode ro.boot.selinux \
         ro.product.cpu.abi ro.crypto.state ro.crypto.type ro.sf.lcd_density; do
  echo "$p=$(getprop $p 2>/dev/null)"
done

sec uptime
cat /proc/uptime 2>/dev/null
date 2>/dev/null

sec battery
dumpsys battery 2>/dev/null

sec powersupply
for d in /sys/class/power_supply/*; do
  [ -d "$d" ] || continue
  echo "[$(basename $d)]"
  for p in cycle_count charge_full charge_full_design capacity health status \
           temp voltage_now current_now charge_counter charge_type; do
    if [ -f "$d/$p" ]; then echo "$p=$(cat $d/$p 2>/dev/null)"; fi
  done
done

sec mem
cat /proc/meminfo 2>/dev/null

sec vmstat
cat /proc/vmstat 2>/dev/null | grep -E "pgscan|pgsteal|allocstall|nr_free_pages|nr_dirty|nr_writeback"

sec pressure
cat /proc/pressure/memory 2>/dev/null
cat /proc/pressure/io 2>/dev/null
cat /proc/pressure/cpu 2>/dev/null

sec storage
df /data 2>/dev/null
echo "--inodes--"
df -i /data 2>/dev/null

sec diskstats
dumpsys diskstats 2>/dev/null

sec sdcard
du -sm /sdcard/* 2>/dev/null | sort -rn | head -15

sec cpu
cat /proc/loadavg 2>/dev/null
grep "^cpu " /proc/stat 2>/dev/null
sleep 1
grep "^cpu " /proc/stat 2>/dev/null
echo "--online--"
cat /sys/devices/system/cpu/online 2>/dev/null
echo "--freq--"
for c in 0 1 2 3 4 5 6 7; do
  f=/sys/devices/system/cpu/cpu$c/cpufreq
  if [ -d "$f" ]; then
    cur=$(cat $f/scaling_cur_freq 2>/dev/null)
    mx=$(cat $f/cpuinfo_max_freq 2>/dev/null)
    echo "cpu$c=$cur/$mx"
  fi
done

sec thermal
dumpsys thermalservice 2>/dev/null | head -40
echo "--zones--"
for z in /sys/class/thermal/thermal_zone*; do
  t=$(cat $z/temp 2>/dev/null)
  n=$(cat $z/type 2>/dev/null)
  if [ -n "$t" ]; then echo "$n=$t"; fi
done

sec power
dumpsys power 2>/dev/null | grep -E "mWakefulness=|mScreenOffTimeoutSetting|mStayOn|mIsPowered|mPlugType|mBatteryLevel|mDeviceIdleMode="

sec settings
echo "screen_off_timeout=$(settings get system screen_off_timeout 2>/dev/null)"
echo "low_power=$(settings get global low_power 2>/dev/null)"
echo "low_power_sticky=$(settings get global low_power_sticky 2>/dev/null)"
echo "adb_enabled=$(settings get global adb_enabled 2>/dev/null)"
echo "development_settings_enabled=$(settings get global development_settings_enabled 2>/dev/null)"
echo "accessibility_enabled=$(settings get secure accessibility_enabled 2>/dev/null)"
echo "accessibility_services=$(settings get secure enabled_accessibility_services 2>/dev/null)"
echo "notification_listeners=$(settings get secure enabled_notification_listeners 2>/dev/null)"
echo "install_non_market_apps=$(settings get secure install_non_market_apps 2>/dev/null)"

sec doze
dumpsys deviceidle 2>/dev/null | grep -E "mState=|mLightState=|mLightEnabled|mDeepEnabled|mForceIdle|mInactiveTimeout"

sec dozewhitelist
cmd deviceidle whitelist 2>/dev/null

sec devpolicy
dumpsys device_policy 2>/dev/null | grep -A 6 "Enabled Device Admins"

sec ps
ps -A -o PID,PPID,USER,STAT,NAME 2>/dev/null

sec packages
pm list packages -3 -i -U 2>/dev/null

sec pkgcount
echo "total=$(pm list packages 2>/dev/null | wc -l)"
echo "thirdparty=$(pm list packages -3 2>/dev/null | wc -l)"

sec rootcheck
if command -v su >/dev/null 2>&1; then echo "su=present"; else echo "su=absent"; fi
ls /system/xbin/su /system/bin/su /sbin/su 2>/dev/null

sec system
cat /proc/version 2>/dev/null
getprop ro.build.date 2>/dev/null | sed 's/^/build_date=/'
getprop ro.vendor.oplus.market.name 2>/dev/null | sed 's/^/marketing_name=/'
getprop ro.product.marketname 2>/dev/null | sed 's/^/marketing_name2=/'
getprop ro.config.marketing_name 2>/dev/null | sed 's/^/marketing_name3=/'
echo "boot_count=$(settings get global boot_count 2>/dev/null)"
echo "uptime_s=$(cut -d. -f1 /proc/uptime 2>/dev/null)"
getprop ro.build.user 2>/dev/null | sed 's/^/build_user=/'
getprop ro.build.host 2>/dev/null | sed 's/^/build_host=/'
getprop ro.build.type 2>/dev/null | sed 's/^/build_type=/'
getprop ro.build.flavor 2>/dev/null | sed 's/^/build_flavor=/'
getprop ro.product.cpu.abilist 2>/dev/null | sed 's/^/abilist=/'
getprop ro.product.cpu.abilist32 2>/dev/null | sed 's/^/abilist32=/'
getprop ro.product.cpu.abilist64 2>/dev/null | sed 's/^/abilist64=/'
getprop ro.board.platform 2>/dev/null | sed 's/^/board_platform=/'
getprop ro.build.system_root_image 2>/dev/null | sed 's/^/system_as_root=/'
getprop ro.boot.dynamic_partitions 2>/dev/null | sed 's/^/dynamic_partitions=/'
getprop ro.build.ab_update 2>/dev/null | sed 's/^/seamless_updates=/'
getprop gsm.version.baseband 2>/dev/null | sed 's/^/baseband=/'
getprop ro.build.version.extension 2>/dev/null | sed 's/^/sdk_ext=/'
cat /proc/cmdline 2>/dev/null | sed 's/^/cmdline=/'

# Declared capabilities: the same list the app reads through PackageManager.
# This is a few hundred lines, but it is the only vendor-neutral source for
# "does this device support NFC / UWB / eSIM".
sec sysfeatures
pm list features 2>/dev/null | head -400

sec cpudetail
grep -E "^(processor|model name|Hardware|CPU implementer|CPU architecture|CPU variant|CPU part|CPU revision)" /proc/cpuinfo 2>/dev/null
echo "--governor--"
for c in 0 1 2 3 4 5 6 7; do
  g=/sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor
  [ -f "$g" ] && echo "cpu$c=$(cat $g 2>/dev/null)"
done
# Capacity drives cluster detection: cores sharing a capacity value are one
# cluster, which is what the app derives from a shared cpufreq policy.
echo "--capacity--"
for c in 0 1 2 3 4 5 6 7; do
  cap=/sys/devices/system/cpu/cpu$c/cpu_capacity
  [ -f "$cap" ] && echo "cpu$c=$(cat $cap 2>/dev/null)"
done
echo "--cpufreq-range--"
for c in 0 1 2 3 4 5 6 7; do
  d=/sys/devices/system/cpu/cpu$c/cpufreq
  if [ -d "$d" ]; then
    mn=$(cat $d/cpuinfo_min_freq 2>/dev/null)
    mx=$(cat $d/cpuinfo_max_freq 2>/dev/null)
    echo "cpu$c=$mn/$mx"
  fi
done

# Vulkan is native-only for an app, but the platform declares its version and
# hardware level as features, which is what the app reports too.
sec vulkan
getprop ro.hardware.vulkan 2>/dev/null | sed 's/^/driver=/'
pm list features 2>/dev/null | grep -i vulkan | head -10

# Container support as the framework registers it. The extractor list is the
# authoritative answer to "can this phone open an MKV".
sec containers
dumpsys media.extractor 2>/dev/null | grep -iE "extractor|Extractor" | head -40
echo "--muxers--"
dumpsys media.muxer 2>/dev/null | head -20
echo "--mimetypes--"
getprop ro.build.version.sdk 2>/dev/null | sed 's/^/sdk=/'

# Wi-Fi generation and security. The supplicant's own report carries the
# standard (11n/ac/ax) and the negotiated security.
sec wifi
dumpsys wifi 2>/dev/null | grep -E "mWifiStandard|mSecurityType|Supplicant state|SSID" | head -6
echo "--link--"
dumpsys wifi 2>/dev/null | grep -E "LinkSpeed|RSSI|Frequency" | head -6

# Screen-off timeout lives with the display section in the app, but the probe
# keeps it in the power block where it was already being read. Named here so
# the two producers agree on the key.
sec screentimeout
echo "screen_off_timeout=$(settings get system screen_off_timeout 2>/dev/null)"

sec display
dumpsys display 2>/dev/null | grep -E "mBaseDisplayInfo|refreshRate|density|DisplayDeviceInfo" | head -20
echo "--size--"
wm size 2>/dev/null
echo "--density--"
wm density 2>/dev/null

sec gpu
dumpsys SurfaceFlinger 2>/dev/null | grep -iE "^GLES|GLES:" | head -6
cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null | sed 's/^/kgsl_model=/'
cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | sed 's/^/kgsl_busy=/'
cat /sys/class/misc/mali0/device/gpuinfo 2>/dev/null | sed 's/^/mali=/'

sec network
getprop gsm.network.type 2>/dev/null | sed 's/^/network_type=/'
getprop gsm.operator.alpha 2>/dev/null | sed 's/^/network_operator=/'
getprop gsm.operator.iso-country 2>/dev/null | sed 's/^/network_country=/'
getprop gsm.operator.numeric 2>/dev/null | sed 's/^/operator_numeric=/'
echo "--interfaces--"
ip -f inet addr 2>/dev/null | grep -E "^[0-9]+:|inet " | head -30
echo "--wifi--"
dumpsys wifi 2>/dev/null | grep -E "mWifiInfo|SSID|supplicant" | head -5

sec traffic
# sysfs statistics are blocked for the shell user, but /proc/net/dev is not.
cat /proc/net/dev 2>/dev/null

sec sim
dumpsys telephony.registry 2>/dev/null | grep -E "mServiceState=|mSimState" | head -6
# The SIM slot's own operator, not the serving network's: these props describe
# the card in the tray, which is what the SIM tab shows.
getprop gsm.sim.operator.alpha 2>/dev/null | sed 's/^/sim_operator=/'
getprop gsm.sim.operator.iso-country 2>/dev/null | sed 's/^/sim_country=/'
getprop gsm.sim.state 2>/dev/null | sed 's/^/sim_state=/'
getprop gsm.sim.operator.numeric 2>/dev/null | sed 's/^/sim_operator_code=/'
dumpsys telephony.registry 2>/dev/null | grep -o "mOperatorAlphaLong=[^,]*" | head -1 | sed 's/mOperatorAlphaLong=/network_operator=/'

sec codec
dumpsys media.codec 2>/dev/null | grep -E "codec|Codec" | head -30
echo "--players--"
dumpsys media.player 2>/dev/null | head -15

# Camera: the shell sees the service's own device map, which is more than an
# ordinary app gets. It is a count and an ID→index map, not sensor specs;
# the spec sheet comes from the app (CameraCharacteristics is API-only).
sec camera
dumpsys media.camera 2>/dev/null | grep -E "Number of camera devices|Number of normal|Number of public|Device [0-9]+ maps to" | head -12

# Sensor: the hardware inventory as the framework knows it. The app reports
# the same list via SensorManager, so the counts should agree.
sec sensor
dumpsys sensorservice 2>/dev/null | grep -E "^Total [0-9]+ h/w sensors" | head -2
dumpsys sensorservice 2>/dev/null | sed -n '/^Sensor List:/,/^$/p' | grep -E "type: android\.sensor" | head -40

# Identifiers. android_id and the serial are readable by the shell user but
# not by an ordinary app on Android 10+, which is exactly the gap the app's
# ID tab explains instead of hiding.
sec id
echo "android_id=$(settings get secure android_id 2>/dev/null)"
echo "serial=$(getprop ro.serialno 2>/dev/null)"
echo "fingerprint=$(getprop ro.build.fingerprint 2>/dev/null)"
echo "build_id=$(getprop ro.build.id 2>/dev/null)"
echo "display_id=$(getprop ro.build.display.id 2>/dev/null)"
echo "bootloader=$(getprop ro.bootloader 2>/dev/null)"

sec end
