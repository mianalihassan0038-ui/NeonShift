plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes the run number so every build gets a higher versionCode.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()

android {
    namespace = "com.neonshift.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neonshift.game"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // A real upload key can be supplied by CI (see .github/workflows/build.yml).
            // Without one, the APK is signed with the shared test key in keystore/, so updates install over each other.
            val ks = System.getenv("RELEASE_KEYSTORE")
            if (!ks.isNullOrEmpty() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/neonshift-test.jks")
                storePassword = "neonshift"
                keyAlias = "neonshift"
                keyPassword = "neonshift"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
