package ai.cekdroid.collect.sections;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * GPU vendor/limits via a throwaway EGL context.
 *
 * glGetString needs a current context, so one is created offscreen and torn
 * down immediately. That is why this does not use GLSurfaceView and needs no
 * visible surface. Anything unavailable becomes null.
 */
public class GpuSection implements Section {

    @Override
    public String id() {
        return "gpu";
    }

    @Override
    public String title() {
        return "GPU";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        out.put("kgsl_model", SectionBase.s(
                SectionBase.readFile("/sys/class/kgsl/kgsl-3d0/gpu_model")));
        out.put("kgsl_busy", SectionBase.s(
                SectionBase.readFile("/sys/class/kgsl/kgsl-3d0/gpubusy")));
        out.put("mali_type", SectionBase.readFile(
                "/sys/class/misc/mali0/device/gpuinfo"));

        // Adreno/Mali expose the number of power levels and which one is
        // active; both are plain sysfs reads. A GPU without the node leaves
        // the row null rather than showing a made-up level.
        out.put("power_levels", powerLevels());
        out.put("power_level", SectionBase.s(
                SectionBase.readFile("/sys/class/kgsl/kgsl-3d0/default_pwrlevel")));
        out.put("current_mhz", gpuClockMhz());

        try {
            readGl(out);
        } catch (Throwable t) {
            out.warn("EGL tidak tersedia: " + t.getMessage());
        }
    }

    private static Long powerLevels() {
        String raw = SectionBase.readFirstLine(
                new java.io.File("/sys/class/kgsl/kgsl-3d0/num_pwrlevels"));
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** GPU clock in MHz from the KGSL gpuclk node, or null when absent. */
    private static Long gpuClockMhz() {
        String[] candidates = {
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/misc/mali0/device/clock",
        };
        for (String path : candidates) {
            String raw = SectionBase.readFirstLine(new java.io.File(path));
            if (raw == null) {
                continue;
            }
            try {
                long hz = Long.parseLong(raw.trim());
                if (hz > 1_000_000L) {
                    return hz / 1_000_000L;
                }
                if (hz > 1_000L) {
                    return hz / 1_000L;
                }
                return hz;
            } catch (NumberFormatException ignored) {
                // try the next node
            }
        }
        return null;
    }

    private void readGl(Recorder out) {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            out.warn("EGL display tidak ada");
            return;
        }
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) {
            out.warn("eglInitialize gagal");
            return;
        }

        int[] attribs = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] num = new int[1];
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
                || num[0] == 0) {
            out.warn("EGL config tidak ada");
            EGL14.eglTerminate(display);
            return;
        }

        int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        EGLContext context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        if (context == EGL14.EGL_NO_CONTEXT) {
            out.warn("EGL context gagal dibuat");
            EGL14.eglTerminate(display);
            return;
        }

        int[] pbufferAttribs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0);
        if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) {
            out.warn("EGL surface gagal");
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
            return;
        }

        out.put("gl_vendor", SectionBase.s(GLES20.glGetString(GLES20.GL_VENDOR)));
        out.put("gl_renderer", SectionBase.s(GLES20.glGetString(GLES20.GL_RENDERER)));
        out.put("gl_version", SectionBase.s(GLES20.glGetString(GLES20.GL_VERSION)));
        out.put("gl_shading_language", SectionBase.s(GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)));
        out.put("gl_max_texture_size", maxTextureSize());

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display, surface);
        EGL14.eglDestroyContext(display, context);
        EGL14.eglTerminate(display);
    }

    private static Integer maxTextureSize() {
        int[] max = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0);
        return max[0] > 0 ? max[0] : null;
    }
}
