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
    implementation("org.telegram.ui:tgui:11.4.2-ui2")
    // WORKAROUND (see INTEGRATION-NOTES.md #1): the published AAR lacks 5 @IntDef classes that AGP strips
    // as SOURCE-retention typedefs, but Java 17 NestMembers still reference them, so D8 fails to dex the AAR.
    implementation(files("libs/tgui-stripped-typedefs.jar"))
}
