package ai.cekdroid.collect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The single data model behind every output: JSON, the on-screen cards, and the
 * plain-text digest. Sections are inserted in registry order, so the JSON key
 * order, the tab order, and the text order never drift apart.
 *
 * Values are limited to String, Long, Integer, Double, Boolean, null, and
 * List<Map<String,Object>> for repeating rows. Nothing here assumes a vendor or
 * an Android version; a value that cannot be read is stored as null together
 * with a warning, never guessed.
 */
public final class Snapshot {

    /**
     * Schema id carried in every report.
     *
     * The CLI writes the same value, so a reader can tell which side produced a
     * file without guessing from its shape.
     */
    public static final String SCHEMA = "cekdroid.report.v2";

    /** section id -> ordered key/value pairs. */
    private final LinkedHashMap<String, LinkedHashMap<String, Object>> sections =
            new LinkedHashMap<>();

    /** section id -> display title, kept beside the data for the UI. */
    private final LinkedHashMap<String, String> titles = new LinkedHashMap<>();

    private final List<String> warnings = new ArrayList<>();

    public LinkedHashMap<String, Object> section(String id) {
        return sections.get(id);
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> ids() {
        return new ArrayList<>(sections.keySet());
    }

    public String title(String id) {
        String t = titles.get(id);
        return t == null ? id : t;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** Called by the Collector right before a section runs. */
    public void open(String id, String title) {
        sections.put(id, new LinkedHashMap<String, Object>());
        titles.put(id, title);
    }

    /** Drop everything so the same Collector can run again. */
    public void collectReset() {
        sections.clear();
        titles.clear();
        warnings.clear();
    }

    public void put(String id, String key, Object value) {
        LinkedHashMap<String, Object> s = sections.get(id);
        if (s != null) {
            s.put(key, value);
        }
    }

    public void warn(String id, String message) {
        warnings.add(id + ": " + message);
    }

    /**
     * Rebuild a Snapshot from the cached report JSON.
     *
     * The hub's "Laporan untuk pembeli" action needs the last collected data
     * without paying for a fresh device read, so it parses the string that was
     * already saved to preferences. Only the report's own shape is understood:
     * the metadata keys (schema, source, generated_at) become sections too, and
     * a section whose value is not an object is skipped rather than guessed at.
     *
     * Throws on malformed JSON — the caller shows the message instead of
     * silently producing an empty report.
     */
    public static Snapshot fromJson(String json) throws org.json.JSONException {
        org.json.JSONObject root = new org.json.JSONObject(json);
        Snapshot snap = new Snapshot();
        java.util.Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("warnings".equals(key)) {
                continue;
            }
            Object value = root.get(key);
            if (!(value instanceof org.json.JSONObject)) {
                continue;
            }
            snap.open(key, key);
            org.json.JSONObject body = (org.json.JSONObject) value;
            java.util.Iterator<String> inner = body.keys();
            while (inner.hasNext()) {
                String field = inner.next();
                snap.put(key, field, convert(body.get(field)));
            }
        }
        org.json.JSONArray warnings = root.optJSONArray("warnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                String line = warnings.optString(i, null);
                if (line != null && !line.isEmpty()) {
                    snap.warnings.add(line);
                }
            }
        }
        return snap;
    }

    /** Convert a parsed JSON value into the types a Snapshot stores. */
    private static Object convert(Object value) throws org.json.JSONException {
        if (value == null || value == org.json.JSONObject.NULL) {
            return null;
        }
        if (value instanceof org.json.JSONObject) {
            org.json.JSONObject object = (org.json.JSONObject) value;
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, convert(object.get(key)));
            }
            return map;
        }
        if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(convert(array.get(i)));
            }
            return list;
        }
        return value;
    }
}
