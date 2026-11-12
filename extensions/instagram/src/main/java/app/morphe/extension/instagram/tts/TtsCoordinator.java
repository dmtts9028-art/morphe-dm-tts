package app.morphe.extension.instagram.tts;

import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** Orchestrates listen → synthesize → cache → play for a message key. */
final class TtsCoordinator {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dmtts-synth");
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        return thread;
    });

    private TtsCoordinator() { }

    /** Link tap: play from cache, or synthesize then play. */
    static void listen(String key, String text, String threadId) {
        TtsPlayer player = TtsPlayer.get();
        TtsPlayer.MessageState state = TtsPlayer.state(key);
        if (state.status == TtsPlayer.Status.GENERATING) {
            if (player.playingKey() != null && !player.playingKey().equals(key)) {
                player.stopAll();
            }
            return;
        }
        player.stopAll();
        TtsCache.Entry entry = TtsCache.get(key);
        if (entry != null) {
            state.entry = entry;
            play(key, entry, 0);
            return;
        }
        if (TtsPrefs.phononSelected() && !EngineFactory.modelComplete()) {
            Utils.showToastShort(TtsStr.MODEL_MISSING);
            TtsSettingsDialog.show();
            return;
        }
        state.status = TtsPlayer.Status.GENERATING;
        state.positionSamples = 0;
        MessageTtsView.refresh(key, state);
        String voice = VoiceManager.resolveVoice(threadId);
        EXECUTOR.execute(() -> synthesize(key, text, voice));
    }

    /** Player-bar tap. */
    static void togglePlay(String key) {
        TtsPlayer player = TtsPlayer.get();
        TtsPlayer.MessageState state = TtsPlayer.state(key);
        switch (state.status) {
            case PLAYING:
                player.pause();
                break;
            case PAUSED:
                player.resume(key);
                break;
            case DONE:
                TtsCache.Entry entry = state.entry != null ? state.entry : TtsCache.get(key);
                if (entry == null) {
                    state.entry = null;
                    MessageTtsView.refresh(key, state);
                } else {
                    state.entry = entry;
                    play(key, entry, state.positionSamples);
                }
                break;
            case GENERATING:
            case IDLE:
            default:
                break;
        }
    }

    private static void play(String key, TtsCache.Entry entry, long startSample) {
        TtsPlayer.get().play(key, entry.wav, startSample);
    }

    private static void synthesize(String key, String text, String voice) {
        TtsPlayer.MessageState state = TtsPlayer.state(key);
        File dir = TtsCache.dir();
        File outWav = new File(dir, key + ".wav");
        File temp = new File(dir, key + ".synth.wav");
        try {
            TtsEngine engine = EngineFactory.get();
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            engine.synthesize(text, voice, key, temp);
            if (!temp.isFile() || temp.length() == 0) {
                throw new TtsUnavailableException("empty synthesis");
            }
            //noinspection ResultOfMethodCallIgnored
            outWav.delete();
            if (!temp.renameTo(outWav)) throw new IOException("cannot move cache wav");
            writeSidecar(key, outWav);
            TtsCache.Entry entry = TtsCache.get(key);
            if (entry == null) throw new IOException("cache entry missing after synthesis");
            state.entry = entry;
            state.status = TtsPlayer.Status.DONE;
            state.positionSamples = 0;
            MessageTtsView.refresh(key, state);
            // Ready: start playback automatically.
            play(key, entry, 0);
        } catch (TtsUnavailableException | IOException ex) {
            Logger.printException(() -> "synthesis failed for " + key, ex);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            String message = TtsStr.MODEL_MISSING.equals(ex.getMessage())
                    ? TtsStr.MODEL_MISSING : TtsStr.PLAYBACK_FAILED;
            state.status = TtsPlayer.Status.IDLE;
            MessageTtsView.refresh(key, state);
            Utils.showToastShort(message);
        }
    }

    /** Writes the duration/peaks sidecar; system-TTS WAVs fall back to MediaMetadataRetriever. */
    private static void writeSidecar(String key, File wav) {
        float[] pcm = WavIo.readFloats(wav, 24_000 * 300);
        if (pcm != null) {
            TtsCache.put(key, wav, pcm, WavIo.SAMPLE_RATE);
            return;
        }
        int sampleRate = 16_000;
        long durationMs = 0;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(wav.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) durationMs = Long.parseLong(duration);
        } catch (Throwable ex) {
            Logger.printDebug(() -> "sidecar fallback failed: " + ex.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        int samples = (int) Math.min(durationMs * sampleRate / 1000L, 16_000L * 300);
        TtsCache.put(key, wav, new float[Math.max(samples, 1)], sampleRate);
    }
}
