package app.morphe.extension.instagram.tts;

import java.io.File;

/** Synthesizes one text to a mono 24 kHz float PCM WAV file. Not thread safe; callers serialize. */
interface TtsEngine {
    /**
     * Writes a WAV to {@code outWav} (parent dir must exist).
     *
     * @param voice Phonon voice name; null or "default" uses the checkpoint voice. Ignored by
     *              the system engine.
     * @param key   cache key of this synthesis; {@link #stop()} may only interrupt this key.
     */
    void synthesize(String text, String voice, String key, File outWav) throws TtsUnavailableException;

    /** True when {@code key} is what this engine is currently synthesizing. */
    boolean isCurrent(String key);

    /** Cancels the in-flight or queued synthesis, if any. */
    void stop();
}
