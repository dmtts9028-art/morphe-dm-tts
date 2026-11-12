/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.tts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.cayaf.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.string

/**
 * The view holder of a written direct message, found by the bubble layout it resolves.
 * Verified against Instagram 452.1.0.53.64 arm64-v8a.
 */
internal object TextMessageBinderFingerprint : Fingerprint(
    definingClass = "LX/0Irz;",
)

/**
 * Fills the views of a written message with the message: the only instance void method of the
 * binder taking exactly two object parameters (view holder, message model).
 */
internal object TextMessageBindFingerprint : Fingerprint(
    classFingerprint = TextMessageBinderFingerprint,
    name = "A03",
    isStatic = false,
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = listOf(
        // The bind attaches a listener to a view of the holder.
        string("setOnClickListener"),
    ),
)

/** Voice-message row binder factory (pins `message_content_voice_bubble_container`). */
internal object VoiceMessageBinderFingerprint : Fingerprint(
    definingClass = "LX/03Pn;",
)

/** Fills the views of a voice message: the binder's void two-object method. */
internal object VoiceMessageBindFingerprint : Fingerprint(
    classFingerprint = VoiceMessageBinderFingerprint,
    name = "AX2",
    isStatic = false,
    returnType = "V",
    parameters = listOf("LX/0Ijp;", "LX/0pjd;"),
)
