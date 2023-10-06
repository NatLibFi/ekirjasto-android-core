import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Properties

fun calculateVersionCode(): Int {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val nowSeconds = now.toEpochSecond(ZoneOffset.UTC)
    // Seconds since 2021-03-15 09:20:00 UTC
    return (nowSeconds - 1615800000).toInt()
}

//apply(plugin = "com.google.gms.google-services")
//apply(plugin = "com.google.firebase.crashlytics")

/*
 * The asset files that are required to be present in order to build the app.
 */

//val palaceAssetsRequired = Properties()

/*
 * The various DRM schemes require that some extra assets be present.
 */

//val adobeDRM =
//    project.findProperty("org.thepalaceproject.adobeDRM.enabled") == "true"
//val lcpDRM =
//    project.findProperty("org.thepalaceproject.lcp.enabled") == "true"
//val findawayDRM =
//    project.findProperty("org.thepalaceproject.findaway.enabled") == "true"
//val overdriveDRM =
//    project.findProperty("org.thepalaceproject.overdrive.enabled") == "true"

//if (adobeDRM) {
//    palaceAssetsRequired.setProperty(
//        "assets/ReaderClientCert.sig",
//        "b064e68b96e258e42fe1ca66ae3fc4863dd802c46585462220907ed291e1217d",
//    )
//}
//
//if (adobeDRM || lcpDRM || findawayDRM || overdriveDRM) {
//    palaceAssetsRequired.setProperty(
//        "assets/secrets.conf",
//        "221db5c8c1ce1ddbc4f4c1a017f5b63271518d2adf6991010c2831a58b7f88ed",
//    )
//}

//val palaceAssetsDirectory =
//    project.findProperty("org.thepalaceproject.app.assets.palace") as String?
//
//if (palaceAssetsDirectory != null) {
//    val directory = File(palaceAssetsDirectory)
//    if (!directory.isDirectory) {
//        throw GradleException("The directory specified by org.thepalaceproject.app.assets.palace does not exist.")
//    }
//}

/*
 * A task that writes the required assets to a file in order to be used later by ZipCheck.
 */

//fun createRequiredAssetsFile(file: File): Task {
//    return task("CheckReleaseRequiredAssetsCreate") {
//        doLast {
//            file.writer().use {
//                palaceAssetsRequired.store(it, "")
//            }
//        }
//    }
//}

/*
 * A task that executes ZipCheck against a given APK file and a list of required assets.
 */

//fun createRequiredAssetsTask(
//    checkFile: File,
//    assetList: File,
//): Task {
//    return task("CheckReleaseRequiredAssets_${checkFile.name}", Exec::class) {
//        commandLine = arrayListOf(
//            "java",
//            "$rootDir/org.thepalaceproject.android.platform/ZipCheck.java",
//            "$checkFile",
//            "$assetList",
//        )
//    }
//}

/*
 * The signing information that is required to exist for release builds.
 */

//val palaceKeyStore =
//    File("$rootDir/release.jks")
//val palaceKeyAlias =
//    project.findProperty("org.thepalaceproject.keyAlias") as String?
//val palaceKeyPassword =
//    project.findProperty("org.thepalaceproject.keyPassword") as String?
//val palaceStorePassword =
//    project.findProperty("org.thepalaceproject.storePassword") as String?

//val requiredSigningTask = task("CheckReleaseSigningInformation") {
//    if (palaceKeyAlias == null) {
//        throw GradleException("org.thepalaceproject.keyAlias is not specified.")
//    }
//    if (palaceKeyPassword == null) {
//        throw GradleException("org.thepalaceproject.keyPassword is not specified.")
//    }
//    if (palaceStorePassword == null) {
//        throw GradleException("org.thepalaceproject.storePassword is not specified.")
//    }
//}

android {
    defaultConfig {
        versionCode = calculateVersionCode()
        resourceConfigurations.add("en")
        resourceConfigurations.add("es")
        setProperty("archivesBaseName", "Ellibs-Ekirjasto")
    }

    /*
     * Add the assets directory to the source sets. This is required for the various
     * secret files.
     */

//    sourceSets {
//        findByName("main")?.apply {
//            if (palaceAssetsDirectory != null) {
//                assets {
//                    srcDir(palaceAssetsDirectory)
//                }
//            }
//        }
//    }

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
        create("release") {
//            keyAlias = palaceKeyAlias
//            keyPassword = palaceKeyPassword
//            storeFile = palaceKeyStore
//            storePassword = palaceStorePassword
        }
    }

    /*
     * Ensure that the right NDK ABIs are declared.
     */

    buildTypes {
        debug {
            ndk {
                abiFilters.add("x86")
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
        }
    }

    /*
     * Release builds need extra checking.
     */

//    applicationVariants.all {
//        if (this.buildType.name == "release") {
//            val preBuildTask = tasks.findByName("preReleaseBuild")
//            preBuildTask?.dependsOn?.add(requiredSigningTask)
//
//            /*
//             * For each APK output, create a task that checks that the APK contains the
//             * required assets.
//             */
//
//            this.outputs.forEach {
//                val outputFile = it.outputFile
//                val assetFile = File("${project.buildDir}/required-assets.conf")
//                val fileTask =
//                    createRequiredAssetsFile(assetFile)
//                val checkTask =
//                    createRequiredAssetsTask(checkFile = outputFile, assetList = assetFile)
//
//                checkTask.dependsOn.add(fileTask)
//                this.assembleProvider.configure {
//                    finalizedBy(checkTask)
//                }
//            }
//        }
//    }
}

/*
 * Produce an AAB file whenever someone asks for "assemble".
 */

afterEvaluate {
    tasks.findByName("assemble")
        ?.dependsOn?.add(tasks.findByName("bundle"))
}

repositories {
    ivy {
        url("https://liblcp.dita.digital")
        patternLayout{
            artifact("/[organisation]/[module]/android/aar/test/[revision].[ext]")
        }
        metadataSources { artifact()}
    }
}

dependencies {
    implementation(project(":simplified-main"))
    implementation(project(":simplified-accounts-source-nyplregistry"))
    implementation(project(":simplified-analytics-circulation"))

    /**
     *  ekirjasto added dependencies
     */


    implementation("readium:liblcp:1.0.0@aar")
    implementation(libs.nypl.theme)
}
