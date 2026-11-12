package app.morphe.extension.instagram.tts;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import app.morphe.extension.shared.settings.preference.SharedPrefCategory;

/** All persisted DM TTS state under one preference file. */
final class TtsPrefs {
    static final String ENGINE_SYSTEM = "system";
    static final String ENGINE_PHONON = "phonon";

    static final String KEY_ENGINE = "tts_engine";
    static final String KEY_LICENSE_ACCEPTED = "tts_license_accepted";
    static final String KEY_MODEL_URL = "tts_model_url";
    static final String KEY_HF_TOKEN = "tts_hf_token";
    static final String KEY_LANG = "tts_lang";
    static final String KEY_VOICE = "tts_voice";
    static final String KEY_HIDDEN = "tts_hidden_messages";
    private static final String KEY_THREAD_VOICE_PREFIX = "tts_thread_voice:";

    private static final String DEFAULT_MODEL_URL =
            "https://github.com/dmtts9028-art/morphe-dm-tts/releases/download/model-phonon-v1/";

    private static SharedPrefCategory prefs;

    private TtsPrefs() { }

    static synchronized SharedPrefCategory prefs() {
        if (prefs == null) prefs = new SharedPrefCategory("dm_tts");
        return prefs;
    }

    static String engine() {
        return prefs().getString(KEY_ENGINE, ENGINE_SYSTEM);
    }

    static void setEngine(String engine) {
        prefs().saveString(KEY_ENGINE, engine);
    }

    static boolean phononSelected() {
        return ENGINE_PHONON.equals(engine());
    }

    static boolean licenseAccepted() {
        return prefs().getBoolean(KEY_LICENSE_ACCEPTED, false);
    }

    static void setLicenseAccepted(boolean accepted) {
        prefs().saveBoolean(KEY_LICENSE_ACCEPTED, accepted);
    }

    static String modelUrl() {
        String url = prefs().getString(KEY_MODEL_URL, "");
        return url.trim().isEmpty() ? DEFAULT_MODEL_URL : url.trim();
    }

    static String hfToken() {
        return prefs().getString(KEY_HF_TOKEN, "");
    }

    static void setHfToken(String token) {
        prefs().saveString(KEY_HF_TOKEN, token.trim());
    }

    static String lang() {
        return prefs().getString(KEY_LANG, "fr");
    }

    static void setLang(String lang) {
        prefs().saveString(KEY_LANG, lang);
    }

    /** Selected voice; "default" means the checkpoint's built-in voice. */
    static String voice() {
        return prefs().getString(KEY_VOICE, "default");
    }

    static void setVoice(String voice) {
        prefs().saveString(KEY_VOICE, voice);
    }

    static String threadVoice(String threadId) {
        return prefs().getString(KEY_THREAD_VOICE_PREFIX + threadId, "");
    }

    static void setThreadVoice(String threadId, String voice) {
        prefs().saveString(KEY_THREAD_VOICE_PREFIX + threadId, voice == null ? "" : voice);
    }

    /** Empty when the conversation has no explicit voice binding. */
    static Set<String> boundThreadIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : prefs().preferences.getAll().keySet()) {
            if (key.startsWith(KEY_THREAD_VOICE_PREFIX)
                    && !prefs().getString(key, "").isEmpty()) {
                ids.add(key.substring(KEY_THREAD_VOICE_PREFIX.length()));
            }
        }
        return ids;
    }

    static boolean isHidden(String messageKey) {
        return hidden().contains(messageKey);
    }

    static void hide(String messageKey) {
        Set<String> hidden = new HashSet<>(hidden());
        hidden.add(messageKey);
        saveHidden(hidden);
    }

    private static Set<String> hidden() {
        return prefs().preferences.getStringSet(KEY_HIDDEN, Collections.emptySet());
    }

    private static void saveHidden(Set<String> keys) {
        SharedPreferences.Editor editor = prefs().preferences.edit();
        editor.putStringSet(KEY_HIDDEN, keys).apply();
    }
}
