package app.morphe.extension.instagram.tts;

/** Checked failure for every unrecoverable synthesis step (missing model, broken engine). */
public class TtsUnavailableException extends Exception {
    public TtsUnavailableException(String message) {
        super(message);
    }

    public TtsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
