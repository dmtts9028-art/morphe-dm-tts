/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.tts

import app.cayaf.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

/**
 * The per-message UI: hooks the written-message binder so MessageTtsView can attach an
 * "Écouter le message" link (and after first tap, a voice-note style player) above the bubble.
 */
@Suppress("unused")
val messageTtsPatch =
    patch(
        name = "Écouter les messages (TTS)",
        description = "Adds a listen link and a voice-message-like player above written DMs.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(nativeTtsRuntimePatch)
        extendWith("extensions/instagram.mpe")

        execute {
            // The bind method runs before its registers are claimed, so p1/p2 move into v0/v1
            // and are handed to the extension (view holder, message model).
            TextMessageBindFingerprint.method.addInstructions(
                0,
                """
                move-object/from16 v0, p1
                move-object/from16 v1, p2
                invoke-static {v0, v1}, Lapp/morphe/extension/instagram/tts/MessageTtsView;->onBind(Ljava/lang/Object;Ljava/lang/Object;)V
                """,
            )
        }
    }
