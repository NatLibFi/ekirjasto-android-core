dependencies {
    api(project(":simplified-parser-api"))
    api(project(":simplified-links"))

    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}
