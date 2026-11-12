package app.morphe.extension.instagram.tts;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Synthesis cache: filesDir/dmtts_cache/&lt;key&gt;.wav plus a .json sidecar
 * {durationMs, peaks[40], sampleRate}. Key = sha256(engine|voice|lang|text|modelMtime).
 */
final class TtsCache {
    static final int PEAK_COUNT = 40;
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_BYTES = 100L * 1024 * 1024;

    static class Entry {
        final File wav;
        final long durationMs;
        final float[] peaks;
        final int sampleRate;

        Entry(File wav, long durationMs, float[] peaks, int sampleRate) {
            this.wav = wav;
            this.durationMs = durationMs;
            this.peaks = peaks;
            this.sampleRate = sampleRate;
        }
    }

    private TtsCache() { }

    static File dir() {
        File dir = new File(Utils.getContext().getFilesDir(), "dmtts_cache");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    static String key(String engine, String voice, String lang, String text) {
        long modelMtime = EngineFactory.modelComplete() ? EngineFactory.modelDir().lastModified() : 0;
        String raw = engine + "|" + voice + "|" + lang + "|" + text + "|" + modelMtime;
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    static Entry get(String key) {
        File wav = new File(dir(), key + ".wav");
        File json = new File(dir(), key + ".json");
        if (!wav.isFile() || wav.length() == 0 || !json.isFile()) return null;
        try {
            String body = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
            long durationMs = readLong(body, "durationMs");
            int sampleRate = (int) readLong(body, "sampleRate");
            float[] peaks = readPeaks(body);
            if (peaks == null || peaks.length != PEAK_COUNT) peaks = silentPeaks();
            if (sampleRate <= 0) sampleRate = WavIo.SAMPLE_RATE;
            return new Entry(wav, durationMs, peaks, sampleRate);
        } catch (IOException | NumberFormatException ex) {
            Logger.printDebug(() -> "cache sidecar unreadable: " + ex.getMessage());
            return null;
        }
    }

    static void put(String key, File wav, float[] pcm, int sampleRate) {
        File json = new File(dir(), key + ".json");
        try {
            float[] peaks = peaks(pcm);
            long durationMs = pcm.length * 1000L / Math.max(1, sampleRate);
            StringBuilder body = new StringBuilder("{\"durationMs\":").append(durationMs)
                    .append(",\"sampleRate\":").append(sampleRate)
                    .append(",\"peaks\":[");
            for (int i = 0; i < peaks.length; i++) {
                if (i > 0) body.append(',');
                body.append(String.format(Locale.US, "%.4f", peaks[i]));
            }
            body.append("]}");
            Files.write(json.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Logger.printDebug(() -> "cache sidecar write failed: " + ex.getMessage());
        }
        trim();
    }

    static void clear() {
        File[] files = dir().listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** RMS per equal window of the PCM, scaled to [0,1] with a floor so silence stays visible. */
    static float[] peaks(float[] pcm) {
        float[] peaks = new float[PEAK_COUNT];
        if (pcm.length == 0) return silentPeaks();
        int window = Math.max(1, pcm.length / PEAK_COUNT);
        float max = 0.001f;
        for (int i = 0; i < PEAK_COUNT; i++) {
            int start = i * window;
            int end = Math.min(pcm.length, start + window);
            double sum = 0;
            int count = 0;
            for (int j = start; j < end; j++) {
                sum += (double) pcm[j] * pcm[j];
                count++;
            }
            float rms = count == 0 ? 0 : (float) Math.sqrt(sum / count);
            peaks[i] = rms;
            if (rms > max) max = rms;
        }
        for (int i = 0; i < PEAK_COUNT; i++) {
            peaks[i] = Math.min(1f, peaks[i] / max);
        }
        return peaks;
    }

    static float[] silentPeaks() {
        float[] peaks = new float[PEAK_COUNT];
        Arrays.fill(peaks, 0.05f);
        return peaks;
    }

    /** LRU by mtime under both an entry cap and a byte cap. */
    private static void trim() {
        File[] wavs = dir().listFiles((file, name) -> name.endsWith(".wav"));
        if (wavs == null || wavs.length <= MAX_ENTRIES) return;
        Arrays.sort(wavs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int excess = wavs.length - MAX_ENTRIES;
        long total = 0;
        for (File wav : wavs) total += wav.length();
        for (int i = 0; i < wavs.length; i++) {
            if (excess <= 0 && total <= MAX_BYTES) break;
            File wav = wavs[i];
            total -= wav.length();
            //noinspection ResultOfMethodCallIgnored
            wav.delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dir(), wav.getName().replace(".wav", ".json")).delete();
            excess--;
        }
    }

    private static long readLong(String json, String field) {
        String value = readField(json, field);
        return value == null ? 0 : Long.parseLong(value.trim());
    }

    private static float[] readPeaks(String json) {
        int start = json.indexOf("\"peaks\"");
        if (start < 0) return null;
        int open = json.indexOf('[', start);
        int close = json.indexOf(']', open);
        if (open < 0 || close < 0) return null;
        String[] parts = json.substring(open + 1, close).split(",");
        float[] peaks = new float[parts.length];
        for (int i = 0; i < parts.length; i++) peaks[i] = Float.parseFloat(parts[i].trim());
        return peaks;
    }

    private static String readField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        int from = start + marker.length();
        int end = from;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return json.substring(from, end);
    }
}
