package app.morphe.extension.instagram.tts;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** One-shot self-test of the synthesis chain, used to validate a new device/model install. */
final class TtsDebug {
    private static final AtomicBoolean ran = new AtomicBoolean();

    private TtsDebug() { }

    /** Synthesizes a fixed sentence with the current engine into filesDir/dmtts_debug/. */
    static void run() {
        if (!ran.compareAndSet(false, true)) return;
        Utils.runOnBackgroundThread(() -> {
            try {
                File dir = new File(Utils.getContext().getFilesDir(), "dmtts_debug");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File out = new File(dir, "debug-" + System.currentTimeMillis() + ".wav");
                TtsEngine engine = EngineFactory.get();
                engine.synthesize("Bonjour, ceci est un test de synthèse vocale.", null, "debug", out);
                long size = out.length();
                android.util.Log.i("DmTts", "TtsDebug: wrote " + out.getName() + " (" + size + " bytes)");
            } catch (Throwable ex) {
                android.util.Log.e("DmTts", "TtsDebug: synthesis failed", ex);
            }
        });
    }
}
