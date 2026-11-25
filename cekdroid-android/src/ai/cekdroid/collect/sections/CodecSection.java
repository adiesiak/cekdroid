package ai.cekdroid.collect.sections;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/** Hardware and software media codecs, grouped by MIME type. */
public class CodecSection implements Section {

    @Override
    public String id() {
        return "codec";
    }

    @Override
    public String title() {
        return "Codec";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        MediaCodecInfo[] infos;
        try {
            infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        } catch (Throwable t) {
            out.warn("MediaCodecList gagal: " + t.getMessage());
            return;
        }

        out.put("codec_count", infos.length);
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        int hardware = 0;
        for (MediaCodecInfo info : infos) {
            boolean hw = isHardwareAccelerated(info);
            if (hw) {
                hardware++;
            }
            for (String type : info.getSupportedTypes()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("name", info.getName());
                row.put("mime", type);
                row.put("encoder", info.isEncoder());
                row.put("hardware", hw);
                row.put("software_only", info.isSoftwareOnly());
                row.put("vendor_only", info.isVendor());
                rows.add(row);
            }
        }
        out.put("hardware_codec_count", hardware);
        out.rows("codecs", rows);
    }

    private static boolean isHardwareAccelerated(MediaCodecInfo info) {
        try {
            return info.isHardwareAccelerated();
        } catch (Throwable t) {
            return false;
        }
    }
}
