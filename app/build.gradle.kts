plugins {
    id("com.android.application")
}

android {
    namespace = "com.alvaro.baixashopee"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alvaro.baixashopee"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0"
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
}

dependencies {
    // Modelo incorporado: fica disponível offline imediatamente após a instalação.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
}
