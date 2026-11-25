package ai.cekdroid.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * Small readers and formatters shared by every section.
 *
 * Everything here degrades to null/empty rather than throwing: on a locked-down
 * ROM a missing sysfs node is normal, not an error.
 */
public final class SectionBase implements Recorder {

    private final Snapshot snapshot;
    private final String id;
    private final boolean deep;

    public SectionBase(Snapshot snapshot, String id, boolean deep) {
        this.snapshot = snapshot;
        this.id = id;
        this.deep = deep;
    }

    public static SectionBase of(Snapshot snapshot, Section section, boolean deep) {
        return new SectionBase(snapshot, section.id(), deep);
    }

    @Override
    public void put(String key, Object value) {
        snapshot.put(id, key, value);
    }

    @Override
    public void rows(String key, java.util.List<LinkedHashMap<String, Object>> value) {
        snapshot.put(id, key, value);
    }

    @Override
    public void warn(String message) {
        snapshot.warn(id, message);
    }

    @Override
    public boolean deep() {
        return deep;
    }

    /** Trim, and turn the empty string into null so JSON says "unknown". */
    public static String s(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static String readFirstLine(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            return line == null ? null : line.trim();
        } catch (IOException e) {
            return null;
        }
    }

    /** Read a whole procfs/sysfs file, or null when it is not readable. */
    public static String readFile(File f) {
        if (!f.isFile() || !f.canRead()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString().trim();
    }

    public static String readFile(String path) {
        return readFile(new File(path));
    }

    /** KB as "12.3 GB", matching the CLI's _gb() formatting. */
    public static String gb(long kb) {
        return String.format(java.util.Locale.US, "%.1f GB", kb / 1024.0 / 1024.0);
    }
}
