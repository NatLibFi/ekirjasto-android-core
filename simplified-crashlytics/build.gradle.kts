dependencies {
    implementation(project(":simplified-crashlytics-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.firebase.config.interop)
    implementation(libs.firebase.crashlytics)
}
