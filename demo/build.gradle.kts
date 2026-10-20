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
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":tgui"))
}
