package app.morphe.extension.instagram.tts;

import android.os.Build;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;

/**
 * JNA port of upstream android/PhononTTS.kt from xn-ptts (MIT/Apache-2.0), against ptts.h.
 *
 * A loaded model is expensive, so exactly one handle is cached here and reused. Every call blocks;
 * call from a background thread, one at a time (guarded by {@link #LOCK}).
 */
final class PhononNative {
    static final int UNIT_CPU = 0;

    /** Serialize all native work: the FFI contract allows one in-flight call per handle. */
    static final Object LOCK = new Object();

    private static Lib lib;

    private static Pointer handle;
    private static String loadedDir;
    private static String loadedLang;

    // The frame callback must outlive every native call it was handed to (upstream invariant),
    // hence a static field rather than a lambda per call.
    private static final FrameFn ON_FRAME = new FrameFn();

    /** Set before each speak; read by the native thread inside the callback. */
    private static volatile FrameSink currentSink;
    private static volatile boolean stopRequested;

    private PhononNative() { }

    interface FrameSink {
        /** @return false to stop synthesis early. */
        boolean onFrame(float[] pcm);
    }

    static void requestStop() {
        stopRequested = true;
    }

    /** Safe before first load: only flips the flag once the library exists. */
    static void requestStopQuietly() {
        try {
            stopRequested = true;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Loads the checkpoint in {@code modelDir} (or reuses the cached handle) and speaks
     * {@code text}, streaming 24 kHz mono float frames into {@code sink}.
     *
     * @return the utterance timings
     * @throws TtsUnavailableException when the library or the model cannot be loaded
     */
    static PhononResult speak(String modelDir, String lang, String voice, String text,
                              FrameSink sink) throws TtsUnavailableException {
        synchronized (LOCK) {
            load(modelDir, lang);
            if (voice != null && !voice.isEmpty() && !"default".equals(voice)) {
                check(lib.ptts_set_voice(handle, voice));
            }
            currentSink = sink;
            stopRequested = false;
            try {
                PhononResult out = new PhononResult();
                boolean ok = lib.ptts_speak(handle, text, ON_FRAME, null, out);
                out.read();
                if (!ok && !stopRequested) {
                    throw new TtsUnavailableException("ptts_speak: " + lastError());
                }
                return out;
            } finally {
                currentSink = null;
            }
        }
    }

    /** Voice names the loaded checkpoint exposes, including "default" when present. */
    static List<String> voices(String modelDir, String lang) throws TtsUnavailableException {
        synchronized (LOCK) {
            load(modelDir, lang);
            return names(lib.ptts_voices(handle));
        }
    }

    static String lastError() {
        if (lib == null) return "libptts_ffi not loaded";
        String error = lib.ptts_last_error(handle);
        return error == null ? "unknown error" : error;
    }

    private static void load(String modelDir, String lang) throws TtsUnavailableException {
        Lib library = library();
        if (handle != null && modelDir.equals(loadedDir)) {
            if (!lang.equals(loadedLang)) {
                check(library.ptts_set_language(handle, lang));
                loadedLang = lang;
            }
            return;
        }
        if (handle != null) {
            Logger.printDebug(() -> "Phonon: reloading model for " + modelDir);
            library.ptts_free(handle);
            handle = null;
        }
        // Upstream recommendation: bound the CPU thread pool on phones.
        try {
            android.system.Os.setenv("RAYON_NUM_THREADS", "2", true);
        } catch (Throwable ignored) {
        }
        Logger.printDebug(() -> "Phonon: loading " + modelDir + " lang=" + lang);
        Pointer loaded = library.ptts_new(modelDir, UNIT_CPU, lang);
        if (loaded == null) {
            throw new TtsUnavailableException("ptts_new: " + lastError());
        }
        handle = loaded;
        loadedDir = modelDir;
        loadedLang = lang;
    }

    static synchronized Lib library() throws TtsUnavailableException {
        if (lib == null) {
            try {
                lib = Native.load("ptts_ffi", Lib.class);
            } catch (Throwable ex) {
                Logger.printException(() -> "libptts_ffi load failed", ex);
                throw new TtsUnavailableException("libptts_ffi: " + ex);
            }
        }
        return lib;
    }

    private static void check(boolean ok) throws TtsUnavailableException {
        if (!ok) throw new TtsUnavailableException(lastError());
    }

    /** Reads the NUL-separated, double-NUL terminated name block ptts_voices/ptts_languages return. */
    private static List<String> names(Pointer block) {
        List<String> names = new ArrayList<>();
        long offset = 0;
        while (true) {
            String name = block.getString(offset, "UTF-8");
            if (name.isEmpty()) return names;
            names.add(name);
            offset += name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
        }
    }

    /** ptts.h PttsFrameFn; returns C bool (1 byte) — JNA maps Java boolean callback results to one byte. */
    private static class FrameFn implements Callback {
        @SuppressWarnings("unused")
        public boolean invoke(Pointer pcm, long n, Pointer user) {
            FrameSink sink = currentSink;
            if (sink == null || stopRequested) return false;
            try {
                return sink.onFrame(pcm.getFloatArray(0, (int) n));
            } catch (Throwable ex) {
                Logger.printException(() -> "phonon frame sink failed", ex);
                return false;
            }
        }
    }

    /** ptts.h PttsResult. */
    @Structure.FieldOrder({"frames", "samples", "ttfaMs", "totalMs"})
    static class PhononResult extends Structure {
        public int frames;
        public long samples;
        public double ttfaMs;
        public double totalMs;
    }

    /** ptts.h. JNA maps C bool returns/params to Java boolean for callbacks and prototypes alike. */
    private interface Lib extends Library {
        Pointer ptts_new(String dir, int unit, String lang);

        boolean ptts_speak(Pointer h, String text, FrameFn cb, Pointer user, PhononResult out);

        Pointer ptts_voices(Pointer h);

        boolean ptts_set_voice(Pointer h, String name);

        boolean ptts_set_language(Pointer h, String lang);

        Pointer ptts_languages(Pointer h);

        boolean ptts_set_temperature(Pointer h, float temperature);

        void ptts_set_seed(Pointer h, long seed);

        String ptts_last_error(Pointer h);

        void ptts_free(Pointer h);
    }
}
