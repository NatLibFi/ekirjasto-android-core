import java.io.FileInputStream
import java.util.Properties
import java.util.regex.Matcher
import java.util.regex.Pattern

val localProp: Properties = Properties().apply{
    try {
        load(FileInputStream(File(rootDir, "local.properties")))
    } catch (exception: Exception) {
        println(exception)
    }
}


/**
 * Overrides property from gradle.properties with same prop in local.properties if present
 */
fun overrideProperty(name: String) : String {
    val value = localProp.getOrElse(name){
        providers.gradleProperty(name).orNull
    }?.toString() ?: throw Exception("Property not found: $name")
    return value
}

/**
 * Overrides property from gradle.properties with same prop in local.properties if present,
 * or otherwise gives a default value
 */
fun overridePropertyDefault(name: String, default: String) : String {
    val value = localProp.getOrElse(name){
        providers.gradleProperty(name).orNull
    }?.toString() ?: default
    return value
}


/**
 * Get the current build flavor.
 */
fun getCurrentFlavor(): String {
    val taskRequests = getGradle().startParameter.taskRequests.toString()
    println("taskRequests: $taskRequests")
    val pattern =
        if (taskRequests.contains("assemble")) {
            // From e.g. `./gradlew assembleRelease` to build an APK
            Pattern.compile("assemble(\\w+)(Release|Debug)")
        }
        else if (taskRequests.contains("bundle")) {
            // From e.g. `./gradlew bundleRelease` to build an AAB
            Pattern.compile("bundle(\\w+)(Release|Debug)")
        }
        else {
            Pattern.compile("generate(\\w+)(Release|Debug)")
        }

    val matcher = pattern.matcher(taskRequests)

    return if (matcher.find()) {
        val flavor = matcher.group(1).lowercase()
        println("Build flavor: $flavor")
        flavor
    }
    else {
        // Could not find flavor (most likely not a build task, this is normal)
        ""
    }
}


android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val buildFlavor = getCurrentFlavor()
        buildConfigField("String", "FLAVOR", "\"$buildFlavor\"")
        val languages = overrideProperty("ekirjasto.languages")
        buildConfigField("String", "LANGUAGES", "\"$languages\"")
    }
}

dependencies {
    //implementation(project(":simplified-webview"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.datastore.android)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.junreachable)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.jakewharton.processphoenix)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.palace.theme)
    implementation(libs.picasso)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava2)
    implementation(libs.rxjava2.extensions)
    implementation(libs.slf4j)
}
