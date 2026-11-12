package app.morphe.extension.instagram.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Singleton playback + per-row state. State lives in a static map keyed by message key so
 * recycled RecyclerView rows re-render their live status on rebind.
 */
final class TtsPlayer {
    enum Status {IDLE, GENERATING, PLAYING, PAUSED, DONE}

    static final class MessageState {
        volatile Status status = Status.IDLE;
        /** Playback position in samples, valid while PAUSED or while playing. */
        volatile long positionSamples;
        volatile TtsCache.Entry entry;
    }

    private static final Map<String, MessageState> STATES = new ConcurrentHashMap<>();
    private static final TtsPlayer INSTANCE = new TtsPlayer();

    private MediaPlayer player;
    private String playingKey;
    private final AtomicBoolean noisyReceiverRegistered = new AtomicBoolean();

    // Attached UI, updated on every bind; progress updates are pushed here.
    private volatile WeakReference<TtsPlayerBar> attachedBar;
    private volatile String attachedKey;
    private volatile OnStateChange onStateChange;

    interface OnStateChange {
        void onState(String key, MessageState state);
    }

    static TtsPlayer get() {
        return INSTANCE;
    }

    static MessageState state(String key) {
        return STATES.computeIfAbsent(key, k -> new MessageState());
    }

    void setOnStateChange(OnStateChange callback) {
        onStateChange = callback;
    }

    void attach(TtsPlayerBar bar, String key) {
        attachedBar = bar == null ? null : new WeakReference<>(bar);
        attachedKey = key;
    }

    synchronized void play(String key, File wav, long startSample) {
        stopPlayback();
        registerNoisyReceiver();
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mediaPlayer.setDataSource(wav.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(completed -> {
                Logger.printDebug(() -> "playback complete " + key);
                MessageState state = state(key);
                state.status = Status.DONE;
                state.positionSamples = 0;
                player = null;
                playingKey = null;
                notifyState(key, state);
            });
            mediaPlayer.setOnErrorListener((errorPlayer, what, extra) -> {
                Logger.printException(() -> "MediaPlayer error " + what + "/" + extra);
                MessageState state = state(key);
                state.status = Status.DONE;
                notifyState(key, state);
                return true;
            });
            mediaPlayer.prepare();
            if (startSample > 0) {
                mediaPlayer.seekTo((int) (startSample * 1000L / Math.max(1, entrySampleRate(key))));
            }
            mediaPlayer.start();
            player = mediaPlayer;
            playingKey = key;
            MessageState state = state(key);
            state.status = Status.PLAYING;
            state.entry = entryOrLoad(key, wav);
            notifyState(key, state);
        } catch (IOException | IllegalStateException ex) {
            Logger.printException(() -> "playback failed", ex);
            try {
                mediaPlayer.release();
            } catch (Throwable ignored) {
            }
            Utils.showToastShort(TtsStr.PLAYBACK_FAILED);
            MessageState state = state(key);
            state.status = Status.DONE;
            notifyState(key, state);
        }
    }

    synchronized void pause() {
        MediaPlayer current = player;
        if (current == null || !current.isPlaying()) return;
        current.pause();
        MessageState state = state(playingKey);
        state.status = Status.PAUSED;
        state.positionSamples = currentSamplePosition();
        notifyState(playingKey, state);
    }

    /** Resumes the paused utterance of {@code key} from its stored position. */
    synchronized void resume(String key) {
        TtsCache.Entry entry = state(key).entry;
        if (entry == null) return;
        play(key, entry.wav, state(key).positionSamples);
    }

    synchronized void stopPlayback() {
        MediaPlayer current = player;
        if (current == null) return;
        try {
            if (current.isPlaying()) {
                state(playingKey).positionSamples = currentSamplePosition();
            }
            current.stop();
        } catch (Throwable ignored) {
        }
        try {
            current.release();
        } catch (Throwable ignored) {
        }
        player = null;
        playingKey = null;
    }

    /** Called before a new synthesis and on BECOMING_NOISY: silences anything audible. */
    void stopAll() {
        stopPlayback();
        PhononNative.requestStopQuietly();
    }

    String playingKey() {
        return playingKey;
    }

    /** Progress tick for the attached bar; called from the UI frame callback. */
    void pushProgress() {
        WeakReference<TtsPlayerBar> bar = attachedBar;
        String key = attachedKey;
        MediaPlayer current = player;
        if (bar == null || key == null || current == null || !key.equals(playingKey)) return;
        MessageState state = state(key);
        if (state.status != Status.PLAYING) return;
        state.positionSamples = currentSamplePosition();
        TtsPlayerBar ui = bar.get();
        if (ui != null && key.equals(ui.key())) ui.setProgress(current.getCurrentPosition());
    }

    private TtsCache.Entry entryOrLoad(String key, File wav) {
        TtsCache.Entry entry = state(key).entry;
        if (entry != null) return entry;
        long samples = WavIo.sampleCount(wav);
        float[] peaks = TtsCache.silentPeaks();
        float[] pcm = WavIo.readFloats(wav, 24_000 * 300);
        if (pcm != null) peaks = TtsCache.peaks(pcm);
        return new TtsCache.Entry(wav, samples * 1000L / WavIo.SAMPLE_RATE, peaks, WavIo.SAMPLE_RATE);
    }

    private int entrySampleRate(String key) {
        TtsCache.Entry entry = state(key).entry;
        return entry == null ? WavIo.SAMPLE_RATE : entry.sampleRate;
    }

    private long currentSamplePosition() {
        MediaPlayer current = player;
        if (current == null) return 0;
        return current.getCurrentPosition() * (long) entrySampleRate(playingKey) / 1000L;
    }

    private void notifyState(String key, MessageState state) {
        OnStateChange callback = onStateChange;
        if (callback != null) Utils.runOnMainThread(() -> callback.onState(key, state));
    }

    private void registerNoisyReceiver() {
        if (!noisyReceiverRegistered.compareAndSet(false, true)) return;
        try {
            Context context = Utils.getContext();
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context receivedContext, Intent intent) {
                    stopAll();
                }
            }, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        } catch (Throwable ex) {
            Logger.printDebug(() -> "noisy receiver unavailable: " + ex.getMessage());
        }
    }
}
