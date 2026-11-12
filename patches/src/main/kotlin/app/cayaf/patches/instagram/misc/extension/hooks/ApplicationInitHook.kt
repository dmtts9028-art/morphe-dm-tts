/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.all.misc.extension.ExtensionHook
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

internal val instagramInitHook =
    ExtensionHook(
        fingerprint =
            Fingerprint(
                definingClass = "Lcom/instagram/app/InstagramAppShell;",
                name = "onCreate",
            ),
        insertIndexResolver = { method ->
            method.indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER) + 1
        },
        contextRegisterResolver = { method ->
            "v${method.getInstruction<FiveRegisterInstruction>(
                method.indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER)).registerC}"
        },
    )
