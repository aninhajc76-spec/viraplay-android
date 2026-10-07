plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vpVersionCode = providers.gradleProperty("viraplayVersionCode").orNull?.toIntOrNull() ?: 51
val vpVersionName = providers.gradleProperty("viraplayVersionName").orNull ?: "3.3.18"

android {
    namespace = "com.viraplay.admin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.viraplay.admin"
        minSdk = 23
        targetSdk = 36
        versionCode = vpVersionCode
        versionName = vpVersionName
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val signingPath = System.getenv("VIRAPLAY_KEYSTORE_PATH")
            if (!signingPath.isNullOrBlank()) {
                storeFile = file(signingPath)
                storePassword = System.getenv("VIRAPLAY_STORE_PASSWORD")
                keyAlias = System.getenv("VIRAPLAY_KEY_ALIAS")
                keyPassword = System.getenv("VIRAPLAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val signingPath = System.getenv("VIRAPLAY_KEYSTORE_PATH")
            if (!signingPath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
