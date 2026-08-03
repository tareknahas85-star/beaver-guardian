plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.microbeaver.guardian"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.microbeaver.guardian"
        minSdk = 24
        targetSdk = 34
        // Bump versionCode on every release, otherwise Android may refuse to
        // install over an existing copy and you end up testing the old build.
        // versionName is what the About screen prints — keep it in step so the
        // installed version is verifiable at a glance.
        versionCode = 21
        versionName = "8.7"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        // AGP 8 no longer generates BuildConfig unless asked.
        // AboutActivity reads BuildConfig.VERSION_NAME, so this must stay on.
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firebase (config comes from app/google-services.json — real project: beaver-guardian)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // E-Signature pad (requires Jitpack repo — see settings.gradle.kts)
    implementation("com.github.gcacace:signature-pad:1.3.1")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
