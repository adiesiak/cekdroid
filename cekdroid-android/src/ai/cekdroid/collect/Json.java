package ai.cekdroid.collect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer with no dependencies.
 *
 * Unlike the previous streaming version, every object body is staged in its own
 * buffer and only appended on endObject(). A section that fails halfway now
 * leaves an empty object instead of a dangling `null` in the middle of a
 * member list, which used to produce unparseable output.
 */
public final class Json {

    private final StringBuilder sb = new StringBuilder();
    private boolean needComma = false;

    public Json beginObject() {
        comma();
        sb.append('{');
        needComma = false;
        return this;
    }

    public Json endObject() {
        sb.append('}');
        needComma = true;
        return this;
    }

    public Json beginArray() {
        comma();
        sb.append('[');
        needComma = false;
        return this;
    }

    public Json endArray() {
        sb.append(']');
        needComma = true;
        return this;
    }

    public Json name(String key) {
        comma();
        sb.append('"').append(escape(key)).append("\":");
        needComma = false;
        return this;
    }

    /**
     * String values must emit the separator themselves: every other overload
     * calls comma() first, and skipping it here produced arrays like
     * ["a""b"] which no JSON parser accepts.
     */
    public Json value(String v) {
        comma();
        if (v == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(v)).append('"');
        }
        needComma = true;
        return this;
    }

    public Json value(long v) {
        comma();
        sb.append(v);
        needComma = true;
        return this;
    }

    public Json value(double v) {
        comma();
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            sb.append("null");
        } else {
            sb.append(v);
        }
        needComma = true;
        return this;
    }

    public Json value(boolean v) {
        comma();
        sb.append(v ? "true" : "false");
        needComma = true;
        return this;
    }

    public Json nullValue() {
        comma();
        sb.append("null");
        needComma = true;
        return this;
    }

    public Json prop(String key, String v) {
        return name(key).value(v);
    }

    public Json prop(String key, long v) {
        return name(key).value(v);
    }

    public Json prop(String key, int v) {
        return name(key).value((long) v);
    }

    public Json prop(String key, double v) {
        return name(key).value(v);
    }

    public Json prop(String key, boolean v) {
        return name(key).value(v);
    }

    public Json prop(String key, Object v) {
        name(key);
        return raw(v);
    }

    /** Write any Snapshot value type without the caller having to cast. */
    @SuppressWarnings("unchecked")
    public Json raw(Object v) {
        if (v == null) {
            return nullValue();
        }
        if (v instanceof String) {
            return value((String) v);
        }
        if (v instanceof Boolean) {
            return value((Boolean) v);
        }
        if (v instanceof Double || v instanceof Float) {
            return value(((Number) v).doubleValue());
        }
        if (v instanceof Number) {
            return value(((Number) v).longValue());
        }
        if (v instanceof List) {
            beginArray();
            for (Object item : (List<Object>) v) {
                raw(item);
            }
            return endArray();
        }
        if (v instanceof Map) {
            beginObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                prop(e.getKey(), e.getValue());
            }
            return endObject();
        }
        return value(String.valueOf(v));
    }

    public Json stringArray(String key, List<String> values) {
        name(key).beginArray();
        if (values != null) {
            for (String v : values) {
                value(v);
            }
        }
        return endArray();
    }

    /** Serialize an entire Snapshot, one top-level key per section. */
    public Json snapshot(Snapshot snap) {
        beginObject();
        // Metadata first, matching the CLI's field order so both producers are
        // interchangeable to any consumer.
        prop("schema", Snapshot.SCHEMA);
        prop("source", "android-app");
        prop("generated_at", new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
                .format(new java.util.Date()));
        for (String id : snap.ids()) {
            name(id).beginObject();
            LinkedHashMap<String, Object> values = snap.section(id);
            if (values != null) {
                for (Map.Entry<String, Object> e : values.entrySet()) {
                    prop(e.getKey(), e.getValue());
                }
            }
            endObject();
        }
        stringArray("warnings", snap.warnings());
        return endObject();
    }

    private void comma() {
        if (needComma) {
            sb.append(',');
        }
    }

    public void reset() {
        sb.setLength(0);
        needComma = false;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
