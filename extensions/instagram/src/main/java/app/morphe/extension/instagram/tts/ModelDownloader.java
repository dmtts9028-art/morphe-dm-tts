package app.morphe.extension.instagram.tts;

import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;

/**
 * Downloads the Phonon checkpoint (and optionally catalog voices) into the model folder.
 *
 * Every file is verified against SHA256SUMS.txt shipped next to the release. The whole
 * downloader is gated behind {@link TtsPrefs#licenseAccepted()}: nothing is fetched before the
 * user accepted the CC BY-NC 4.0 terms in the settings dialog.
 */
final class ModelDownloader {
    static final String[] MODEL_FILE_NAMES = {
            "config.json", "tokenizer.json", "model.q8.gguf", "default-voice.safetensors",
    };
    private static final String SUMS_FILE = "SHA256SUMS.txt";

    interface Progress {
        /** Called on the download thread. {@code done}/{@code total} in bytes, {@code total} -1 when unknown. */
        void onProgress(String fileName, long done, long total);
    }

    private static final AtomicBoolean running = new AtomicBoolean();

    private ModelDownloader() { }

    static boolean isRunning() {
        return running.get();
    }

    /**
     * Downloads every model file, verifying each checksum. On mismatch the partial file is
     * deleted and the exception names the file.
     */
    static void downloadModel(Progress progress) throws IOException, TtsUnavailableException {
        if (!TtsPrefs.licenseAccepted()) throw new TtsUnavailableException("license not accepted");
        if (!running.compareAndSet(false, true)) throw new TtsUnavailableException("already running");
        try {
            File dir = EngineFactory.modelDir();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File voices = new File(dir, "voices");
            //noinspection ResultOfMethodCallIgnored
            voices.mkdirs();
            File tempDir = new File(dir, ".download");
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();

            File sumsFile = new File(tempDir, SUMS_FILE);
            fetch(TtsPrefs.modelUrl() + SUMS_FILE, sumsFile, null, null);
            Map<String, String> sums = parseSums(sumsFile);

            IOException failure = null;
            for (String name : MODEL_FILE_NAMES) {
                String expected = sums.get(name);
                if (expected == null) {
                    throw new IOException(SUMS_FILE + " is missing an entry for " + name);
                }
                File target = new File(dir, name);
                if (target.isFile() && sha256(target).equalsIgnoreCase(expected)) {
                    Logger.printDebug(() -> "model file already valid: " + name);
                    continue;
                }
                try {
                    fetch(TtsPrefs.modelUrl() + name, target, expected, progress == null
                            ? null : (file, done, total) -> progress.onProgress(name, done, total));
                } catch (IOException ex) {
                    failure = ex;
                    break;
                }
            }
            sumsFile.delete();
            //noinspection ResultOfMethodCallIgnored
            tempDir.delete();
            if (failure != null) {
                throw new TtsUnavailableException("download failed: " + failure.getMessage(), failure);
            }
        } finally {
            running.set(false);
        }
    }

    /** Downloads one public voice file from gradium/phonon-voices into voices/. */
    static void downloadVoice(String fileName, Progress progress) throws IOException, TtsUnavailableException {
        if (!TtsPrefs.licenseAccepted()) throw new TtsUnavailableException("license not accepted");
        File voices = new File(EngineFactory.modelDir(), "voices");
        //noinspection ResultOfMethodCallIgnored
        voices.mkdirs();
        File target = new File(voices, fileName);
        String url = "https://huggingface.co/gradium/phonon-voices/resolve/main/" + Uri.encode(fileName);
        fetch(url, target, null, progress);
    }

    private static void fetch(String url, File target, String expectedSha256, Progress progress)
            throws IOException {
        boolean hf = url.startsWith("https://huggingface.co/");
        String token = hf ? TtsPrefs.hfToken() : "";
        File part = new File(target.getParentFile(), target.getName() + ".part");

        long attemptFrom = part.isFile() ? part.length() : 0;
        // A previous successful verify means nothing if the target grew/changed: re-verify target
        // only when the caller supplied an expected hash and the file already exists complete.
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL(url);
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                connection.setRequestProperty("User-Agent", "morphe-dm-tts");
                if (!token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (attemptFrom > 0) {
                    connection.setRequestProperty("Range", "bytes=" + attemptFrom + "-");
                }
                int code = connection.getResponseCode();
                boolean append = attemptFrom > 0 && code == 206;
                if (code != 200 && code != 206) {
                    throw new IOException(url + " -> HTTP " + code);
                }
                if (code == 206 && attemptFrom != part.length()) {
                    throw new IOException("unexpected resume offset");
                }
                if (code == 200 && attemptFrom > 0) {
                    attemptFrom = 0; // server ignored the range
                }
                long total = connection.getContentLengthLong();
                long done = append ? attemptFrom : 0;
                total = total < 0 ? -1 : total + attemptFrom;
                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(part, append)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (progress != null) progress.onProgress(target.getName(), done, total);
                    }
                }
                if (expectedSha256 != null && !sha256(part).equalsIgnoreCase(expectedSha256)) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    attemptFrom = 0;
                    if (attempt == 2) throw new IOException("checksum mismatch: " + target.getName());
                    continue;
                }
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!part.renameTo(target)) throw new IOException("cannot move " + target.getName());
                return;
            } catch (IOException ex) {
                Logger.printDebug(() -> "fetch attempt failed: " + ex.getMessage());
                if (attempt == 2) throw ex;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }
    private static Map<String, String> parseSums(File sumsFile) throws IOException {
        Map<String, String> sums = new HashMap<>();
        byte[] raw = java.nio.file.Files.readAllBytes(sumsFile.toPath());
        for (String line : new String(raw, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 66) continue;
            String hash = trimmed.substring(0, 64);
            String name = trimmed.substring(66).trim();
            while (name.startsWith("*") || name.startsWith("./")) {
                name = name.startsWith("*") ? name.substring(1) : name.substring(2);
            }
            sums.put(name, hash);
        }
        return sums;
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }
}
