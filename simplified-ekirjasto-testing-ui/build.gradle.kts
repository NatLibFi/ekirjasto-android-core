import java.io.FileInputStream
import java.util.Properties


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

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        var testLoginEnabled = overrideProperty("ekirjasto.testLogin.enabled")
        buildConfigField("Boolean", "TEST_LOGIN_ENABLED", testLoginEnabled)
        val testLoginUsername = overridePropertyDefault("ekirjasto.testLogin.username", "")
        buildConfigField("String", "TEST_LOGIN_USERNAME", "\"$testLoginUsername\"")
        val testLoginPinCode = overridePropertyDefault("ekirjasto.testLogin.pinCode", "")
        buildConfigField("String", "TEST_LOGIN_PIN_CODE", "\"$testLoginPinCode\"")
    }
}

dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-adobe-extensions"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-android-ktx"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-borrowing"))
    implementation(project(":simplified-books-controller-api"))
    implementation(project(":simplified-books-covers"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-ekirjasto-testing"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-futures"))
    implementation(project(":simplified-mdc"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-reader-api"))
    implementation(project(":simplified-services-api"))
    implementation(project(":simplified-taskrecorder-api"))
    implementation(project(":simplified-ui-accounts"))
    implementation(project(":simplified-ui-errorpage"))
    implementation(project(":simplified-ui-images"))
    implementation(project(":simplified-ui-listeners-api"))
    implementation(project(":simplified-ui-screen"))
    implementation(project(":simplified-ui-thread-api"))
    implementation(project(":simplified-webview"))

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
