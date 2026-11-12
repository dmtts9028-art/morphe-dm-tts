package app.morphe.extension.instagram.tts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/**
 * Settings entry for DM TTS: the dialog opened by the inbox gear button (and by
 * {@link TtsCoordinator} when Phonon is selected without a model). Engine choice with the license
 * gate, model download, language, voices (global, imported, per conversation) and the advanced
 * download URL / Hugging Face token fields. Every widget is built in code; no resources are used.
 */
public final class TtsSettingsDialog {
    private static final String[] LANGUAGE_CODES = {"fr", "en", "de", "es", "pt"};
    private static final String[] LANGUAGE_NAMES =
            {"Français", "English", "Deutsch", "Español", "Português"};

    private static final boolean FRENCH = "fr".equals(Locale.getDefault().getLanguage());
    private static final String NO_BOUND_THREADS = FRENCH
            ? "Aucune conversation liée à une voix."
            : "No conversation is bound to a voice.";

    private static WeakReference<AlertDialog> openDialog;

    private TtsSettingsDialog() { }

    /** Opens the settings dialog on the main thread; does nothing without an activity. */
    public static void show() {
        if (!Utils.isCurrentlyOnMainThread()) {
            Utils.runOnMainThread(TtsSettingsDialog::show);
            return;
        }
        Activity activity = Utils.getActivity();
        if (activity == null) {
            Logger.printDebug(() -> "no activity: cannot open the DM TTS settings");
            return;
        }
        dismissOpenDialog();

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Dim.dp(16), Dim.dp(6), Dim.dp(16), Dim.dp(6));

        LinearLayout modelSection = new LinearLayout(activity);
        modelSection.setOrientation(LinearLayout.VERTICAL);

        addEngineSection(activity, body, modelSection);
        body.addView(modelSection, matchWrap());
        refreshModelSection(activity, modelSection);

        addLanguageSection(activity, body);
        addVoiceSection(activity, body);

        TextView threads = clickableRow(activity, TtsStr.VOICE_PER_THREAD);
        threads.setOnClickListener(v -> showThreadsDialog(activity));
        body.addView(threads, matchWrap());

        addAdvancedSection(activity, body);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(TtsStr.SETTINGS)
                .setView(scroll)
                .setNegativeButton(TtsStr.CANCEL, null)
                .show();
        openDialog = new WeakReference<>(dialog);
    }

    /**
     * Opens the voice-file picker. {@link ImportActivity} is a UI-less trampoline: it has to exist
     * because the extension cannot declare anything in the app manifest and only an activity can
     * receive the picker result.
     */
    public static void showImportVoicePicker() {
        Activity activity = Utils.getActivity();
        if (activity == null) {
            Logger.printDebug(() -> "no activity: cannot import a voice");
            return;
        }
        try {
            activity.startActivity(new Intent(activity, ImportActivity.class));
        } catch (Throwable ex) {
            Logger.printException(() -> "cannot start the voice import picker", ex);
            Utils.showToastShort(TtsStr.VOICE_IMPORT_FAILED);
        }
    }

    // ---------------------------------------------------------------------- engine

    private static void addEngineSection(Activity activity, LinearLayout body, LinearLayout modelSection) {
        body.addView(label(activity, TtsStr.ENGINE_LABEL), matchWrap());

        RadioGroup group = new RadioGroup(activity);
        RadioButton system = radioButton(activity, TtsStr.ENGINE_SYSTEM);
        RadioButton phonon = radioButton(activity, TtsStr.ENGINE_PHONON);
        group.addView(system, matchWrap());
        group.addView(phonon, matchWrap());
        group.check(TtsPrefs.phononSelected() ? phonon.getId() : system.getId());
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            String engine = checkedId == phonon.getId()
                    ? TtsPrefs.ENGINE_PHONON : TtsPrefs.ENGINE_SYSTEM;
            if (engine.equals(TtsPrefs.engine())) return;
            if (TtsPrefs.ENGINE_PHONON.equals(engine) && !TtsPrefs.licenseAccepted()) {
                showLicenseDialog(activity, accepted -> {
                    if (accepted) {
                        applyEngine(activity, modelSection, TtsPrefs.ENGINE_PHONON);
                    } else {
                        group.check(TtsPrefs.phononSelected() ? phonon.getId() : system.getId());
                    }
                });
                return;
            }
            applyEngine(activity, modelSection, engine);
        });
        body.addView(group, matchWrap());
    }

    private static void applyEngine(Activity activity, LinearLayout modelSection, String engine) {
        TtsPrefs.setEngine(engine);
        EngineFactory.close();
        refreshModelSection(activity, modelSection);
    }

    /** License gate: Confirm stays disabled until the checkbox is ticked. */
    private static void showLicenseDialog(Activity activity, Consumer<Boolean> onResult) {
        TextView text = value(activity, "");
        text.setAutoLinkMask(Linkify.WEB_URLS);
        text.setText(TtsStr.LICENSE_BODY);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        text.setPadding(0, Dim.dp(4), 0, Dim.dp(8));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text, matchWrap());

        CheckBox accept = new CheckBox(activity);
        accept.setText(TtsStr.LICENSE_ACCEPT);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Dim.dp(16), Dim.dp(6), Dim.dp(16), 0);
        body.addView(scroll, matchWrap());
        body.addView(accept, matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(TtsStr.LICENSE_TITLE)
                .setView(body)
                .setPositiveButton(TtsStr.CONFIRM, (ignored, which) -> {
                    TtsPrefs.setLicenseAccepted(true);
                    onResult.accept(true);
                })
                .setNegativeButton(TtsStr.CANCEL, (ignored, which) -> onResult.accept(false))
                .setCancelable(false)
                .create();
        dialog.show();

        Button confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (confirm != null) confirm.setEnabled(false);
        accept.setOnCheckedChangeListener((ignored, checked) -> {
            if (confirm != null) confirm.setEnabled(checked);
        });
    }

    // ----------------------------------------------------------------------- model

    private static void refreshModelSection(Activity activity, LinearLayout section) {
        section.removeAllViews();
        section.setVisibility(TtsPrefs.phononSelected() ? View.VISIBLE : View.GONE);
        if (EngineFactory.modelComplete()) {
            section.addView(value(activity, TtsStr.MODEL_DONE), matchWrap());
            return;
        }

        Button download = new Button(activity);
        download.setText(TtsStr.DOWNLOAD_MODEL);
        download.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        TextView progress = value(activity, "");
        progress.setVisibility(View.GONE);

        download.setOnClickListener(v -> {
            if (ModelDownloader.isRunning()) return;
            v.setEnabled(false);
            progress.setText(TtsStr.DOWNLOADING);
            progress.setVisibility(View.VISIBLE);
            Utils.runOnBackgroundThread(() -> downloadModel(activity, section, progress));
        });

        section.addView(download, matchWrap());
        section.addView(progress, matchWrap());
    }

    /** Runs on a background thread; {@code progress} is only touched through the main thread. */
    private static void downloadModel(Activity activity, LinearLayout section, TextView progress) {
        long[] lastShown = {0};
        try {
            ModelDownloader.downloadModel((name, done, total) -> {
                long now = System.currentTimeMillis();
                if (now - lastShown[0] < 200) return;
                lastShown[0] = now;
                String text = progressText(name, done, total);
                Utils.runOnMainThread(() -> progress.setText(text));
            });
            Utils.showToastShort(TtsStr.MODEL_DONE);
        } catch (Exception ex) {
            Logger.printException(() -> "model download failed", ex);
            deletePartialFiles(EngineFactory.modelDir());
            Utils.showToastShort(TtsStr.DOWNLOAD_FAILED);
        }
        Utils.runOnMainThread(() -> refreshModelSection(activity, section));
    }

    private static String progressText(String name, long done, long total) {
        String unit = FRENCH ? " Mo" : " MB";
        if (total > 0) return name + " " + megabytes(done) + "/" + megabytes(total) + unit;
        return name + " " + megabytes(done) + unit;
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1_048_576f);
    }

    /** Removes leftover *.part files of an interrupted download (model dir, one level deep). */
    private static void deletePartialFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                File[] nested = file.listFiles();
                if (nested == null) continue;
                for (File child : nested) {
                    if (child.getName().endsWith(".part")) {
                        //noinspection ResultOfMethodCallIgnored
                        child.delete();
                    }
                }
            } else if (file.getName().endsWith(".part")) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    // ------------------------------------------------------------- language & voices

    private static void addLanguageSection(Activity activity, LinearLayout body) {
        body.addView(label(activity, TtsStr.LANGUAGE), matchWrap());
        Spinner spinner = new Spinner(activity);
        spinner.setAdapter(spinnerAdapter(activity, asList(LANGUAGE_NAMES)));
        spinner.setSelection(languageIndex(TtsPrefs.lang()));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String code = LANGUAGE_CODES[position];
                if (!code.equals(TtsPrefs.lang())) TtsPrefs.setLang(code);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(spinner, matchWrap());
    }

    private static void addVoiceSection(Activity activity, LinearLayout body) {
        List<String> voices = VoiceManager.voices();
        body.addView(label(activity, TtsStr.VOICE), matchWrap());

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Spinner spinner = voiceSpinner(activity, voices, TtsPrefs.voice(), TtsPrefs::setVoice);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(spinner, spinnerParams);

        Button importVoice = new Button(activity);
        importVoice.setText(TtsStr.VOICE_IMPORT);
        importVoice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        importVoice.setMaxWidth(Dim.dp(120));
        importVoice.setOnClickListener(v -> showImportVoicePicker());
        row.addView(importVoice, matchWrap());

        body.addView(row, matchWrap());
    }

    private static void showThreadsDialog(Activity activity) {
        List<String> voices = VoiceManager.voices();
        Set<String> threadIds = TtsPrefs.boundThreadIds();

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Dim.dp(16), Dim.dp(6), Dim.dp(16), Dim.dp(6));

        if (threadIds.isEmpty()) {
            body.addView(value(activity, NO_BOUND_THREADS), matchWrap());
        } else {
            for (String threadId : threadIds) {
                body.addView(label(activity, threadId), matchWrap());
                body.addView(voiceSpinner(activity, voices, TtsPrefs.threadVoice(threadId),
                        voice -> TtsPrefs.setThreadVoice(threadId, voice)), matchWrap());
                Button export = new Button(activity);
                export.setText(TtsStr.EXPORT_VOICE_NOTES + " (" + VoiceNoteExport.collectedCount(threadId) + ")");
                export.setOnClickListener(v -> VoiceNoteExport.export(threadId));
                body.addView(export, matchWrap());
            }
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
                .setTitle(TtsStr.VOICE_PER_THREAD)
                .setView(scroll)
                .setNegativeButton(TtsStr.CANCEL, null)
                .show();
    }

    private static Spinner voiceSpinner(Activity activity, List<String> voices, String selected,
                                        Consumer<String> onPick) {
        List<String> names = new ArrayList<>(voices);
        if (names.isEmpty()) names.add(VoiceManager.DEFAULT_VOICE);
        Spinner spinner = new Spinner(activity);
        spinner.setAdapter(spinnerAdapter(activity, names));
        int index = Math.max(0, names.indexOf(selected));
        spinner.setSelection(index);
        String[] current = {names.get(index)};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String voice = names.get(position);
                if (voice.equals(current[0])) return;
                current[0] = voice;
                onPick.accept(voice);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        return spinner;
    }

    // -------------------------------------------------------------------- advanced

    private static void addAdvancedSection(Activity activity, LinearLayout body) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setVisibility(View.GONE);

        EditText url = editText(activity, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI, TtsPrefs.modelUrl());
        EditText token = editText(activity, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD, TtsPrefs.hfToken());

        Button save = new Button(activity);
        save.setText(TtsStr.SAVE);
        save.setOnClickListener(v -> {
            TtsPrefs.prefs().saveString(TtsPrefs.KEY_MODEL_URL, url.getText().toString().trim());
            TtsPrefs.setHfToken(token.getText().toString());
            EngineFactory.close();
            Utils.showToastShort(TtsStr.SAVE);
        });

        section.addView(label(activity, TtsStr.MODEL_URL), matchWrap());
        section.addView(url, matchWrap());
        section.addView(label(activity, TtsStr.HF_TOKEN), matchWrap());
        section.addView(token, matchWrap());
        section.addView(save, matchWrap());

        Button selfTest = new Button(activity);
        selfTest.setText("Auto-test");
        selfTest.setOnClickListener(v -> {
            Utils.showToastShort(TtsStr.GENERATING);
            TtsDebug.run();
        });
        section.addView(selfTest, matchWrap());

        TextView header = clickableRow(activity, TtsStr.ADVANCED);
        header.setOnClickListener(v -> section.setVisibility(
                section.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        body.addView(header, matchWrap());
        body.addView(section, matchWrap());
    }

    // ------------------------------------------------------------------ voice import

    /** UI-less activity: asks for a file, imports it, toasts, closes. */
    public static class ImportActivity extends Activity {
        private static final int PICK_REQUEST = 0x7D05;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(android.R.style.Theme_Translucent_NoTitleBar);
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) return;
            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("*/*");
            picker.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/octet-stream", "*/*"});
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(picker, PICK_REQUEST);
            } catch (Throwable ex) {
                Logger.printException(() -> "no file picker available", ex);
                Utils.showToastShort(TtsStr.VOICE_IMPORT_FAILED);
                finish();
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            Uri uri = data == null ? null : data.getData();
            if (requestCode != PICK_REQUEST || resultCode != RESULT_OK || uri == null) {
                finish();
                return;
            }
            // The read grant lives as long as this activity does: finish only once copied.
            Utils.runOnBackgroundThread(() -> {
                String voice = null;
                try {
                    voice = VoiceManager.importVoice(this, uri);
                } catch (Exception ex) {
                    Logger.printException(() -> "voice import failed", ex);
                }
                final String imported = voice;
                Utils.showToastShort(imported == null
                        ? TtsStr.VOICE_IMPORT_FAILED : TtsStr.VOICE_IMPORTED + ": " + imported);
                Utils.runOnMainThread(this::finish);
            });
        }
    }

    // ---------------------------------------------------------------- widget helpers

    private static void dismissOpenDialog() {
        WeakReference<AlertDialog> reference = openDialog;
        openDialog = null;
        if (reference == null) return;
        AlertDialog previous = reference.get();
        if (previous != null && previous.isShowing()) previous.dismiss();
    }

    private static ArrayAdapter<String> spinnerAdapter(Context context, List<String> entries) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static List<String> asList(String[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (String value : values) list.add(value);
        return list;
    }

    private static int languageIndex(String code) {
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) return i;
        }
        return 0;
    }

    private static RadioButton radioButton(Context context, String text) {
        RadioButton button = new RadioButton(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setPadding(Dim.dp(4), Dim.dp(4), Dim.dp(4), Dim.dp(4));
        return button;
    }

    private static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(TtsPlayerBar.secondaryTextColor());
        view.setText(text);
        view.setPadding(Dim.dp(2), Dim.dp(10), Dim.dp(2), 0);
        return view;
    }

    private static TextView value(Context context, String text) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTextColor(TtsPlayerBar.themed("igds_color_primary_text",
                TtsPlayerBar.darkMode() ? 0xfff5f5f5 : 0xff262626));
        view.setText(text);
        view.setPadding(Dim.dp(2), Dim.dp(2), Dim.dp(2), Dim.dp(2));
        return view;
    }

    private static TextView clickableRow(Context context, String text) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTextColor(TtsPlayerBar.linkColor());
        view.setText(text);
        view.setPadding(Dim.dp(2), Dim.dp(12), Dim.dp(2), Dim.dp(12));
        return view;
    }

    private static EditText editText(Context context, int inputType, String text) {
        EditText edit = new EditText(context);
        edit.setInputType(inputType);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        edit.setText(text);
        return edit;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
