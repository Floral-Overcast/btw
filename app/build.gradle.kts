plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.floralovercast.btw"
    compileSdk = 34
    // Builds libtermux.so (PTY JNI) from the vendored Termux terminal-emulator
    // jni; see app/src/main/jni and third_party/termux/README.md.
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.floralovercast.btw"
        minSdk = 28
        // targetSdk 28 is deliberate: targeting 29+ blocks exec() of
        // downloaded binaries from app data. See CLAUDE.md / architecture.md.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-alpha1"
        ndk {
            // Single ABI: the bundled proot is aarch64-only (jniLibs).
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    buildTypes {
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
    }

    packaging {
        // extractNativeLibs=true (manifest) needs legacy packaging: proot
        // must be unpacked to nativeLibraryDir on install to be exec'able.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
