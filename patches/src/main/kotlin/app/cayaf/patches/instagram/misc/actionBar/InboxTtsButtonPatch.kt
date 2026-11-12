/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.actionBar

import app.cayaf.patches.instagram.misc.tts.messageTtsPatch
import app.cayaf.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.cayaf.patches.instagram.utils.Constants.TTS_ACTION_BAR_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/** The inbox action bar builder, found by its debug tag and the IgdsActionBar it binds. */
internal object InboxActionBarBuilderFingerprint : Fingerprint(
    strings = listOf("PrebindActionBar"),
    filters = listOf(checkCast("Lcom/instagram/igds/components/actionbar/IgdsActionBar;")),
)

/** Adds the DM TTS settings button to the direct-inbox action bar. */
@Suppress("unused")
val inboxTtsButtonPatch =
    patch(
        name = "Réglages DM TTS dans la boîte de réception",
        description = "Adds a settings button to the inbox action bar opening the DM TTS options.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(messageTtsPatch)

        execute {
            InboxActionBarBuilderFingerprint.let {
                val castInstruction = it.instructionMatches.first()
                val actionBarRegister = castInstruction.getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstructions(
                    castInstruction.index + 1,
                    """
                    invoke-static {v$actionBarRegister}, $TTS_ACTION_BAR_DESCRIPTOR->addButton(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }
        }
    }
