plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.jbecker.tractivewatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.jbecker.tractivewatch"
        minSdk = 30
        targetSdk = 35
        versionCode = 14
        versionName = "1.13"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    // v1.12: Echte Wear-OS-UI-Bibliothek (keine Phone-Material3 mehr).
    // 1.6.2 = kompatibel mit AGP 8.7.3 + compileSdk 35 + Kotlin 2.1.0.
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-navigation:1.6.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    debugImplementation("androidx.wear.compose:compose-ui-tooling:1.6.2")
    // Ongoing Activity: Live-Tracking auf der Uhr + One-Tap zurück zur Karte
    implementation("androidx.wear:wear-ongoing:1.1.0")
    // Core-Icon-Satz (Refresh, Settings, ExitToApp, Warning, Check, Person, Info)
    implementation("androidx.compose.material:material-icons-core:1.7.5")
    implementation("androidx.compose.animation:animation:1.7.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.moshi:moshi:1.15.2")
    // Kotlin-Adapter für Moshi (Reflexion) — OHNE diesen wirft toJson()
    // 'Cannot serialize Kotlin type ...' für unsere Data-Classes (Login-Fehler v1.9)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    testImplementation("junit:junit:4.13.2")
    // Robolectric-Start-Test: rendert die echte UI auf der JVM (v1.12 zeigte
    // nur einen schwarzen Screen, ohne dass ein Test das bemerkt hätte)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
}
