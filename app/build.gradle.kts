import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val trefleProperties = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}
// Public service address only. The Trefle account token stays in Render's secret environment.
val sharedTrefleServer = "https://plant-app-hcc.onrender.com"
val localTrefleDemo = providers.gradleProperty("trefle.localDemo").orNull == "true"
val trefleServer = (trefleProperties.getProperty("trefle.baseUrl") ?: sharedTrefleServer).trim().trimEnd('/')
require(trefleServer.isEmpty() || trefleServer.startsWith("https://")) {
    "trefle.baseUrl must be an HTTPS server URL. The debug emulator default is configured separately."
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.plant_app_hcc"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.plant_app_hcc"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "TREFLE_BASE_URL", quoted(if (localTrefleDemo) "http://10.0.2.2:8765" else trefleServer))
        }
        release {
            buildConfigField("String", "TREFLE_BASE_URL", quoted(trefleServer))
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
