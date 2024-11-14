import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.Properties

fun calculateVersionCode(): Int {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val nowSeconds = now.toEpochSecond(ZoneOffset.UTC)
    // Seconds since 2021-03-15 09:20:00 UTC
    val versionCodeBeforeModuloCutoff = (nowSeconds - 1615800000).toInt()
    // Round down to the nearest 10
    val versionCode = versionCodeBeforeModuloCutoff - versionCodeBeforeModuloCutoff % 10
    return versionCode
}

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


fun getVersionName(): String {
    return overrideProperty("ekirjasto.versionName")
}


apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")

/*
 * The asset files that are required to be present in order to build the app.
 */

val palaceAssetsRequired = Properties()

/*
 * The various DRM schemes require that some extra assets be present.
 */

val adobeDRM =
    project.findProperty("org.thepalaceproject.adobeDRM.enabled") == "true"
val lcpDRM =
    project.findProperty("org.thepalaceproject.lcp.enabled") == "true"
val findawayDRM =
    project.findProperty("org.thepalaceproject.findaway.enabled") == "true"
val overdriveDRM =
    project.findProperty("org.thepalaceproject.overdrive.enabled") == "true"

if (adobeDRM) {
    palaceAssetsRequired.setProperty(
        "assets/ReaderClientCert.sig",
        "b064e68b96e258e42fe1ca66ae3fc4863dd802c46585462220907ed291e1217d",
    )
}

if (adobeDRM || lcpDRM || findawayDRM || overdriveDRM) {
    palaceAssetsRequired.setProperty(
        "assets/secrets.conf",
        "221db5c8c1ce1ddbc4f4c1a017f5b63271518d2adf6991010c2831a58b7f88ed",
    )
}

val palaceAssetsDirectory =
    project.findProperty("org.thepalaceproject.app.assets.palace") as String?

if (palaceAssetsDirectory != null) {
    val directory = File(palaceAssetsDirectory)
    if (!directory.isDirectory) {
        throw GradleException("The directory specified by org.thepalaceproject.app.assets.palace does not exist.")
    }
}

/*
 * A task that writes the required assets to a file in order to be used later by ZipCheck.
 */

fun createRequiredAssetsFile(file: File, flavorName: String): Task {
    return task("CheckReleaseRequiredAssetsCreate_${flavorName}") {
        doLast {
            file.writer().use {
                palaceAssetsRequired.store(it, "")
            }
        }
    }
}

/*
 * A task that executes ZipCheck against a given APK file and a list of required assets.
 */

fun createRequiredAssetsTask(
    checkFile: File,
    assetList: File,
): Task {
    return task("CheckReleaseRequiredAssets_${checkFile.name}", Exec::class) {
        commandLine = arrayListOf(
            "java",
            "$rootDir/org.thepalaceproject.android.platform/ZipCheck.java",
            "$checkFile",
            "$assetList",
        )
    }
}

/*
 * The signing information that is required to exist for release builds.
 */

val releaseKeystore =
    File("$rootDir/release.jks")
val releaseKeyAlias =
    overridePropertyDefault("ekirjasto.keyAlias", "")
val releaseKeyPassword =
    overridePropertyDefault("ekirjasto.keyPassword", "")
val releaseStorePassword =
    overridePropertyDefault("ekirjasto.storePassword", "")

val requiredSigningTask = task("CheckReleaseSigningInformation") {
    if (releaseKeyAlias == "") {
        throw GradleException("ekirjasto.keyAlias is not specified.")
    }
    if (releaseKeyPassword == "") {
        throw GradleException("ekirjasto.keyPassword is not specified.")
    }
    if (releaseStorePassword == "") {
        throw GradleException("ekirjasto.storePassword is not specified.")
    }
}

val versionCodeBase = calculateVersionCode()

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "fi.kansalliskirjasto.ekirjasto"
        versionName = getVersionName()
        versionCode = versionCodeBase
        val feedbackUrlBase = overrideProperty("ekirjasto.feedbackUrlBase")
        buildConfigField("String", "FEEDBACK_URL_BASE", "\"$feedbackUrlBase\"")
        val languages = overrideProperty("ekirjasto.languages")
        println("Configured languages: $languages")
        resourceConfigurations += languages.split(",")
        setProperty("archivesBaseName", "ekirjasto")
        val supportEmailBase64 = overrideProperty("ekirjasto.supportEmailBase64")
        val supportEmail = Base64.getDecoder().decode(supportEmailBase64.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
        println("Support email: $supportEmail")
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")
    }

    /*
     * Add the assets directory to the source sets. This is required for the various
     * secret files.
     */

    sourceSets {
        findByName("main")?.apply {
            if (palaceAssetsDirectory != null) {
                assets {
                    srcDir(palaceAssetsDirectory)
                }
            }
        }
    }

    flavorDimensions += "environment"

    // Product flavors: environments from least stable to most stable
    productFlavors {
        create("localhost") {
            versionCode = versionCodeBase + 4
            // Set as default flavor, otherwise alphabetically first will be the default
            isDefault = true
            val circURL = "http://1.2.3.4:6500" // remember to include port, if necessary
            buildConfigField("String", "CIRCULATION_API_URL", "\"$circURL\"")
            val libProvider = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" // format: 8-4-4-4-12
            buildConfigField("String", "LIBRARY_PROVIDER_ID", "\"$libProvider\"")
        }
        create("dev") {
            versionCode = versionCodeBase + 3
            val circURL = "https://lib-dev.e-kirjasto.fi"
            buildConfigField("String", "CIRCULATION_API_URL", "\"$circURL\"")
            val libProvider = "28bed937-a16b-4d69-a9c8-4b2656333423"
            buildConfigField("String", "LIBRARY_PROVIDER_ID", "\"$libProvider\"")
        }
        create("beta") {
            versionCode = versionCodeBase + 2
            val circURL = "https://lib-beta.e-kirjasto.fi"
            buildConfigField("String", "CIRCULATION_API_URL", "\"$circURL\"")
            val libProvider = "37015541-b542-4157-a687-3ca5ad47fdbe"
            buildConfigField("String", "LIBRARY_PROVIDER_ID", "\"$libProvider\"")
        }
        create("production") {
            versionCode = versionCodeBase + 1
            val circURL = "https://lib.e-kirjasto.fi"
            buildConfigField("String", "CIRCULATION_API_URL", "\"$circURL\"")
            val libProvider = "8b7292e9-ed77-480e-a695-423f715be0f2"
            buildConfigField("String", "LIBRARY_PROVIDER_ID", "\"$libProvider\"")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("lib/**/*.so")

            /*
             * Various components (R2, the PDF library, LCP, etc) include this shared library.
             */

            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
            pickFirsts.add("lib/x86/libc++_shared.so")
            pickFirsts.add("lib/x86_64/libc++_shared.so")
        }
    }

    /*
     * Ensure that release builds are signed.
     */

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = releaseKeystore
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    /*
     * Ensure that the right NDK ABIs are declared.
     */

    buildTypes {
        debug {
            ndk {
                abiFilters.add("x86")
                abiFilters.add("x86_64")
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
            versionNameSuffix = "-debug"
        }
        release {
            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
            this.signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                // Includes the default ProGuard rules files that are packaged with
                // the Android Gradle plugin. To learn more, go to the section about
                // R8 configuration files.
                getDefaultProguardFile("proguard-android-optimize.txt"),

                // Includes a local, custom Proguard rules file
                "proguard-rules.pro"
            )
        }
    }

    /*
     * Release builds need extra checking.
     */

    applicationVariants.all {
        if (this.buildType.name == "release") {
            val preBuildTask = tasks.findByName("preReleaseBuild")
            preBuildTask?.dependsOn?.add(requiredSigningTask)

            /*
             * For each APK output, create a task that checks that the APK contains the
             * required assets.
             */

            this.outputs.forEach {
                val outputFile = it.outputFile
                val assetFile = File("${project.projectDir}/build/required-assets.conf")
                val fileTask =
                    createRequiredAssetsFile(assetFile, this.flavorName)
                val checkTask =
                    createRequiredAssetsTask(checkFile = outputFile, assetList = assetFile)

                checkTask.dependsOn.add(fileTask)
                this.assembleProvider.configure {
                    finalizedBy(checkTask)
                }
            }
        }
    }
}

/*
 * Produce an AAB file whenever someone asks for "assemble".
 */

afterEvaluate {
    tasks.findByName("assemble")
        ?.dependsOn?.add(tasks.findByName("bundle"))
}

dependencies {
    implementation(project(":simplified-accessibility"))
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-accounts-json"))
    implementation(project(":simplified-accounts-registry"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-adobe-extensions"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-analytics-circulation"))
    implementation(project(":simplified-android-ktx"))
    implementation(project(":simplified-announcements"))
    implementation(project(":simplified-bookmarks"))
    implementation(project(":simplified-bookmarks-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-audio"))
    implementation(project(":simplified-books-borrowing"))
    implementation(project(":simplified-books-bundled-api"))
    implementation(project(":simplified-books-controller"))
    implementation(project(":simplified-books-controller-api"))
    implementation(project(":simplified-books-covers"))
    implementation(project(":simplified-books-database"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-preview"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-books-time-tracking"))
    implementation(project(":simplified-boot-api"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-content-api"))
    implementation(project(":simplified-crashlytics"))
    implementation(project(":simplified-crashlytics-api"))
    implementation(project(":simplified-deeplinks-controller-api"))
    implementation(project(":simplified-documents"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-futures"))
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-lcp"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-links-json"))
    implementation(project(":simplified-main"))
    implementation(project(":simplified-mdc"))
    implementation(project(":simplified-metrics"))
    implementation(project(":simplified-metrics-api"))
    implementation(project(":simplified-migration-api"))
    implementation(project(":simplified-migration-spi"))
    implementation(project(":simplified-networkconnectivity"))
    implementation(project(":simplified-networkconnectivity-api"))
    implementation(project(":simplified-notifications"))
    implementation(project(":simplified-oauth"))
    implementation(project(":simplified-opds-auth-document"))
    implementation(project(":simplified-opds-auth-document-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-opds2"))
    implementation(project(":simplified-opds2-irradia"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-opds2-r2"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-patron"))
    implementation(project(":simplified-patron-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-profiles"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-reader-api"))
    implementation(project(":simplified-reports"))
    implementation(project(":simplified-services-api"))
    implementation(project(":simplified-taskrecorder-api"))
    implementation(project(":simplified-tenprint"))
    implementation(project(":simplified-threads"))
    implementation(project(":simplified-ui-accounts"))
    implementation(project(":simplified-ui-announcements"))
    implementation(project(":simplified-ui-branding"))
    implementation(project(":simplified-ui-catalog"))
    implementation(project(":simplified-ui-errorpage"))
    implementation(project(":simplified-ui-images"))
    implementation(project(":simplified-ui-listeners-api"))
    implementation(project(":simplified-ekirjasto-magazines"))
    implementation(project(":simplified-ekirjasto-testing"))
    implementation(project(":simplified-ekirjasto-testing-ui"))
    implementation(project(":simplified-ekirjasto-util"))
    implementation(project(":simplified-ui-navigation-tabs"))
    //implementation(project(":simplified-ui-neutrality"))
    implementation(project(":simplified-ui-onboarding"))
    implementation(project(":simplified-ui-screen"))
    implementation(project(":simplified-ui-settings"))
    implementation(project(":simplified-ui-splash"))
    implementation(project(":simplified-ui-thread-api"))
    implementation(project(":simplified-ui-tutorial"))
    implementation(project(":simplified-viewer-api"))
    implementation(project(":simplified-viewer-audiobook"))
    implementation(project(":simplified-viewer-epub-readium2"))
    implementation(project(":simplified-viewer-pdf-pdfjs"))
    implementation(project(":simplified-viewer-preview"))
    implementation(project(":simplified-viewer-spi"))
    implementation(project(":simplified-webview"))
    implementation(project(":simplified-accounts-source-ekirjasto"))

    /*
     * Dependencies conditional upon Adobe DRM support.
     */

    if (adobeDRM) {
        implementation(libs.palace.drm.adobe)
    }

    /*
     * Dependencies conditional upon LCP support.
     */

    if (lcpDRM) {
        implementation(libs.readium.lcp) {
            artifact {
                type = "aar"
            }
        }
    }

    /*
     * Dependencies conditional upon Findaway support.
     */

    if (findawayDRM) {
        implementation(libs.dagger)
        implementation(libs.exoplayer2.core)
        implementation(libs.findaway)
        implementation(libs.findaway.common)
        implementation(libs.findaway.listening)
        implementation(libs.findaway.persistence)
        implementation(libs.findaway.play.android)
        implementation(libs.google.gson)
        implementation(libs.javax.inject)
        implementation(libs.koin.android)
        implementation(libs.koin.core)
        implementation(libs.koin.core.jvm)
        implementation(libs.moshi)
        implementation(libs.moshi.adapters)
        implementation(libs.moshi.kotlin)
        implementation(libs.okhttp3)
        implementation(libs.okhttp3.logging.interceptor)
        implementation(libs.palace.findaway)
        implementation(libs.retrofit2)
        implementation(libs.retrofit2.adapter.rxjava)
        implementation(libs.retrofit2.converter.gson)
        implementation(libs.retrofit2.converter.moshi)
        implementation(libs.rxandroid)
        implementation(libs.rxrelay)
        implementation(libs.sqlbrite)
        implementation(libs.stately.common)
        implementation(libs.stately.concurrency)
        implementation(libs.timber)
    }

    /*
     * Dependencies conditional upon Overdrive support.
     */

    if (overdriveDRM) {
        implementation(libs.palace.overdrive)
    }

    /*
     * Dependencies needed for Feedbooks JWT handling. Always enabled.
     */

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.net.minidev.json.smart)
    implementation(libs.net.minidev.accessors.smart)

    // Transifex
    implementation(libs.transifex.common)
    implementation(libs.transifex.sdk)
    implementation(libs.b3nedikt.viewpump)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.arch.core.common)
    implementation(libs.androidx.arch.core.runtime)
    implementation(libs.androidx.asynclayoutinflater)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.constraintlayout.solver)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials.playauth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.cursoradapter)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.datastore.android)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.legacy.support.core.ui)
    implementation(libs.androidx.legacy.support.core.utils)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.loader)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.print)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.transition.ktx)
    implementation(libs.androidx.vectordrawable)
    implementation(libs.androidx.vectordrawable.animated)
    implementation(libs.androidx.versionedparcelable)
    implementation(libs.androidx.viewbinding)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.azam.ulidj)
    implementation(libs.commons.compress)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.annotations)
    implementation(libs.firebase.common)
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.components)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.datatransport)
    implementation(libs.firebase.dynamic.links)
    implementation(libs.firebase.dynamic.links.ktx)
    implementation(libs.firebase.encoders)
    implementation(libs.firebase.encoders.json)
    implementation(libs.firebase.encoders.proto)
    implementation(libs.firebase.iid.interop)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.installations.interop)
    implementation(libs.firebase.measurement.connector)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.sessions)
    implementation(libs.google.failureaccess)
    implementation(libs.google.gson)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.irradia.opds2.api)
    implementation(libs.irradia.opds2.lexical)
    implementation(libs.irradia.opds2.librarysimplified)
    implementation(libs.irradia.opds2.parser.api)
    implementation(libs.irradia.opds2.parser.extension.spi)
    implementation(libs.irradia.opds2.parser.librarysimplified)
    implementation(libs.irradia.opds2.parser.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.annotations)
    implementation(libs.jakewharton.processphoenix)
    implementation(libs.javax.inject)
    implementation(libs.joda.time)
    implementation(libs.jsoup)
    implementation(libs.koi.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.android)
    implementation(libs.media3.common)
    implementation(libs.media3.container)
    implementation(libs.media3.datasource)
    implementation(libs.media3.decoder)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.extractor)
    implementation(libs.media3.session)
    implementation(libs.moznion.uribuildertiny)
    implementation(libs.nypl.readium)
    implementation(libs.okhttp3)
    implementation(libs.okio)
    implementation(libs.palace.audiobook.api)
    implementation(libs.palace.audiobook.downloads)
    implementation(libs.palace.audiobook.feedbooks)
    implementation(libs.palace.audiobook.http)
    implementation(libs.palace.audiobook.json.canon)
    implementation(libs.palace.audiobook.json.web.token)
    implementation(libs.palace.audiobook.lcp)
    implementation(libs.palace.audiobook.lcp.license.status)
    implementation(libs.palace.audiobook.license.check.api)
    implementation(libs.palace.audiobook.license.check.spi)
    implementation(libs.palace.audiobook.manifest.api)
    implementation(libs.palace.audiobook.manifest.fulfill.api)
    implementation(libs.palace.audiobook.manifest.fulfill.basic)
    implementation(libs.palace.audiobook.manifest.fulfill.opa)
    implementation(libs.palace.audiobook.manifest.fulfill.spi)
    implementation(libs.palace.audiobook.manifest.parser.api)
    implementation(libs.palace.audiobook.manifest.parser.extension.spi)
    implementation(libs.palace.audiobook.manifest.parser.webpub)
    implementation(libs.palace.audiobook.open.access)
    implementation(libs.palace.audiobook.parser.api)
    implementation(libs.palace.audiobook.rbdigital)
    implementation(libs.palace.audiobook.views)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.bearer.token)
    implementation(libs.palace.http.downloads)
    implementation(libs.palace.http.refresh.token)
    implementation(libs.palace.http.uri)
    implementation(libs.palace.http.vanilla)
    implementation(libs.palace.readium2.api)
    implementation(libs.palace.readium2.ui.thread)
    implementation(libs.palace.readium2.vanilla)
    implementation(libs.palace.readium2.views)
    implementation(libs.palace.theme)
    implementation(libs.pandora.bottom.navigator)
    implementation(libs.pdfium.android)
    implementation(libs.picasso)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
    implementation(libs.play.services.cloud.messaging)
    implementation(libs.play.services.location)
    implementation(libs.play.services.measurement)
    implementation(libs.play.services.measurement.api)
    implementation(libs.play.services.measurement.base)
    implementation(libs.play.services.measurement.impl)
    implementation(libs.play.services.measurement.sdk)
    implementation(libs.play.services.measurement.sdk.api)
    implementation(libs.play.services.stats)
    implementation(libs.play.services.tasks)
    implementation(libs.r2.lcp)
    implementation(libs.r2.opds)
    implementation(libs.r2.shared)
    implementation(libs.r2.streamer)
    implementation(libs.reactive.streams)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava)
    implementation(libs.rxjava2)
    implementation(libs.rxjava2.extensions)
    implementation(libs.service.wight.annotation)
    implementation(libs.service.wight.core)
    implementation(libs.slf4j)
    implementation(libs.timber)
    implementation(libs.transport.api)
    implementation(libs.transport.backend.cct)
    implementation(libs.transport.runtime)
    implementation(libs.truecommons.cio)
    implementation(libs.truecommons.io)
    implementation(libs.truecommons.key.disable)
    implementation(libs.truecommons.key.spec)
    implementation(libs.truecommons.logging)
    implementation(libs.truecommons.services)
    implementation(libs.truecommons.shed)
    implementation(libs.truevfs.access)
    implementation(libs.truevfs.comp.zip)
    implementation(libs.truevfs.comp.zipdriver)
    implementation(libs.truevfs.driver.file)
    implementation(libs.truevfs.driver.zip)
    implementation(libs.truevfs.kernel.impl)
    implementation(libs.truevfs.kernel.spec)

    val libLcpRepositoryLayout = overrideProperty("ekirjasto.liblcp.repositorylayout")
    if (libLcpRepositoryLayout.contains("test")) {
        println("Using test liblcp AAR")
        implementation("readium:liblcp:1.0.0@aar")
    }
    else {
        println("Using production liblcp AAR")
        implementation("readium:liblcp:2.1.0@aar")
    }

    /** For missing passkey libraries **/
    implementation(libs.android.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.fido)
}
