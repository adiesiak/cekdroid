package ai.cekdroid.collect.sections;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/**
 * Media containers and muxers the platform recognises.
 *
 * DevInfo lists a container table (extension, MIME types, track count) because
 * "can this phone play an MKV" is a real question before buying a device.
 *
 * Two sources are combined, in order of honesty:
 *
 *  1. MediaExtractor, given a real (empty) file with the extension. A platform
 *     whose extractor is registered for a format reports its MIME types and
 *     how many tracks it parsed, which is the strongest signal available to an
 *     app. An empty file usually fails sniffing, so this mostly covers the
 *     extractors that key off the extension.
 *  2. MimeTypeMap, which is the platform's own extension-to-MIME table. When
 *     the extractor could not answer, the MIME mapping still tells the reader
 *     whether Android has a declared type for the extension. That row is
 *     marked "declared" rather than "supported", so the report never claims
 *     playback it did not verify.
 */
public class MediaContainerSection implements Section {

    /**
     * Extensions to probe, with the media family they belong to.
     *
     * The list is the set a phone encounters in practice: camera output, chat
     * forwards, downloads, and music. Probe files are empty; they exist only to
     * give MediaExtractor a path with the right extension.
     */
    private static final String[][] PROBES = {
            {"video", "mp4"}, {"video", "mkv"}, {"video", "webm"},
            {"video", "ts"}, {"video", "3gp"}, {"video", "avi"},
            {"video", "m4v"}, {"video", "mov"},
            {"audio", "mp3"}, {"audio", "flac"}, {"audio", "ogg"},
            {"audio", "wav"}, {"audio", "m4a"}, {"audio", "aac"},
            {"audio", "opus"}, {"audio", "amr"},
            {"image", "jpg"}, {"image", "png"}, {"image", "webp"},
            {"image", "heic"}, {"image", "gif"},
    };

    @Override
    public String id() {
        return "container";
    }

    @Override
    public String title() {
        return "Format kontainer";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        File probeDir = new File(ctx.getCacheDir(), "container-probe");
        if (!probeDir.exists() && !probeDir.mkdirs()) {
            out.warn("tidak bisa menyiapkan folder probe");
        }

        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        int verified = 0;
        for (String[] probe : PROBES) {
            String kind = probe[0];
            String ext = probe[1];

            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("extension", ext);
            row.put("media_type", kind);

            String[] parsed = tryExtractor(probeDir, ext);
            if (parsed != null) {
                row.put("supported", true);
                row.put("source", "MediaExtractor");
                row.put("mime_types", parsed[0]);
                row.put("track_count", parsed[1].isEmpty()
                        ? null : Long.parseLong(parsed[1]));
                verified++;
            } else {
                // Extractor could not parse an empty file. That is expected for
                // container formats that sniff content, so the platform's own
                // MIME table is the next-best answer — and it is labelled as a
                // declaration, not a verified capability.
                String mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext);
                row.put("supported", false);
                row.put("source", mime == null ? null : "MimeTypeMap");
                row.put("mime_types", mime);
                row.put("track_count", null);
            }
            rows.add(row);
        }
        out.put("verified_count", verified);
        out.rows("containers", rows);

        // Muxer formats are an int enum with no public table, so the ones a
        // phone must support are listed by name. The constant existing is the
        // only fact available; it does not promise the encoder is present.
        LinkedHashMap<String, Object> muxers = new LinkedHashMap<>();
        muxers.put("mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxers.put("webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
        muxers.put("3gpp", MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
        muxers.put("ogg", MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
        out.put("muxers", muxers);

        if (verified == 0) {
            out.warn("tidak ada extractor yang mau mem-parse file kosong; "
                    + "kolom MIME diambil dari tabel MimeTypeMap, kolom track "
                    + "count tidak tersedia");
        }
    }

    /**
     * Try one extension through MediaExtractor.
     *
     * Returns {mimeTypes, trackCount} on success, null when the extractor
     * refused. The probe file is reused per extension and deleted afterwards so
     * a failed attempt cannot accumulate files in the cache.
     */
    private static String[] tryExtractor(File dir, String ext) {
        File probe = new File(dir, "probe." + ext);
        MediaExtractor extractor = new MediaExtractor();
        try {
            try (FileOutputStream fos = new FileOutputStream(probe)) {
                // An empty file: the extractor either recognises the extension
                // or rejects the content before reading anything.
                fos.write(new byte[0]);
            }
            extractor.setDataSource(probe.getAbsolutePath());
            int tracks = extractor.getTrackCount();
            List<String> mimes = new ArrayList<>();
            for (int i = 0; i < tracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && !mimes.contains(mime)) {
                    mimes.add(mime);
                }
            }
            return new String[]{String.join(", ", mimes), String.valueOf(tracks)};
        } catch (Throwable t) {
            return null;
        } finally {
            extractor.release();
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
        }
    }
}
