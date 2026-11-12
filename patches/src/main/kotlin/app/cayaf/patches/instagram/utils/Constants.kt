/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi.ARM64_V8A

object Constants {
    val COMPATIBILITY_INSTAGRAM =
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0xFC483C,
            targets =
                listOf(
                    AppTarget(
                        version = "452.1.0.53.64",
                        versionCodes =
                            mapOf(
                                ARM64_V8A to 385811006,
                            ),
                    ),
                ),
        )

    // Extension classes.
    const val EXTENSIONS_PACKAGE = "Lapp/morphe/extension/instagram"
    const val TTS_PACKAGE = "$EXTENSIONS_PACKAGE/tts"
    const val MESSAGE_TTS_DESCRIPTOR = "$TTS_PACKAGE/MessageTtsView;"
    const val TTS_SETTINGS_DESCRIPTOR = "$TTS_PACKAGE/TtsSettingsDialog;"
    const val TTS_VOICE_EXPORT_DESCRIPTOR = "$TTS_PACKAGE/VoiceNoteExport;"
    const val TTS_ACTION_BAR_DESCRIPTOR = "$TTS_PACKAGE/TtsActionBar;"

    // Instagram classes.
    const val USER_SESSION_CLASS = "Lcom/instagram/common/session/UserSession;"
    const val DIRECT_THREAD_KEY_CLASS = "Lcom/instagram/direct/model/DirectThreadKey;"
}
