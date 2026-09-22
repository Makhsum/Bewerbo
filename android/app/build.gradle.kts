plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

        // 10.0.2.2 is the host machine as seen from the emulator. A device on the same network
        // takes the host's LAN address instead; both are overridden here rather than in code.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5099\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // The emulator and a dev host talk plain HTTP. Only the debug build may.
            isDebuggable = true
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

// EmojiFreeStringsTest reads strings.xml off disk rather than through R, so Gradle has no idea
// those files are an input to the test task. Without this it reports the test UP-TO-DATE and skips
// it whenever nothing else changed — which is exactly the commit that adds an emoji and nothing
// else. Declaring the inputs is what makes the rule an actual gate rather than a decoration.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("src/main/res") {
            include("values*/strings.xml")
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
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
