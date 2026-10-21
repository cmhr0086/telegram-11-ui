plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.tguihost"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tguihost"
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
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("org.telegram.ui:tgui:11.4.2-ui4")
    // tgui's RecyclerView fork has its own package, so Material (which pulls the official
    // androidx.recyclerview) can sit next to it without excludes.
    implementation("com.google.android.material:material:1.12.0")
}
