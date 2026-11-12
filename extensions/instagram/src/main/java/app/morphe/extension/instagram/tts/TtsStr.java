package app.morphe.extension.instagram.tts;

import java.util.Locale;

/** FR-first UI strings; EN when the device locale is not French. */
final class TtsStr {
    static final String LISTEN = fr("Écouter le message", "Listen to message");
    static final String GENERATING = fr("Génération…", "Generating…");
    static final String HIDE = fr("Masquer", "Hide");
    static final String SETTINGS = fr("Réglages DM TTS", "DM TTS settings");
    static final String DOWNLOAD_MODEL = fr("Télécharger le modèle Vocal (Phonon 40m)", "Download Vocal model (Phonon 40m)");
    static final String DOWNLOADING = fr("Téléchargement…", "Downloading…");
    static final String MODEL_MISSING = fr("Téléchargez d'abord le modèle Vocal dans les réglages", "Download the Vocal model first in settings");
    static final String MODEL_DONE = fr("Modèle installé", "Model installed");
    static final String DOWNLOAD_FAILED = fr("Échec du téléchargement (empreinte invalide ?)", "Download failed (bad checksum?)");
    static final String ENGINE_LABEL = fr("Moteur de synthèse vocale", "Speech engine");
    static final String ENGINE_SYSTEM = fr("Système", "System");
    static final String ENGINE_PHONON = fr("Phonon 40m (vocal sur l'appareil)", "Phonon 40m (on-device voice)");
    static final String LANGUAGE = fr("Langue", "Language");
    static final String VOICE = fr("Voix", "Voice");
    static final String VOICE_PER_THREAD = fr("Voix par conversation", "Voice per conversation");
    static final String VOICE_IMPORT = fr("Importer une voix (.safetensors)", "Import a voice (.safetensors)");
    static final String VOICE_IMPORTED = fr("Voix importée", "Voice imported");
    static final String VOICE_IMPORT_FAILED = fr("Import impossible", "Import failed");
    static final String ADVANCED = fr("Avancé", "Advanced");
    static final String MODEL_URL = fr("Adresse de téléchargement", "Download URL");
    static final String HF_TOKEN = fr("Jeton Hugging Face (facultatif)", "Hugging Face token (optional)");
    static final String SAVE = fr("Enregistrer", "Save");
    static final String CANCEL = fr("Annuler", "Cancel");
    static final String CONFIRM = fr("Confirmer", "Confirm");
    static final String LICENSE_TITLE = fr("Licence du modèle", "Model license");
    static final String LICENSE_BODY =
            "Phonon 40m multilingual — © Gradium\n"
                    + "https://huggingface.co/gradium/phonon-40m-multilingual\n\n"
                    + "Licence : Creative Commons Attribution - NonCommercial 4.0 "
                    + "International (CC BY-NC 4.0).\n\n"
                    + "Vous pouvez copier et redistribuer le modèle, à condition de créditer "
                    + "Gradium et de ne l'utiliser que dans un cadre non commercial.\n"
                    + "Texte complet : https://creativecommons.org/licenses/by-nc/4.0/legalcode.fr\n\n"
                    + "English: you may share and adapt this model for any non-commercial use, "
                    + "giving appropriate credit to Gradium.";
    static final String LICENSE_ACCEPT = fr("J'accepte les conditions", "I accept the license terms");
    static final String STOP = fr("Arrêter", "Stop");
    static final String EXPORT_VOICE_NOTES = fr("Extraire les notes vocales", "Export voice notes");
    static final String EXPORTING = fr("Extraction des notes vocales…", "Exporting voice notes…");
    static final String EXPORT_DONE = fr("Notes vocales exportées dans le dossier de l'application", "Voice notes exported to the app folder");
    static final String EXPORT_NONE = fr("Aucune note vocale dans cette conversation", "No voice notes in this conversation");
    static final String EXPORT_CONSENT = fr("N'exportez que l'audio de personnes qui y consentent.", "Only export audio of people who consent.");
    static final String PLAYBACK_FAILED = fr("Lecture impossible", "Playback failed");

    private TtsStr() { }

    private static boolean french() {
        return "fr".equals(Locale.getDefault().getLanguage());
    }

    private static String fr(String french, String english) {
        return french() ? french : english;
    }
}
