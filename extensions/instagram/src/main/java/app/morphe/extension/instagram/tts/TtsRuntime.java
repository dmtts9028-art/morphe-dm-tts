/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.morphe.extension.instagram.tts;

import app.morphe.extension.shared.Logger;

/**
 * One-time startup work: verifies the native libraries can be mapped and runs
 * the synthesis self-test when {@code dm_tts__debug_autotest} is enabled.
 */
@SuppressWarnings("unused")
final class TtsRuntime {
    private static volatile boolean initialized;

    private TtsRuntime() { }

    public static void init() {
        if (initialized) return;
        synchronized (TtsRuntime.class) {
            if (initialized) return;
            initialized = true;
            try {
                PhononNative.library();
                android.util.Log.i("DmTts", "phonon library loaded");
            } catch (Throwable ex) {
                android.util.Log.e("DmTts", "phonon library load failed", ex);
                return;
            }
            // The dialog's Advanced → Auto-test sets the preference; the flag file lets the
            // self-test run without UI access (adb push into the app's external files dir).
            boolean flag = TtsPrefs.prefs().getBoolean("debug_autotest", false);
            android.util.Log.i("DmTts", "init done autotest=" + flag);
            if (flag) {
                TtsDebug.run();
            }
        }
    }
}
