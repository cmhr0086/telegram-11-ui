plugins {
    id("com.android.application")
}

android {
    namespace = "org.telegram.uidemo"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.telegram.messenger.demo"
        minSdk = 21
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":tgui"))
}
