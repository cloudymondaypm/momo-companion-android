plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional persistent signer for repeatable APK upgrades. If not configured,
// local builds and pull-request previews continue using Android's debug signer.
val watchStorePath = System.getenv("MOMO_WATCH_KEYSTORE_PATH")
val watchStorePassword = System.getenv("MOMO_WATCH_KEYSTORE_PASSWORD")
val watchKeyAlias = System.getenv("MOMO_WATCH_KEY_ALIAS")
val watchKeyPassword = System.getenv("MOMO_WATCH_KEY_PASSWORD")
val hasStableWatchSigner = listOf(watchStorePath, watchStorePassword,
    watchKeyAlias, watchKeyPassword).all { !it.isNullOrBlank() }
if (hasStableWatchSigner && !file(watchStorePath!!).isFile) {
    throw GradleException("Configured watch signing file does not exist")
}

android {
    namespace = "com.xiaozhi.simple"
    compileSdk = 34
    ndkVersion = "25.1.8937393"

    defaultConfig {
        applicationId = "com.kiumo.xiaozhi"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "0.4.4-momo-companion"

        vectorDrawables { useSupportLibrary = true }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasStableWatchSigner) {
            create("watchStable") {
                storeFile = file(watchStorePath!!)
                storePassword = watchStorePassword!!
                keyAlias = watchKeyAlias!!
                keyPassword = watchKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableWatchSigner) {
                signingConfig = signingConfigs.getByName("watchStable")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasStableWatchSigner) signingConfigs.getByName("watchStable")
                else signingConfigs.getByName("debug")
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

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        prefab = true
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.oboe:oboe:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.12.2")

}
