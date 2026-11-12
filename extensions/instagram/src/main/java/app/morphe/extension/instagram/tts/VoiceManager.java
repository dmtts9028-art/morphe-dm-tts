package app.morphe.extension.instagram.tts;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** Voices are *.safetensors files in the model dir's voices/ folder, plus the built-in default. */
final class VoiceManager {
    static final String DEFAULT_VOICE = "default";
    static final String VOICES_DIR = "voices";

    private VoiceManager() { }

    static File voicesDir() {
        return new File(EngineFactory.modelDir(), VOICES_DIR);
    }

    /** "default" first, then voice file names without extension, sorted. */
    static List<String> voices() {
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_VOICE);
        String[] files = voicesDir().list((dir, name) -> name.toLowerCase(Locale.US).endsWith(".safetensors"));
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                names.add(file.substring(0, file.length() - ".safetensors".length()));
            }
        }
        return names;
    }

    /** Voice for a message: per-conversation binding, else the global voice. */
    static String resolveVoice(String threadId) {
        if (threadId != null) {
            String bound = TtsPrefs.threadVoice(threadId);
            if (!bound.isEmpty()) return bound;
        }
        return TtsPrefs.voice();
    }

    /**
     * Imports a .safetensors voice picked via SAF (Downloads, files, ...) into voices/.
     *
     * @return the voice name
     */
    static String importVoice(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        if (name == null || !name.toLowerCase(Locale.US).endsWith(".safetensors")) {
            throw new IOException("not a .safetensors file");
        }
        //noinspection ResultOfMethodCallIgnored
        voicesDir().mkdirs();
        File target = new File(voicesDir(), name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("cannot open " + uri);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
        Logger.printDebug(() -> "imported voice " + name);
        return name.substring(0, name.length() - ".safetensors".length());
    }

    private static String displayName(Context context, Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Throwable ignored) {
        }
        if (name == null) {
            String last = uri.getLastPathSegment();
            name = last == null ? null : last.substring(last.lastIndexOf('/') + 1);
        }
        return name;
    }

    /** Directory voice-note audio is exported to for offline cloning. */
    static File refsDir(String threadId) {
        File dir = new File(Utils.getContext().getFilesDir(),
                "phonon/refs/" + sanitize(threadId));
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
