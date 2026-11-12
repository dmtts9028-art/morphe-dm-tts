package app.morphe.extension.instagram.tts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;

/** Streams 24 kHz float frames from libptts_ffi into a WAV file. */
final class PhononEngine implements TtsEngine {
    private final String modelDir;
    private volatile boolean stopped;
    private volatile String currentKey;

    PhononEngine() throws TtsUnavailableException {
        if (!EngineFactory.modelComplete()) throw new TtsUnavailableException(TtsStr.MODEL_MISSING);
        modelDir = EngineFactory.modelDir().getAbsolutePath();
    }

    @Override
    public boolean isCurrent(String key) {
        return key != null && key.equals(currentKey);
    }

    @Override
    public void synthesize(String text, String voice, String key, File outWav) throws TtsUnavailableException {
        stopped = false;
        currentKey = key;
        List<float[]> frames = new ArrayList<>();
        long[] total = new long[1];
        try {
            PhononNative.speak(modelDir, TtsPrefs.lang(), voice, text, pcm -> {
                if (stopped) return false;
                frames.add(pcm);
                total[0] += pcm.length;
                return true;
            });
        } catch (TtsUnavailableException ex) {
            frames.clear();
            throw ex;
        } finally {
            currentKey = null;
        }
        if (frames.isEmpty()) {
            if (!stopped) throw new TtsUnavailableException(PhononNative.lastError());
            return;
        }
        float[] pcm = new float[(int) total[0]];
        int offset = 0;
        for (float[] frame : frames) {
            System.arraycopy(frame, 0, pcm, offset, frame.length);
            offset += frame.length;
        }
        frames.clear();
        try {
            WavIo.writeFloatWav(outWav, pcm);
        } catch (IOException ex) {
            throw new TtsUnavailableException("wav write", ex);
        }
    }

    @Override
    public void stop() {
        stopped = true;
        PhononNative.requestStop();
    }

    /** Voice names of the installed checkpoint (native listing, minus the built-in default). */
    static List<String> installedVoices() {
        List<String> names = new ArrayList<>();
        try {
            for (String name : PhononNative.voices(EngineFactory.modelDir().getAbsolutePath(),
                    TtsPrefs.lang())) {
                if (!"default".equals(name)) names.add(name);
            }
        } catch (TtsUnavailableException ex) {
            Logger.printDebug(() -> "ptts_voices unavailable: " + ex.getMessage());
        }
        return names;
    }
}
