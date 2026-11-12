/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.extension

import app.cayaf.patches.instagram.misc.extension.hooks.instagramInitHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

@Suppress("unused")
val sharedExtensionPatch =
    sharedExtensionPatch(
        listOf("shared", "instagram"),
        instagramInitHook,
    )
