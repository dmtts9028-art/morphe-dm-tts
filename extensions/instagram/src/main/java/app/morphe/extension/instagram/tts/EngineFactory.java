package app.morphe.extension.instagram.tts;

import java.io.File;

import app.morphe.extension.shared.Utils;

/** Engine choice: Phonon only when its checkpoint is fully downloaded, system TTS otherwise. */
final class EngineFactory {
    private static volatile TtsEngine engine;
    private static volatile String engineKey;

    private EngineFactory() { }

    static File modelDir() {
        return new File(Utils.getContext().getFilesDir(), "phonon/models/phonon-40m-multilingual");
    }

    static File modelFile(String name) {
        return new File(modelDir(), name);
    }

    static boolean modelComplete() {
        File dir = modelDir();
        return new File(dir, ModelDownloader.MODEL_FILE_NAMES[0]).isFile()
                && new File(dir, ModelDownloader.MODEL_FILE_NAMES[1]).isFile()
                && new File(dir, ModelDownloader.MODEL_FILE_NAMES[2]).isFile()
                && new File(dir, ModelDownloader.MODEL_FILE_NAMES[3]).isFile();
    }

    static synchronized TtsEngine get() throws TtsUnavailableException {
        String key = TtsPrefs.engine();
        if (engine != null && key.equals(engineKey)) return engine;
        close();
        engine = TtsPrefs.ENGINE_PHONON.equals(key) ? new PhononEngine() : new SystemEngine();
        engineKey = key;
        return engine;
    }

    static synchronized void close() {
        if (engine != null) {
            engine.stop();
            if (engine instanceof SystemEngine) ((SystemEngine) engine).shutdown();
            engine = null;
            engineKey = null;
        }
    }
}
