package ai.cekdroid.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Human wording for every data key.
 *
 * The collector stores machine keys (meminfo_free_kb, wear_pct, ...) because
 * those are stable and match the CLI JSON. Showing them raw is what made the
 * old UI unreadable, so each key is mapped here to a readable label and, where
 * the number alone is not self-explanatory, a one-sentence explanation.
 *
 * A key without an entry still renders (auto-capitalised) — this table adds
 * clarity, it does not gate what is visible.
 */
public final class FieldMeta {

    public static final class Meta {
        public final String label;
        public final String hint;

        Meta(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private static final Map<String, Meta> META = new LinkedHashMap<>();

    private static void put(String key, String label, String hint) {
        META.put(key, new Meta(label, hint));
    }

    private static void put(String key, String label) {
        META.put(key, new Meta(label, null));
    }

    static {
        // ---------------------------------------------------------- device
        put("manufacturer", "Merek");
        put("brand", "Brand");
        put("marketing_name", "Nama pemasaran", "Nama yang dipakai di toko dan iklan, mis. OPPO A16.");
        put("model", "Kode model", "Kode pabrik perangkat, mis. CPH2269.");
        put("uptime_text", "Waktu aktif", "Sudah berapa lama perangkat menyala tanpa reboot.");
        put("boot_count", "Jumlah boot", "Berapa kali perangkat dinyalakan sejak data sistem dibuat.");
        put("device", "Nama perangkat", "Nama internal yang dipakai ROM, bukan nama pemasaran.");
        put("product", "Produk");
        put("board", "Board");
        put("hardware", "Hardware");
        put("bootloader", "Bootloader");
        put("android", "Android");
        put("sdk", "API level", "Tingkat API Android; menentukan fitur sistem yang tersedia.");
        put("codename", "Codename");
        put("incremental", "Build incremental");
        put("security_patch", "Patch keamanan", "Tanggal patch keamanan terakhir dari vendor. Makin lama, makin berisiko.");
        put("build_type", "Tipe build");
        put("build_tags", "Tag build");
        put("fingerprint", "Fingerprint build", "Identitas lengkap build: merek/produk/perangkat:versi/build.");
        put("abi", "Arsitektur", "Arsitektur CPU utama yang dipakai app.");
        put("abis", "Arsitektur didukung");
        put("uptime_ms", "Uptime", "Waktu sejak perangkat dinyalakan, dalam milidetik.");
        put("boot_time", "Waktu boot");

        // ---------------------------------------------------------- system
        put("kernel", "Kernel", "Versi kernel Linux yang menjalankan Android.");
        put("os_name", "OS");
        put("os_version", "Versi OS");
        put("os_arch", "Arsitektur OS");
        put("java_vm", "Runtime", "Mesin runtime Java (ART) di perangkat.");
        put("java_vendor", "Vendor runtime");
        put("locale", "Bahasa & region");
        put("timezone", "Zona waktu");
        put("processors", "Jumlah core tersedia");
        put("heap_max_mb", "Batas heap app", "RAM maksimum yang boleh dipakai satu app sebelum sistem menghentikannya.");
        put("build_date", "Tanggal build");
        put("build_user", "User build");
        put("build_host", "Host build");
        put("build_flavor", "Flavor build");
        put("abilist", "Daftar ABI");
        put("abilist32", "ABI 32-bit");
        put("abilist64", "ABI 64-bit");
        put("board_platform", "Platform board");
        put("kernel_cmdline", "Kernel cmdline", "Parameter boot yang diberikan ke kernel.");
        put("selinux", "SELinux");
        put("verified_boot", "Verified boot", "Status integritas boot. 'green' berarti bootloader utuh dan tidak dimodifikasi.");
        put("verity", "dm-verity", "Pemeriksaan integritas partisi sistem. 'enforcing' berarti aktif.");
        put("encryption", "Enkripsi penyimpanan");

        // ------------------------------------------------------------- cpu
        put("cores", "Jumlah core");
        put("hardware_cpuinfo", "Nama SoC", "Chip utama yang dibaca dari /proc/cpuinfo.");
        put("model_name", "Model CPU");
        put("processor_implementer", "Implementer", "Kode vendor desain core (0x41 = ARM).");
        put("processor_architecture", "Arsitektur CPU", "Angka arsitektur ARM dari /proc/cpuinfo: 8 berarti ARMv8 (64-bit).");
        put("processor_variant", "Varian core");
        put("processor_part", "Part core", "Kode tipe core ARM, mis. 0xd03 = Cortex-A53.");
        put("processor_revision", "Revisi core");
        put("core_online", "Core aktif", "Jumlah core yang sedang dinyalakan; core bisa dimatikan untuk hemat daya.");
        put("governor", "Governor CPU", "Strategi pengaturan frekuensi CPU.");
        put("frequencies", "Frekuensi per core", "Kecepatan saat ini dibanding maksimum tiap core.");
        put("core", "Core");
        put("current_mhz", "Saat ini");
        put("max_mhz", "Maksimum");
        put("min_mhz", "Minimum");

        // ------------------------------------------------------------- gpu
        put("kgsl_model", "Model GPU (KGSL)");
        put("kgsl_busy", "GPU busy");
        put("mali_type", "Model GPU (Mali)");
        put("gl_vendor", "Vendor GPU");
        put("gl_renderer", "GPU", "Nama chip grafis yang dilaporkan driver OpenGL.");
        put("gl_version", "Versi OpenGL ES");
        put("gl_shading_language", "Versi GLSL");
        put("gl_max_texture_size", "Tekstur maksimum", "Ukuran tekstur terbesar yang bisa diproses GPU, dalam piksel.");
        put("gles", "Info GLES");

        // ---------------------------------------------------------- memory
        put("total_kb", "Total RAM");
        put("available_kb", "Tersedia", "RAM yang bisa langsung dipakai tanpa membuang cache.");
        put("threshold_kb", "Ambang batas", "Batas di mana sistem mulai menutup app di latar belakang.");
        put("low_memory", "Memori rendah", "True bila sistem sedang kekurangan memori.");
        put("used_pct", "Terpakai");
        put("heap_class_mb", "Heap normal", "Batas memori per app pada konfigurasi standar.");
        put("large_heap_class_mb", "Heap besar", "Batas memori per app bila manifest meminta heap besar.");
        put("meminfo_free_kb", "Memori bebas", "RAM yang benar-benar belum dipakai; cache masih bisa diklaim sistem.");
        put("meminfo_cached_kb", "Cache", "File cache dan memori kernel yang bisa diklaim ulang saat dibutuhkan.");
        put("meminfo_buffers_kb", "Buffer", "Memori sementara untuk operasi I/O.");
        put("swap_total_kb", "Total swap");
        put("swap_used_kb", "Swap terpakai");
        put("swap_used_pct", "Swap (%)");

        // Five-way memory split
        put("seg_total_kb", "Total RAM");
        put("seg_apps_kb", "Aplikasi", "Memori yang dipakai app dan proses berjalan.");
        put("seg_apps_pct", "Aplikasi (%)");
        put("seg_cache_kb", "Cache", "Cache file dan memori kernel; bisa diklaim ulang saat dibutuhkan.");
        put("seg_cache_pct", "Cache (%)");
        put("seg_system_kb", "Sistem", "Struktur kernel: slab, page table, dan sejenisnya.");
        put("seg_system_pct", "Sistem (%)");
        put("seg_free_kb", "Bebas", "RAM yang benar-benar belum dipakai.");
        put("seg_free_pct", "Bebas (%)");
        put("seg_other_kb", "Lainnya");
        put("meminfo_active_kb", "Memori aktif", "Halaman yang baru dipakai dan masih hangat di RAM.");
        put("meminfo_inactive_kb", "Memori tidak aktif", "Halaman yang jarang dipakai; kandidat pertama untuk diklaim.");
        put("meminfo_shmem_kb", "Memori bersama", "Halaman yang dipakai lebih dari satu proses.");
        put("meminfo_slab_kb", "Slab", "Memori struktur data kernel.");
        put("meminfo_sreclaimable_kb", "Slab dapat direklaim", "Bagian slab yang bisa dibebaskan bila perlu.");
        put("meminfo_sunreclaim_kb", "Slab tidak dapat direklaim", "Bagian slab yang tidak bisa dibebaskan.");
        put("meminfo_zram_kb", "ZRAM", "Swap terkompresi di RAM, bukan di penyimpanan.");
        put("meminfo_compressed_kb", "Terkompresi", "Jumlah data yang sudah dipadatkan ZRAM.");

        // --------------------------------------------------------- storage
        put("internal_total_kb", "Total penyimpanan");
        put("internal_free_kb", "Sisa");
        put("internal_free_pct", "Sisa (%)");
        put("internal_used_kb", "Terpakai");
        put("internal_used_pct", "Terpakai (%)");
        put("segment_apps_kb", "Aplikasi & data", "Ukuran APK plus data semua app terpasang.");
        put("segment_apps_pct", "Aplikasi & data (%)");
        put("segment_system_kb", "Sistem & dicadangkan", "Sisa ruang yang dipakai sistem dan partisi cadangan.");
        put("segment_system_pct", "Sistem (%)");
        put("segment_free_kb", "Tersedia");
        put("segment_free_pct", "Tersedia (%)");
        put("media_photo_kb", "Foto");
        put("media_photo_count", "Jumlah foto");
        put("media_video_kb", "Video");
        put("media_video_count", "Jumlah video");
        put("media_audio_kb", "Audio");
        put("media_audio_count", "Jumlah audio");
        put("volumes", "Volume", "Semua penyimpanan yang terpasang, termasuk kartu SD.");
        put("external_app_total_kb", "Penyimpanan eksternal app");
        put("external_app_free_kb", "Sisa eksternal");
        put("external_app_path", "Path eksternal app");
        put("cache_dir", "Folder cache");
        put("cache_used_kb", "Cache terpakai");
        put("description", "Nama");
        put("removable", "Bisa dilepas");
        put("emulated", "Emulasi");
        put("primary", "Utama");
        put("state", "Status");
        put("path", "Path");

        // --------------------------------------------------------- battery
        put("level", "Baterai");
        put("status", "Status", "'charging' berarti sedang mengisi daya.");
        put("health", "Kesehatan", "Perkiraan kondisi baterai dari pengendali daya.");
        put("temperature_c", "Suhu", "Suhu baterai. Di atas 45 °C mempercepat penurunan kapasitas.");
        put("voltage_mv", "Tegangan");
        put("technology", "Teknologi");
        put("ac_powered", "Adaptor AC");
        put("usb_powered", "USB");
        put("wireless_powered", "Nirkabel");
        put("charging", "Mengisi daya");
        put("cycle_count", "Siklus cas", "Berapa kali baterai diisi penuh. Banyak kernel tidak melaporkannya.");
        put("charge_full_uah", "Kapasitas sekarang", "Muatan penuh yang terukur saat ini, dalam µAh.");
        put("charge_full_design_uah", "Kapasitas desain", "Kapasitas pabrikan. Selisihnya menunjukkan keausan.");
        put("charge_counter_uah", "Muatan tersisa");
        put("current_ua", "Arus", "Positif saat mengisi, negatif saat memakai daya.");
        put("wear_pct", "Keausan", "Kapasitas sekarang dibanding desain. 85% berarti sudah aus 15%.");
        put("power_supplies", "Power supply");
        put("capacity", "Kapasitas");
        put("type", "Tipe");
        put("cycle_count ", "Siklus");

        // --------------------------------------------------------- display
        put("width_px", "Lebar");
        put("height_px", "Tinggi");
        put("density_dpi", "Kerapatan", "Jumlah piksel per inci. Menentukan seberapa tajam teks terlihat.");
        put("density", "Skala density");
        put("scaled_density", "Skala font");
        put("xdpi", "DPI horizontal");
        put("ydpi", "DPI vertikal");
        put("refresh_rate_hz", "Refresh rate", "Berapa kali layar diperbarui per detik.");
        put("name", "Nama layar");
        put("rotation", "Rotasi");
        put("mode_width", "Lebar mode");
        put("mode_height", "Tinggi mode");
        put("mode_refresh_hz", "Refresh mode");
        put("hdr_types", "Dukungan HDR", "Jumlah format HDR yang didukung panel. 0 berarti tidak ada.");
        put("modes", "Mode layar", "Resolusi dan refresh rate yang didukung panel.");

        // ---------------------------------------------------------- camera
        put("id", "ID kamera");
        put("facing", "Arah", "Depan, belakang, atau kamera eksternal.");
        put("flash", "Flash");
        put("sensor_width", "Lebar sensor");
        put("sensor_height", "Tinggi sensor");
        put("apertures", "Bukaan", "Angka f lebih kecil berarti lebih banyak cahaya masuk.");
        put("focal_lengths_mm", "Jarak fokus");
        put("hardware_level", "Level hardware", "Dukungan Camera2 penuh mensyaratkan 'full' atau lebih.");
        put("raw_capable", "Dukungan RAW");
        put("manual_sensor", "Kontrol manual");
        put("logical_multi", "Multi-kamera logis");
        put("jpeg_sizes", "Jumlah ukuran JPEG");
        put("max_jpeg", "Resolusi foto maksimum");
        put("cameras", "Kamera");
        put("count", "Jumlah");

        // ---------------------------------------------------------- sensor
        put("sensors", "Sensor", "Semua sensor yang dilaporkan Android, termasuk yang tidak aktif.");
        put("vendor", "Vendor");
        put("type_id", "ID tipe");
        put("version", "Versi");
        put("max_range", "Jangkauan maksimum");
        put("resolution", "Resolusi");
        put("power_ma", "Daya");
        put("min_delay_us", "Interval minimum");
        put("wakeup", "Wakeup sensor", "True bila sensor bisa membangunkan perangkat dari tidur.");
        put("string_type", "Tipe");

        // --------------------------------------------------------- network
        put("connected", "Terhubung");
        put("wifi", "Wi-Fi");
        put("cellular", "Seluler");
        put("ethernet", "Ethernet");
        put("vpn", "VPN");
        put("bluetooth", "Bluetooth");
        put("downstream_kbps", "Kecepatan unduh", "Perkiraan bandwidth dari sistem, bukan hasil uji kecepatan.");
        put("upstream_kbps", "Kecepatan unggah");
        put("interface", "Antarmuka");
        put("dns", "DNS");
        put("ipv4", "Alamat IP");
        put("routes", "Jumlah rute");
        put("wifi_enabled", "Wi-Fi aktif");
        put("wifi_link_speed_mbps", "Kecepatan link");
        put("wifi_rssi", "Kekuatan sinyal", "Dalam dBm; -35 kuat, -80 lemah.");
        put("wifi_frequency_mhz", "Frekuensi");
        put("wifi_ip", "IP Wi-Fi");
        put("wifi_bssid", "BSSID");
        put("wifi_ssid", "Nama Wi-Fi (SSID)", "Butuh izin lokasi aktif sejak Android 10.");
        put("network_type", "Tipe jaringan");
        put("network_operator", "Operator");
        put("data_enabled", "Data aktif");
        put("data_state", "Status data");
        put("traffic_mobile_rx_kb", "Unduh seluler");
        put("traffic_mobile_tx_kb", "Unggah seluler");
        put("traffic_mobile_total_kb", "Total data seluler");
        put("traffic_wifi_rx_kb", "Unduh Wi-Fi");
        put("traffic_wifi_tx_kb", "Unggah Wi-Fi");
        put("traffic_wifi_total_kb", "Total non-seluler");
        put("traffic_total_kb", "Total lalu lintas", "Dihitung sejak perangkat dinyalakan, bukan per bulan.");

        // ------------------------------------------------------------- sim
        put("sim_state", "Status SIM");
        put("phone_type", "Tipe telepon");
        put("sim_operator_name", "Operator SIM");
        put("sim_operator_code", "Kode operator");
        put("sim_country", "Negara SIM");
        put("network_country", "Negara jaringan");
        put("roaming", "Roaming");
        put("slot_count", "Jumlah slot SIM");
        put("imei", "IMEI", "Diblokir untuk app biasa sejak Android 10.");
        put("meid", "MEID", "Diblokir untuk app biasa sejak Android 10.");
        put("imsi", "IMSI", "Diblokir untuk app biasa sejak Android 10.");
        put("iccid", "ICCID", "Diblokir untuk app biasa sejak Android 10.");
        put("slots", "Slot SIM");
        put("slot", "Slot");
        put("subscription_id", "ID langganan");
        put("carrier", "Operator");
        put("country", "Negara");
        put("display_name", "Nama tampilan");
        put("mcc", "MCC");
        put("mnc", "MNC");

        // ------------------------------------------------------------ apps
        put("third_party_count", "App pihak ketiga");
        put("system_count", "App sistem");
        put("total_count", "Total app");
        put("installed", "App terpasang", "Hanya app yang dipasang pengguna, bukan bawaan sistem.");
        put("package", "Paket");
        put("version_code", "Kode versi");
        put("target_sdk", "Target SDK", "Versi Android yang jadi acuan perilaku app.");
        put("min_sdk", "Min SDK", "Android terendah yang bisa menjalankan app.");
        put("installer", "Sumber install");
        put("sideloaded", "Di luar Play Store");
        put("system", "App sistem");
        put("enabled", "Aktif");
        put("debuggable", "Debuggable");
        put("native_libs", "ABI native");
        put("data_dir", "Folder data");
        put("apk_size_kb", "Ukuran APK");

        // ----------------------------------------------------------- codec
        put("codec_count", "Jumlah codec");
        put("hardware_codec_count", "Codec hardware", "Codec yang dijalankan chip khusus, jauh lebih hemat daya.");
        put("codecs", "Codec");
        put("mime", "Tipe MIME");
        put("encoder", "Encoder");
        put("software_only", "Hanya software");
        put("vendor_only", "Khusus vendor");

        // -------------------------------------------------------------- id
        put("android_id", "Android ID", "ID per instalasi app, bukan ID hardware. Berubah setelah factory reset.");
        put("build_id", "Build ID");
        put("display_id", "Display ID");
        put("radio", "Versi radio");
        put("serial", "Serial perangkat", "Bernilai 'unknown' untuk app biasa sejak Android 10.");
        put("advertising_id", "Advertising ID", "Butuh Google Play Services; tidak tersedia di app ini.");

        // ------------------------------------------------------------ hubs
        put("available", "Jumlah", "Berapa banyak alat di tab ini.");
        put("layar", "Tes layar");
        put("tombol", "Tes tombol");
        put("getar", "Tes getar");
        put("gps", "GPS & satelit");
        put("analyze_storage", "Analisis penyimpanan");
        put("export", "Export ikon & APK");
        put("monitor", "Monitor mengambang");
        put("share", "Bagikan laporan");
        put("overlay_permission", "Izin overlay");

        // ------------------------------------------------ devinfo parity
        put("capabilities", "Kemampuan perangkat");
        put("nfc", "NFC");
        put("nfc_hce", "Emulasi kartu NFC");
        put("face", "Pengenalan wajah");
        put("iris", "Pengenalan iris");
        put("infrared", "Pemancar inframerah");
        put("uwb", "UWB");
        put("usb_host", "Host USB");
        put("telephony", "Telefoni");
        put("esim", "eSIM");
        put("flashlight", "Senter");

        put("system_as_root", "System-as-Root");
        put("dynamic_partitions", "Partisi dinamis");
        put("seamless_updates", "Pembaruan tanpa gangguan");
        put("baseband", "Baseband");
        put("sdk_ext", "Versi Ekstensi SDK");

        put("features", "Fitur sistem", "Kemampuan yang dinyatakan Android dan vendor. Deklarasi tidak menjamin fitur benar-benar tersedia.");
        put("value", "Nilai");

        put("clusters", "Klaster", "Kelompok inti yang berbagi rentang frekuensi.");
        put("index", "Klaster");
        put("core_list", "Daftar inti");
        put("frequency_range_text", "Rentang frekuensi");
        put("core_part_code", "Kode part");
        put("core_name", "Struktur core", "Nama core ARM dari kode part di /proc/cpuinfo.");

        put("power_levels", "Level performa");
        put("power_level", "Level aktif");

        put("driver", "Driver Vulkan");
        put("instance_version", "API instance");
        put("api_version", "API perangkat");
        put("supported", "Didukung");
        put("extras", "Tambahan");

        put("containers", "Format kontainer");
        put("extension", "Ekstensi");
        put("media_type", "Jenis media");
        put("mime_types", "Jenis MIME");
        put("track_count", "Jumlah track");
        put("source", "Sumber");
        put("verified_count", "Terverifikasi");
        put("muxers", "Muxer");

        put("media_badge", "Media penyimpanan", "Perkiraan eMMC/UFS dari node boot; tidak ada API publik untuk ini.");
        put("design_capacity_mah", "Kapasitas desain");
        put("estimated_full_charge_mah", "Perkiraan kapasitas penuh");
        put("max_charging_current_ma", "Input pengisian maksimum");
        put("max_charging_voltage_mv", "Tegangan pengisian maksimum");
        put("charge_now_mah", "Muatan sekarang");
        put("health_pct", "Kesehatan kapasitas");

        put("size_inches", "Ukuran fisik", "Diagonal layar dalam inci, dihitung dari resolusi dan DPI.");
        put("density_bucket", "Kelas densitas");
        put("screen_off_timeout_ms", "Waktu tunggu layar");

        put("megapixels", "Megapiksel");
        put("sensor_size_inch", "Ukuran sensor");
        put("antibanding_modes", "Mode Antibanding");
        put("aberration_modes", "Mode Aberasi");
        put("autofocus_modes", "Mode Fokus Otomatis");
        put("exposure_modes", "Mode Eksposur Otomatis");
        put("compensation_range", "Rentang kompensasi");
        put("compensation_step", "Langkah kompensasi");
        put("fps_ranges", "Rentang target FPS");

        put("is_64bit", "64-bit");
        put("apk_path", "Jalur APK");
        put("wifi_standard", "Standar Wi-Fi");
        put("wifi_security", "Keamanan Wi-Fi");
    }

    private FieldMeta() {
    }

    public static Meta of(String key) {
        Meta m = META.get(key);
        if (m != null) {
            return m;
        }
        // Fall back to a capitalised, underscored-free version of the key so an
        // unmapped field is still readable rather than raw.
        String spaced = key.replace('_', ' ').trim();
        String label = spaced.isEmpty() ? key
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        return new Meta(label, null);
    }

    public static Map<String, Meta> all() {
        return Collections.unmodifiableMap(META);
    }
}
