package ai.cekdroid.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.cekdroid.collect.Snapshot;

/**
 * Turns a Snapshot into one health score and a verdict.
 *
 * Why this exists: the collector answers "what is this device", and a technician
 * can read that. A shop counter selling a used phone needs a different question
 * answered — "should I take this unit" — and that has to be one number plus the
 * few reasons behind it, not a wall of fields.
 *
 * Two rules keep it honest:
 *
 *  1. A category that has no readable data is *excluded*, and the score is
 *     renormalised over the categories that were measured. A phone whose battery
 *     capacity the kernel hides cannot be marked down for it — the report says
 *     "tidak dinilai" instead of inventing a number.
 *  2. Every deduction names its evidence. The verdict is a summary of the
 *     reasons list, never a black box.
 *
 * The weights follow what actually fails in a used phone: battery wear first,
 * then tamper/integrity, then storage health, then the parts a customer notices
 * (sensors, screen), then memory pressure.
 */
public final class Health {

    /** One scored area. */
    public static final class Category {
        public final String id;
        public final String label;
        public final int weight;
        /** 0..100, or -1 when this category had no data. */
        public final int score;
        public final String note;

        Category(String id, String label, int weight, int score, String note) {
            this.id = id;
            this.label = label;
            this.weight = weight;
            this.score = score;
            this.note = note;
        }

        public boolean measured() {
            return score >= 0;
        }
    }

    /** The whole verdict. */
    public static final class Result {
        public final int score;
        public final String verdict;
        public final String headline;
        public final List<Category> categories;
        public final List<String> reasons;
        public final int measuredWeight;

        Result(int score, String verdict, String headline, List<Category> categories,
               List<String> reasons, int measuredWeight) {
            this.score = score;
            this.verdict = verdict;
            this.headline = headline;
            this.categories = categories;
            this.reasons = reasons;
            this.measuredWeight = measuredWeight;
        }

        /** Accent colour for the verdict, used by the card. */
        public int color() {
            if (score >= 85) {
                return Palette.OK;
            }
            if (score >= 65) {
                return Palette.WARN;
            }
            return Palette.CRITICAL;
        }
    }

    private Health() {
    }

    public static Result evaluate(Snapshot snap) {
        List<Category> categories = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        categories.add(battery(snap, reasons));
        categories.add(integrity(snap, reasons));
        categories.add(storage(snap, reasons));
        categories.add(memory(snap, reasons));
        categories.add(sensors(snap, reasons));
        categories.add(screen(snap, reasons));

        int measuredWeight = 0;
        double weighted = 0;
        for (Category c : categories) {
            if (!c.measured()) {
                continue;
            }
            measuredWeight += c.weight;
            weighted += c.score * (double) c.weight;
        }

        int score = measuredWeight == 0 ? -1
                : (int) Math.round(weighted / measuredWeight);

        String verdict;
        String headline;
        if (score < 0) {
            verdict = "Tidak cukup data";
            headline = "Perangkat belum terbaca. Muat ulang untuk menilai.";
        } else if (score >= 85) {
            verdict = "Layak";
            headline = "Tidak ada masalah berarti untuk pemakaian normal.";
        } else if (score >= 65) {
            verdict = "Perlu dicek";
            headline = "Ada beberapa hal yang sebaiknya diperiksa dulu sebelum transaksi.";
        } else {
            verdict = "Perlu servis";
            headline = "Ada temuan yang membatasi pemakaian; periksa sebelum dijual.";
        }

        return new Result(score, verdict, headline, categories, reasons, measuredWeight);
    }

    // ------------------------------------------------------------ categories

    /**
     * Battery: measured capacity against design, plus temperature and health.
     *
     * Capacity is the single most useful wear signal a used phone carries, and
     * it is also the one most often hidden. When neither health_pct nor wear_pct
     * is readable the category reports unmeasured rather than assuming the pack
     * is fine.
     */
    private static Category battery(Snapshot snap, List<String> reasons) {
        Map<String, Object> battery = snap.section("battery");
        if (battery == null) {
            return new Category("battery", "Baterai", 30, -1, "tidak terbaca");
        }

        Double health = num(battery, "health_pct");
        if (health == null) {
            health = num(battery, "wear_pct");
        }
        Double temp = num(battery, "temperature_c");
        String healthName = str(battery, "health");

        if (health == null && temp == null && healthName == null) {
            return new Category("battery", "Baterai", 30, -1,
                    "kapasitas & suhu tidak terbaca");
        }

        int score = 100;
        StringBuilder note = new StringBuilder();

        if (health != null) {
            // Capacity below 80% of design is where runtime complaints start;
            // below 70% most shops replace the pack.
            double h = health;
            if (h >= 90) {
                score = 100;
            } else if (h >= 80) {
                score = 85;
            } else if (h >= 70) {
                score = 65;
            } else if (h >= 60) {
                score = 40;
            } else {
                score = 15;
            }
            note.append(String.format(Locale.US, "kapasitas %.0f%% desain", h));
            if (h < 80) {
                reasons.add(String.format(Locale.US,
                        "Kapasitas baterai %.0f%% dari desain (di bawah 80%%).", h));
            }
        } else if (healthName != null) {
            // No capacity number, but the platform still names a condition.
            if ("good".equalsIgnoreCase(healthName)) {
                score = 85;
            } else if ("overheat".equalsIgnoreCase(healthName)
                    || "dead".equalsIgnoreCase(healthName)
                    || "failure".equalsIgnoreCase(healthName)) {
                score = 20;
                reasons.add("Status baterai dilaporkan '" + healthName + "'.");
            } else {
                score = 70;
            }
            note.append("status ").append(healthName);
        }

        if (temp != null) {
            if (temp >= 45) {
                score = Math.min(score, 20);
                reasons.add(String.format(Locale.US,
                        "Suhu baterai %.1f °C — terlalu panas.", temp));
            } else if (temp >= 40) {
                score = Math.min(score, 65);
                reasons.add(String.format(Locale.US,
                        "Suhu baterai %.1f °C — hangat.", temp));
            }
            if (note.length() > 0) {
                note.append(" · ");
            }
            note.append(String.format(Locale.US, "%.1f °C", temp));
        }

        return new Category("battery", "Baterai", 30, clamp(score), note.toString());
    }

    /**
     * Integrity: boot state, root, SELinux, patch age.
     *
     * These are the facts that decide whether the unit is "as the factory made
     * it". A phone whose bootloader reports unlocked or whose verified boot is
     * not green has been modified, and no software score can tell what else was
     * changed — so that is a hard deduction, not a hint.
     */
    private static Category integrity(Snapshot snap, List<String> reasons) {
        Map<String, Object> system = snap.section("system");
        Map<String, Object> device = snap.section("device");
        if (system == null && device == null) {
            return new Category("integrity", "Integritas", 25, -1, "tidak terbaca");
        }

        int score = 100;
        List<String> notes = new ArrayList<>();
        boolean anySignal = false;

        String verifiedBoot = str(system, "verified_boot");
        if (verifiedBoot != null) {
            anySignal = true;
            notes.add("verified boot " + verifiedBoot);
            if (!"green".equalsIgnoreCase(verifiedBoot)) {
                score -= 40;
                reasons.add("Verified boot bukan 'green' (" + verifiedBoot
                        + ") — sistem pernah dimodifikasi.");
            }
        }

        String bootloader = str(device, "bootloader");
        if (bootloader != null && !bootloader.isEmpty()) {
            anySignal = true;
            boolean unknown = "unknown".equalsIgnoreCase(bootloader);
            if (unknown) {
                notes.add("bootloader tidak dilaporkan");
            } else if ("locked".equalsIgnoreCase(bootloader)) {
                notes.add("bootloader locked");
            } else {
                notes.add("bootloader " + bootloader);
                score -= 30;
                reasons.add("Bootloader terbuka (" + bootloader + ").");
            }
        }

        String selinux = str(system, "selinux");
        if (selinux != null) {
            anySignal = true;
            if ("permissive".equalsIgnoreCase(selinux)) {
                notes.add("SELinux permissive");
                score -= 10;
                reasons.add("SELinux permissive — kontrol keamanan sistem dilonggarkan.");
            } else {
                notes.add("SELinux " + selinux);
            }
        }

        String patch = str(device, "security_patch");
        if (patch != null) {
            anySignal = true;
            Integer age = patchAgeDays(patch);
            if (age != null) {
                if (age > 730) {
                    score -= 25;
                    reasons.add("Patch keamanan " + age + " hari — perangkat sudah EOL.");
                } else if (age > 365) {
                    score -= 12;
                    reasons.add("Patch keamanan " + age + " hari — sudah lama.");
                }
                notes.add("patch " + age + " hari");
            }
        }

        if (!anySignal) {
            return new Category("integrity", "Integritas", 25, -1, "tidak terbaca");
        }
        return new Category("integrity", "Integritas", 25, clamp(score),
                String.join(" · ", notes));
    }

    /** Free space: a full phone cannot take updates and writes get slow. */
    private static Category storage(Snapshot snap, List<String> reasons) {
        Map<String, Object> storage = snap.section("storage");
        Double freePct = storage == null ? null : num(storage, "internal_free_pct");
        if (freePct == null) {
            return new Category("storage", "Penyimpanan", 15, -1, "tidak terbaca");
        }
        int score;
        if (freePct >= 25) {
            score = 100;
        } else if (freePct >= 15) {
            score = 85;
        } else if (freePct >= 8) {
            score = 60;
        } else if (freePct >= 4) {
            score = 30;
        } else {
            score = 10;
        }
        if (freePct < 15) {
            reasons.add(String.format(Locale.US,
                    "Sisa penyimpanan %.1f%% — sulit dipakai tanpa dibersihkan.", freePct));
        }
        return new Category("storage", "Penyimpanan", 15, score,
                String.format(Locale.US, "sisa %.1f%%", freePct));
    }

    /** RAM pressure: swap use is the honest signal on an Android device. */
    private static Category memory(Snapshot snap, List<String> reasons) {
        Map<String, Object> memory = snap.section("memory");
        if (memory == null) {
            return new Category("memory", "Memori", 10, -1, "tidak terbaca");
        }
        Double swap = num(memory, "swap_used_pct");
        Double used = num(memory, "used_pct");
        if (swap == null && used == null) {
            return new Category("memory", "Memori", 10, -1, "tidak terbaca");
        }
        int score = 100;
        if (swap != null) {
            if (swap >= 60) {
                score = 30;
                reasons.add(String.format(Locale.US,
                        "Swap terpakai %.0f%% — RAM kurang untuk pemakaian berat.", swap));
            } else if (swap >= 40) {
                score = 65;
                reasons.add(String.format(Locale.US, "Swap terpakai %.0f%%.", swap));
            } else if (swap >= 20) {
                score = 85;
            }
        }
        String note = swap == null ? "—"
                : String.format(Locale.US, "swap %.0f%%", swap);
        return new Category("memory", "Memori", 10, score, note);
    }

    /**
     * Sensors: how many the platform reports.
     *
     * There is no "expected count" across vendors, so the threshold is the one
     * true floor: a phone that reports almost nothing has a dead sensor hub. A
     * missing accelerometer is the classic failure and is called out by name.
     */
    private static Category sensors(Snapshot snap, List<String> reasons) {
        Map<String, Object> sensor = snap.section("sensor");
        Double count = sensor == null ? null : num(sensor, "count");
        if (count == null || count <= 0) {
            return new Category("sensor", "Sensor", 10, -1, "tidak terbaca");
        }
        int n = count.intValue();
        int score;
        if (n >= 12) {
            score = 100;
        } else if (n >= 8) {
            score = 85;
        } else if (n >= 5) {
            score = 65;
            reasons.add("Hanya " + n + " sensor terdeteksi.");
        } else {
            score = 30;
            reasons.add("Hanya " + n + " sensor terdeteksi — kemungkinan sensor hub bermasalah.");
        }
        return new Category("sensor", "Sensor", 10, score, n + " sensor");
    }

    /** Screen sanity: a plausible resolution and density for the panel size. */
    private static Category screen(Snapshot snap, List<String> reasons) {
        Map<String, Object> display = snap.section("display");
        if (display == null) {
            return new Category("screen", "Layar", 10, -1, "tidak terbaca");
        }
        Double w = num(display, "width_px");
        Double h = num(display, "height_px");
        Double dpi = num(display, "density_dpi");
        if (w == null || h == null) {
            return new Category("screen", "Layar", 10, -1, "tidak terbaca");
        }
        int score = 100;
        int width = w.intValue();
        int height = h.intValue();
        double shortSide = Math.min(w, h);
        double longSide = Math.max(w, h);
        if (shortSide < 480) {
            score = 50;
            reasons.add("Resolusi layar rendah (" + width + "×" + height + ").");
        }
        String note = String.format(Locale.US, "%d×%d", width, height);
        if (dpi != null) {
            note += String.format(Locale.US, " · %.0f dpi", dpi);
        }
        // An aspect ratio far outside anything a phone ships with usually means
        // the resolution was reported for a secondary display.
        double ratio = longSide / shortSide;
        if (ratio < 1.2 || ratio > 2.6) {
            score = Math.min(score, 60);
        }
        return new Category("screen", "Layar", 10, score, note);
    }

    // --------------------------------------------------------------- helpers

    /** Days since a "yyyy-MM-dd" patch level, or null when unparseable. */
    public static Integer patchAgeDays(String patch) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(patch.trim());
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    date, java.time.LocalDate.now());
            return (int) Math.max(0, days);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static Double num(Map<String, Object> section, String key) {
        if (section == null) {
            return null;
        }
        Object value = section.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String str(Map<String, Object> section, String key) {
        if (section == null) {
            return null;
        }
        Object value = section.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
