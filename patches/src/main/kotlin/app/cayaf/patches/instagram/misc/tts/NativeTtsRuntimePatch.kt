/*
 * Copyright (C) 2026 dmtts9028-art <https://github.com/dmtts9028-art/morphe-dm-tts>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.cayaf.patches.instagram.misc.tts

import app.cayaf.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.util.inputStreamFromBundledResource

// The only architecture this bundle ships libraries for, the one its AppTarget pins.
private const val ABI = "arm64-v8a"

// The libraries bundled under patches/src/main/resources/native/, loaded through JNA at first use.
private val NATIVE_LIBRARIES = listOf(
    "libptts_ffi.so",
    "libjnidispatch.so",
)

@Suppress("unused")
val nativeTtsRuntimePatch =
    patch(
        name = "TTS runtime libraries",
        description = "Adds Phonon TTS + JNA native libraries (arm64-v8a).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            NATIVE_LIBRARIES.forEach { name ->
                val stream = inputStreamFromBundledResource("native", name)
                    ?: throw PatchException("The native library $name is not bundled with the patch")

                // copy = false because the APK holds no such library to decode: the file is added to it.
                resourceFile("lib/$ABI/$name", copy = false).writeBytes(stream.use { it.readBytes() })
            }
        }
    }
