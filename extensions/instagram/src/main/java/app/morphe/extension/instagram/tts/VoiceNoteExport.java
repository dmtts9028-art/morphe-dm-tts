package app.morphe.extension.instagram.tts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Collects voice-note audio addresses while a conversation is scrolled, so they can be exported
 * as cloning references. Nothing is downloaded until the user triggers an export.
 */
final class VoiceNoteExport {
    private static final Map<String, Set<String>> URLS_BY_THREAD = new ConcurrentHashMap<>();

    private VoiceNoteExport() { }

    /** Called from the voice-message binder hook with (view holder, message model). */
    public static void onVoiceBind(Object holder, Object model) {
        try {
            String threadId = MessageTtsView.threadKeyOf(model);
            if (threadId == null) return;
            String url = findAudioUrl(model, 0);
            if (url != null) {
                URLS_BY_THREAD.computeIfAbsent(threadId, id -> ConcurrentHashMap.newKeySet())
                        .add(url);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "VoiceNoteExport.onVoiceBind failed", ex);
        }
    }

    /** Downloads every collected voice note of the thread into filesDir/phonon/refs/&lt;thread&gt;/. */
    public static void export(String threadId) {
        Set<String> urls = URLS_BY_THREAD.get(threadId);
        if (urls == null || urls.isEmpty()) {
            Utils.showToastShort(TtsStr.EXPORT_NONE);
            return;
        }
        Utils.showToastShort(TtsStr.EXPORTING);
        Utils.runOnBackgroundThread(() -> {
            File dir = VoiceManager.refsDir(threadId);
            AtomicInteger index = new AtomicInteger();
            int exported = 0;
            for (String url : urls) {
                File out = new File(dir, String.format("voice-%03d.m4a", index.incrementAndGet()));
                try {
                    download(url, out);
                    exported++;
                } catch (IOException ex) {
                    Logger.printDebug(() -> "voice note download failed: " + ex.getMessage());
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                }
            }
            final int count = exported;
            Utils.runOnMainThread(() -> Utils.showToastShort(
                    count > 0 ? TtsStr.EXPORT_DONE + " (" + count + ")" : TtsStr.PLAYBACK_FAILED));
        });
    }

    static int collectedCount(String threadId) {
        Set<String> urls = URLS_BY_THREAD.get(threadId);
        return urls == null ? 0 : urls.size();
    }

    /** Breadth-first scan for an https media string on the model object graph. */
    private static String findAudioUrl(Object root, int depth) {
        if (root == null || depth > 4) return null;
        try {
            for (java.lang.reflect.Field field : fieldsOf(root.getClass())) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType() != String.class) continue;
                field.setAccessible(true);
                Object value = field.get(root);
                if (value instanceof String) {
                    String candidate = (String) value;
                    if (isMediaUrl(candidate)) return candidate;
                }
            }
            for (java.lang.reflect.Field field : fieldsOf(root.getClass())) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                String type = field.getType().getName();
                if (!type.startsWith("com.instagram") && !type.startsWith("instagram.")
                        && !type.startsWith("X")) continue;
                field.setAccessible(true);
                String found = findAudioUrl(field.get(root), depth + 1);
                if (found != null) return found;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isMediaUrl(String value) {
        if (!value.startsWith("https://")) return false;
        String lower = value.toLowerCase(java.util.Locale.US);
        return (lower.contains("ebcdn") || lower.contains("cdninstagram") || lower.contains("fbcdn"))
                && (lower.contains("mp4") || lower.contains("m4a") || lower.contains("audio")
                        || lower.contains("media") || lower.contains("ephemeral"));
    }

    private static List<java.lang.reflect.Field> fieldsOf(Class<?> type) {
        List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            java.util.Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields;
    }

    private static void download(String url, File out) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = connection.getInputStream();
                 FileOutputStream fileOut = new FileOutputStream(out)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) fileOut.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }
}
