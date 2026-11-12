group = "app.morphe"

patches {
    about {
        name = "DM TTS"
        description = "Instagram DM text-to-speech patches for use with Morphe"
        source = "https://github.com/dmtts9028-art/morphe-dm-tts.git"
        author = "dmtts9028-art"
        contact = "na"
        website = "https://github.com/dmtts9028-art/morphe-dm-tts"
        license = "GPLv3"
    }
}

dependencies {
    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
