plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.wsp3_1.stundenplan"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "de.wsp3_1.stundenplan"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2"
    }
    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false // bewusst aus: keine ProGuard-Ueberraschungen im Projekt
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true } // kein buildConfig: Vorgabe-URL ist eine Konstante (Defaults.kt)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint {
        // Versionen sind bewusst festgezurrt (README "Versionen"): Compose-BOM 2026.06.01 ist die letzte, die zu
        // AGP 8 passt; OkHttp 4.12 wegen MockWebServer; AGP 9 / Kotlin 2.4 nur als kompletter Wechsel.
        // Hinweise auf neuere Versionen sind deshalb keine Befunde.
        disable += listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
        // ObsoleteSdkInt raet, mipmap-anydpi-v26 nach mipmap-anydpi zu verschieben (minSdk 30). Gemessen 05.09.:
        // ohne den v26-Qualifier findet aapt2 die adaptiven Icons nicht ("resource mipmap/ic_launcher not found").
        disable += "ObsoleteSdkInt"
        warningsAsErrors = false
        abortOnError = true
    }
    testOptions {
        // JVM-Tests laufen ohne Geraet; Android-Klassen ohne Implementierung liefern Standardwerte statt zu werfen.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    // Kein HttpLoggingInterceptor: er stand ungenutzt im Debug-APK. Wer ihn zur Fehlersuche
    // einhaengt, schreibt mit Level HEADERS/BODY das Token ins Logcat - dann nur mit
    // redactHeader("Authorization") und bewusst dokumentiert.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
