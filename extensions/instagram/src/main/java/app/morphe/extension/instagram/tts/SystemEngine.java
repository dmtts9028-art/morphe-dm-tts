package app.morphe.extension.instagram.tts;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** System TextToSpeech backend: synthesizeToFile into a WAV the player treats like any other. */
final class SystemEngine implements TtsEngine {
    private static final long TIMEOUT_SECONDS = 60;
    private static final String UTTERANCE_ID = "dmtts";

    private static TextToSpeech tts;
    private static final Object INIT_LOCK = new Object();

    private final java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String currentKey;

    @Override
    public boolean isCurrent(String key) {
        return key != null && key.equals(currentKey);
    }

    @Override
    public void synthesize(String text, String voice, String key, File outWav) throws TtsUnavailableException {
        currentKey = key;
        try {
            synthesizeTo(text, outWav);
        } finally {
            currentKey = null;
        }
    }

    private void synthesizeTo(String text, File outWav) throws TtsUnavailableException {
        TextToSpeech engine = engine();
        stopped.set(false);
        String lang = TtsPrefs.lang();
        int ready = engine.setLanguage(localeFor(lang));
        if (ready == TextToSpeech.LANG_MISSING_DATA || ready == TextToSpeech.LANG_NOT_SUPPORTED) {
            Logger.printDebug(() -> "system TTS language missing: " + lang + ", using default");
        }
        File tmp = new File(outWav.getParentFile(), outWav.getName() + ".raw");
        tmp.delete();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Integer> outcome = new AtomicReference<>(TextToSpeech.ERROR);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) { }

            @Override
            public void onDone(String utteranceId) {
                outcome.set(TextToSpeech.SUCCESS);
                done.countDown();
            }

            @Override
            public void onError(String utteranceId) {
                outcome.set(TextToSpeech.ERROR);
                done.countDown();
            }
        });
        int queued = engine.synthesizeToFile(text, null, tmp, UTTERANCE_ID);
        if (queued != TextToSpeech.SUCCESS) {
            throw new TtsUnavailableException("synthesizeToFile queued=" + queued);
        }
        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                engine.stop();
                throw new TtsUnavailableException("system TTS timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TtsUnavailableException("interrupted", ex);
        }
        if (stopped.get()) {
            tmp.delete();
            return;
        }
        if (outcome.get() != TextToSpeech.SUCCESS) {
            tmp.delete();
            throw new TtsUnavailableException("system TTS QUEUE_ERROR");
        }
        if (!tmp.renameTo(outWav)) {
            tmp.delete();
            throw new TtsUnavailableException("cannot move result wav");
        }
    }

    @Override
    public void stop() {
        stopped.set(true);
        synchronized (INIT_LOCK) {
            if (tts != null) tts.stop();
        }
    }

    void shutdown() {
        synchronized (INIT_LOCK) {
            if (tts != null) {
                try {
                    tts.stop();
                    tts.shutdown();
                } catch (Throwable ignored) {
                }
                tts = null;
            }
        }
    }

    private static TextToSpeech engine() throws TtsUnavailableException {
        synchronized (INIT_LOCK) {
            if (tts != null) return tts;
            CountDownLatch init = new CountDownLatch(1);
            AtomicReference<Integer> status = new AtomicReference<>(TextToSpeech.ERROR);
            try {
                tts = new TextToSpeech(Utils.getContext(), status1 -> {
                    status.set(status1);
                    init.countDown();
                });
            } catch (Throwable ex) {
                tts = null;
                throw new TtsUnavailableException("TextToSpeech init", ex);
            }
            try {
                if (!init.await(15, TimeUnit.SECONDS)) {
                    throw new TtsUnavailableException("TextToSpeech init timeout");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TtsUnavailableException("interrupted", ex);
            }
            if (status.get() != TextToSpeech.SUCCESS || tts == null) {
                shutdownStatic();
                throw new TtsUnavailableException("no system TTS engine");
            }
            return tts;
        }
    }

    private static void shutdownStatic() {
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
    }

    private static Locale localeFor(String lang) {
        switch (lang) {
            case "en": return Locale.ENGLISH;
            case "de": return Locale.GERMAN;
            case "es": return new Locale("es");
            case "pt": return new Locale("pt");
            case "fr":
            default:   return Locale.FRENCH;
        }
    }
}
