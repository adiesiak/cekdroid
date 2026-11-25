package ai.cekdroid.collect;

import android.content.Context;

/**
 * One tab's worth of device data.
 *
 * Implementations must be free of side effects beyond reading, must never
 * throw (the Collector guards, but a section that handles its own failures can
 * record a precise warning instead of a stack trace), and must store null for
 * anything the platform refuses to expose.
 */
public interface Section {

    /** Stable id, shared with the CLI section name and the JSON key. */
    String id();

    /** Short title shown on the tab and card header. */
    String title();

    /** Read the device and write everything into {@code out}. */
    void collect(Context ctx, Recorder out);
}
