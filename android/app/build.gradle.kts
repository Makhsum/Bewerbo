import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The signing key of the release build, read from android/keystore.properties.
//
// Neither the key nor this file is in the repository, and neither may be: whoever holds them can
// publish an update that every phone with Bewerbo installed accepts as ours. The file names the
// keystore, so a checkout without it still builds — the release is simply left unsigned then,
// which `signingConfig` below says out loud rather than failing three tasks later.
//
// LOSING THE KEYSTORE CANNOT BE REPAIRED. Android identifies an app by its signature, so an update
// signed with a different key will not install over one already on a device; the only way out is a
// new applicationId. Back it up somewhere other than this machine.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "de.bewerbo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.bewerbo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // 10.0.2.2 is the host machine as seen from the emulator, and that address means nothing
        // on a real phone: there the backend is reached over Tailscale or over the LAN. So the URL
        // is a build input rather than a constant — an APK for a device is built with e.g.
        //   ./gradlew assembleDebug -PbewerboApiUrl=http://100.91.107.10:5099
        val apiBaseUrl = (findProperty("bewerboApiUrl") as String?) ?: "http://10.0.2.2:5099"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            val storeFileName = keystoreProperties.getProperty("storeFile")
            if (storeFileName != null) {
                storeFile = rootProject.file(storeFileName)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }

            // v1 is the signature inside META-INF that Android 6 and older need, and minSdk is 26.
            // Leaving it out costs nothing and keeps the archive smaller. v3 is what carries the
            // proof of a key rotation, so it has to be there BEFORE the key would ever be rotated —
            // added afterwards it proves nothing about the key it replaced.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Unsigned when there is no keystore.properties, so a checkout without the key still
            // produces an APK — it just cannot be installed, which is the honest outcome.
            signingConfig = if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            // The emulator and a dev host talk plain HTTP. Only the debug build may.
            isDebuggable = true
        }
    }

    // One APK per architecture instead of one carrying all four.
    //
    // The text recogniser ships a native library of about 11 MB per ABI, so a single APK with all
    // of them is ~56 MB of which the phone uses a quarter. Split, the arm64 APK a real device
    // installs is ~26 MB. That is also what makes a build small enough to hang on a card: the
    // attachment endpoint refuses anything over 50 MB.
    //
    // No universal APK: it would be the 56 MB file this exists to avoid.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// EmojiFreeStringsTest and GermanTermsTest read the resource files off disk rather than through R,
// so Gradle has no idea those files are an input to the test task. Without this it reports the test
// UP-TO-DATE and skips it whenever nothing else changed — which is exactly the commit that adds an
// emoji and nothing else. Declaring the inputs is what makes the rule an actual gate rather than a
// decoration.
//
// plurals.xml belongs here for the same reason strings.xml does: GermanTermsTest scans it now, and
// a scan Gradle can skip is not a gate. It was left out when the plurals arrived, so a German word
// could have gone into a count without the build noticing.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("src/main/res") {
            include("values*/strings.xml")
            include("values*/plurals.xml")
        },
    ).withPropertyName("userFacingStrings").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Reads the words out of a photographed or screenshotted advert, on the device. The BUNDLED
    // recogniser and not the Play-services one: it needs no Google Play on the phone and no model
    // download before the first use, which is the difference between a way in that works and one
    // that works later. It also means the advert never leaves the device to be read.
    implementation(libs.mlkit.text.recognition)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
