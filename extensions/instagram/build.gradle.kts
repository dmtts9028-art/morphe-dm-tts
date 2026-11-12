extension {
    name = "extensions/instagram.mpe"
}

android {
    namespace = "app.morphe.extension.instagram"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)

    // Runtime dependency: must be merged into the .mpe so JNA classes resolve at runtime.
    implementation(libs.jna)
}
