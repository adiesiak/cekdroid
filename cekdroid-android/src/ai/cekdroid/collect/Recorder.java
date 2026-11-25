package ai.cekdroid.collect;

/**
 * What a {@link Section} writes into. A section only ever talks to this
 * interface, so it can be collected into a Snapshot, a JSON string, or a test
 * double without knowing the difference.
 */
public interface Recorder {

    /** Store one scalar value. null is meaningful: it means "not readable". */
    void put(String key, Object value);

    /** Store a repeating block, e.g. one row per installed app. */
    void rows(String key, java.util.List<java.util.LinkedHashMap<String, Object>> value);

    /** Record why something could not be read. */
    void warn(String message);

    /** True when the caller wants the expensive/permissioned fields. */
    default boolean deep() {
        return true;
    }
}
