package ai.cekdroid.collect;

import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.collect.sections.AppsSection;
import ai.cekdroid.collect.sections.BatterySection;
import ai.cekdroid.collect.sections.CameraSection;
import ai.cekdroid.collect.sections.CodecSection;
import ai.cekdroid.collect.sections.CpuSection;
import ai.cekdroid.collect.sections.DeviceSection;
import ai.cekdroid.collect.sections.DisplaySection;
import ai.cekdroid.collect.sections.GpuSection;
import ai.cekdroid.collect.sections.IdSection;
import ai.cekdroid.collect.sections.MediaContainerSection;
import ai.cekdroid.collect.sections.MemorySection;
import ai.cekdroid.collect.sections.NetworkSection;
import ai.cekdroid.collect.sections.SensorSection;
import ai.cekdroid.collect.sections.SimSection;
import ai.cekdroid.collect.sections.StorageSection;
import ai.cekdroid.collect.sections.SystemFeaturesSection;
import ai.cekdroid.collect.sections.SystemSection;
import ai.cekdroid.collect.sections.TestsSection;
import ai.cekdroid.collect.sections.ToolsSection;
import ai.cekdroid.collect.sections.VulkanSection;

/**
 * The tab order, in one place.
 *
 * The CLI mirrors these ids as probe.sh section names, so a new entry here
 * should get a matching @@section if the same data is readable over ADB.
 */
public final class Sections {

    private Sections() {
    }

    public static List<Section> all() {
        List<Section> out = new ArrayList<>();
        out.add(new DeviceSection());
        out.add(new SystemSection());
        out.add(new SystemFeaturesSection());
        out.add(new CpuSection());
        out.add(new GpuSection());
        out.add(new VulkanSection());
        out.add(new MemorySection());
        out.add(new StorageSection());
        out.add(new BatterySection());
        out.add(new DisplaySection());
        out.add(new CameraSection());
        out.add(new SensorSection());
        out.add(new NetworkSection());
        out.add(new SimSection());
        out.add(new AppsSection());
        out.add(new CodecSection());
        out.add(new MediaContainerSection());
        out.add(new IdSection());
        out.add(new TestsSection());
        out.add(new ToolsSection());
        return out;
    }
}