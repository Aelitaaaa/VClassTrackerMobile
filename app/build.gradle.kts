import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")

val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use {
            load(it)
        }
    }
}

val signingEnvironment = mapOf(
    "storeFile" to System.getenv("ANDROID_KEYSTORE_PATH"),
    "storePassword" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    "keyAlias" to System.getenv("ANDROID_KEY_ALIAS"),
    "keyPassword" to System.getenv("ANDROID_KEY_PASSWORD")
)
if (signingEnvironment.values.any { !it.isNullOrBlank() }) {
    require(signingEnvironment.values.all { !it.isNullOrBlank() }) {
        "Incomplete Android release signing environment; configure all four signing values."
    }
    signingEnvironment.forEach { (name, value) -> keystoreProperties.setProperty(name, value!!) }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }
if (providers.gradleProperty("requireReleaseSigning").orNull == "true") {
    require(hasReleaseSigning) { "Release signing is required; signing credentials are missing." }
}
if (hasReleaseSigning) {
    require(rootProject.file(keystoreProperties.getProperty("storeFile")).isFile) {
        "The configured release keystore file is missing."
    }
}

android {
    namespace = "com.dzaky.vclasstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dzaky.vclasstracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(
                    requireNotNull(keystoreProperties.getProperty("storeFile"))
                )
                storePassword = requireNotNull(
                    keystoreProperties.getProperty("storePassword")
                )
                keyAlias = requireNotNull(
                    keystoreProperties.getProperty("keyAlias")
                )
                keyPassword = requireNotNull(
                    keystoreProperties.getProperty("keyPassword")
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
