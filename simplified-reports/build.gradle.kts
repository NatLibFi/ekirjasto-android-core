fun getGitHash(): String {
    // Ekirjasto: required for Gradle Configuration cache, to prevent reconfiguration
    //   if hash was not changed.
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SIMPLIFIED_GIT_COMMIT", "\"${getGitHash()}\"")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
