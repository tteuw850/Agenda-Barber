import java.net.URI

// Release credentials and the production endpoint are supplied only by the release machine.
val prodApiUrl = System.getenv("AB_PROD_API_URL")
val prodApiHost = prodApiUrl?.let { runCatching { URI(it).host }.getOrNull() }
val releaseKeyStore = System.getenv("AB_RELEASE_KEYSTORE")
val releaseStorePassword = System.getenv("AB_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("AB_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("AB_RELEASE_KEY_PASSWORD")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}
android {
    namespace = "com.agendabarber.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agendabarber.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"https://18-216-115-216.sslip.io/\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("production") {
            if (!releaseKeyStore.isNullOrBlank() &&
                !releaseStorePassword.isNullOrBlank() &&
                !releaseKeyAlias.isNullOrBlank() &&
                !releaseKeyPassword.isNullOrBlank()
            ) {
                storeFile = file(releaseKeyStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".qa" }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("production")
            if (!prodApiUrl.isNullOrBlank()) {
                buildConfigField("String", "API_BASE_URL", "\"" + prodApiUrl + "\"")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(
            prodApiUrl?.startsWith("https://") == true &&
                !prodApiHost.isNullOrBlank() &&
                prodApiHost != "18-216-115-216.sslip.io"
        ) {
            "A separate HTTPS production API endpoint is required to build release."
        }
        require(
            !releaseKeyStore.isNullOrBlank() &&
                !releaseStorePassword.isNullOrBlank() &&
                !releaseKeyAlias.isNullOrBlank() &&
                !releaseKeyPassword.isNullOrBlank()
        ) {
            "Release signing credentials must be supplied securely."
        }
        require(file(releaseKeyStore).isFile) { "Release keystore file is missing." }
    }
}
